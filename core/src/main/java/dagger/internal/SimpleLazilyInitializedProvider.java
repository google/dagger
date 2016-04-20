/*
 * Copyright (C) 2014 Google, Inc.
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
import javax.inject.Provider;

import static dagger.internal.Preconditions.checkNotNull;

/**
 * A {@link Provider} implementation that memoizes the result of a {@link Factory} instance using
 * simple lazy initialization, not the double-checked lock pattern.
 */
public final class SimpleLazilyInitializedProvider<T> implements Provider<T>, Lazy<T> {
  private static final Object UNINITIALIZED = new Object();

  private final Factory<T> factory;
  private volatile Object instance = UNINITIALIZED;

  private SimpleLazilyInitializedProvider(Factory<T> factory) {
    assert factory != null;
    this.factory = factory;
  }

  @SuppressWarnings("unchecked") // cast only happens when result comes from the factory
  @Override
  public T get() {
    if (instance == UNINITIALIZED) {
      instance = factory.get();
    }
    return (T) instance;
  }

  /** Returns a new provider for the given factory. */
  public static <T> Provider<T> create(Factory<T> factory) {
    return new SimpleLazilyInitializedProvider<T>(checkNotNull(factory));
  }
}
