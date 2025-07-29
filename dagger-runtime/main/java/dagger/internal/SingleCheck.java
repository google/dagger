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
import static dagger.internal.Providers.asDaggerProvider;

import org.jspecify.annotations.Nullable;

/**
 * A {@link Provider} implementation that memoizes the result of another {@link Provider} using
 * simple lazy initialization, not the double-checked lock pattern.
 */
public final class SingleCheck<T extends @Nullable Object> implements Provider<T> {
  private static final Object UNINITIALIZED = new Object();

  private volatile @Nullable Provider<T> provider;
  private volatile @Nullable Object instance = UNINITIALIZED;

  private SingleCheck(Provider<T> provider) {
    assert provider != null;
    this.provider = provider;
  }

  @SuppressWarnings("unchecked") // cast only happens when result comes from the delegate provider
  @Override
  public T get() {
    @Nullable Object local = instance;
    if (local == UNINITIALIZED) {
      // provider is volatile and might become null after the check, so retrieve the provider first
      @Nullable Provider<T> providerReference = provider;
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
  public static <T> Provider<T> provider(Provider<T> provider) {
    // If a scoped @Binds delegates to a scoped binding, don't cache the value again.
    if (provider instanceof SingleCheck || provider instanceof DoubleCheck) {
      return provider;
    }
    return new SingleCheck<T>(checkNotNull(provider));
  }

  /**
   * Legacy javax version of the method to support libraries compiled with an older version of
   * Dagger. Do not use directly.
   */
  public static <P extends javax.inject.Provider<T>, T> javax.inject.Provider<T> provider(
      P delegate) {
    return provider(asDaggerProvider(delegate));
  }
}
