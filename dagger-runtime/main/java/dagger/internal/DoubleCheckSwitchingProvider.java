/*
 * Copyright (C) 2026 The Dagger Authors.
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

import static dagger.internal.Preconditions.checkNotNull;

import dagger.Lazy;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Lazy} and {@link Provider} implementation that memoizes the value returned from a {@link
 * SwitchingProvider} and switch ID using the double-check idiom.
 */

public final class DoubleCheckSwitchingProvider<T extends @Nullable Object>
    implements Provider<T>, Lazy<T> {
  private static final Object UNINITIALIZED = new Object();

  private volatile @Nullable SwitchingProvider<T> provider;
  private volatile @Nullable Object instance = UNINITIALIZED;
  private final int id;

  private DoubleCheckSwitchingProvider(SwitchingProvider<T> provider, int id) {
    assert provider != null;
    assert id >= 0;
    this.provider = provider;
    this.id = id;
  }

  @SuppressWarnings("unchecked") // cast only happens when result comes from the provider
  @Override
  public T get() {
    @Nullable Object result = instance;
    if (result == UNINITIALIZED) {
      result = getSynchronized();
    }
    return (T) result;
  }

  @SuppressWarnings("nullness:dereference.of.nullable") // provider is non-null
  private synchronized @Nullable Object getSynchronized() {
    @Nullable Object result = instance;
    if (result == UNINITIALIZED) {
      result = provider.get(id);
      instance = reentrantCheck(instance, result);
      /* Null out the reference to the provider. We are never going to need it again, so we
       * can make it eligible for GC. */
      provider = null;
    }
    return result;
  }

  /**
   * Checks to see if creating the new instance has resulted in a recursive call. If it has, and the
   * new instance is the same as the current instance, return the instance. However, if the new
   * instance differs from the current instance, an {@link IllegalStateException} is thrown.
   */
  private static @Nullable Object reentrantCheck(
      @Nullable Object currentInstance, @Nullable Object newInstance) {
    boolean isReentrant = currentInstance != UNINITIALIZED;
    if (isReentrant && currentInstance != newInstance) {
      throw new IllegalStateException("Scoped provider was invoked recursively returning "
          + "different results: " + currentInstance + " & " + newInstance + ". This is likely "
          + "due to a circular dependency.");
    }
    return newInstance;
  }

  /** Returns a {@link Provider} that caches the value from the given switching provider and ID. */
  @SuppressWarnings("unchecked")
  public static <T extends @Nullable Object> dagger.internal.Provider<T> provider(
      SwitchingProvider<?> provider, int id) {
    return new DoubleCheckSwitchingProvider<T>(
        (SwitchingProvider<T>) (SwitchingProvider<?>) checkNotNull(provider), id);
  }
}

