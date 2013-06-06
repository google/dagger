/*
 * Copyright (C) 2012 Google, Inc.
 * Copyright (C) 2012 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dagger.internal;

import dagger.Lazy;

/**
 * Injects a Lazy wrapper for a type T
 */
final class LazyBinding<T> extends Binding<Lazy<T>> {

  private final static Object NOT_PRESENT = new Object();

  private final String lazyKey;
  private final ClassLoader loader;
  private Binding<T> delegate;

  public LazyBinding(String key, Object requiredBy, ClassLoader loader, String lazyKey) {
    super(key, null, false, requiredBy);
    this.loader = loader;
    this.lazyKey = lazyKey;
  }

  @SuppressWarnings("unchecked") // At runtime we know it's a Binding<Lazy<T>>.
  @Override
  public void attach(Linker linker) {
    delegate = (Binding<T>) linker.requestBinding(lazyKey, requiredBy, loader);
  }

  @Override public void injectMembers(Lazy<T> t) {
    throw new UnsupportedOperationException(); // Injecting into a custom Lazy not supported.
  }

  @Override
  public Lazy<T> get() {
    return new Lazy<T>() {
      private volatile Object cacheValue = NOT_PRESENT;

      @SuppressWarnings("unchecked") // Delegate is of type T
      @Override
      public T get() {
        if (cacheValue == NOT_PRESENT) {
          synchronized (this) {
            if (cacheValue == NOT_PRESENT) {
              cacheValue = delegate.get();
            }
          }
        }
        return (T) cacheValue;
      }
    };
  }

  // public void getDependencies() not overridden.
  // We don't add 'delegate' because it isn't actually used by get() or injectMembers().
}
