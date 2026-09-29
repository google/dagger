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

import org.jspecify.annotations.Nullable;

/**
 * A {@link Provider} implementation that memoizes the result of a {@link SwitchingProvider} and
 * switch ID using simple lazy initialization, not the double-checked lock pattern.
 */

public final class SingleCheckSwitchingProvider<T extends @Nullable Object> implements Provider<T> {
  private static final Object UNINITIALIZED = new Object();

  private volatile @Nullable SwitchingProvider<T> switchingProvider;
  private volatile @Nullable Object instance = UNINITIALIZED;
  private final int id;

  private SingleCheckSwitchingProvider(SwitchingProvider<T> switchingProvider, int id) {
    assert switchingProvider != null;
    assert id >= 0;
    this.switchingProvider = switchingProvider;
    this.id = id;
  }

  @SuppressWarnings("unchecked") // cast only happens when result comes from the delegate provider
  @Override
  public T get() {
    @Nullable Object local = instance;
    if (local == UNINITIALIZED) {
      // switchingProvider is volatile and might become null after the check, so retrieve the
      // switchingProvider first
      @Nullable SwitchingProvider<T> providerReference = switchingProvider;
      if (providerReference == null) {
        // The switchingProvider was null, so the instance must already be set
        local = instance;
      } else {
        local = providerReference.get(id);
        instance = local;
        /* Null out the local reference to the shared switching provider so this wrapper
         * instance no longer holds it once initialized. */
        switchingProvider = null;
      }
    }
    return (T) local;
  }

  /** Returns a {@link Provider} that caches the value from the given switching provider and ID. */
  @SuppressWarnings("unchecked")
  public static <T extends @Nullable Object> dagger.internal.Provider<T> provider(
      SwitchingProvider<?> switchingProvider, int id) {
    return new SingleCheckSwitchingProvider<T>(
        (SwitchingProvider<T>) (SwitchingProvider<?>) checkNotNull(switchingProvider), id);
  }
}

