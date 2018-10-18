/*
 * Copyright (C) 2014 The Dagger Authors.
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

import javax.inject.Provider;

/**
 * A {@link Provider} implementation that memoizes the result of another {@link Provider} using
 * simple lazy initialization, not the double-checked lock pattern.
 */
public final class SingleCheck<T> implements Provider<T> {
  private static final Object UNINITIALIZED = new Object();

  private volatile Provider<T> provider;
  private volatile Object instance = UNINITIALIZED;

  private SingleCheck(Provider<T> provider) {
    assert provider != null;
    this.provider = provider;
  }

  @SuppressWarnings("unchecked") // cast only happens when result comes from the delegate provider
  @Override
  public T get() {
    Object local = instance;
    if (local == UNINITIALIZED) {
      // provider is volatile and might become null after the check, so retrieve the provider first
      Provider<T> providerReference = provider;
      if (providerReference == null) {
        // The provider was null, so the instance must already be set
        local = instance;
      } else {
        local = providerReference.get();
        instance = local;

        // Null out the reference to the provider. We are never going to need it again, so we can
        // make it eligible for GC.
        provider = null;
      }
    }
    return (T) local;
  }

  /** Returns a {@link Provider} that caches the value from the given delegate provider. */
  // This method is declared this way instead of "<T> Provider<T> provider(Provider<T> provider)" 
  // to work around an Eclipse type inference bug: https://github.com/google/dagger/issues/949.
  public static <P extends Provider<T>, T> Provider<T> provider(P provider) {
    // If a scoped @Binds delegates to a scoped binding, don't cache the value again.
    if (provider instanceof SingleCheck || provider instanceof DoubleCheck) {
      return provider;
    }
    return new SingleCheck<T>(checkNotNull(provider));
  }
}
