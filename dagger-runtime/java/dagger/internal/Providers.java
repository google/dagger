/*
 * Copyright (C) 2023 The Dagger Authors.
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

/** Helper class for utility functions dealing with Providers. */
public final class Providers {

  /** Converts a javax provider to a Dagger internal provider. */
  @SuppressWarnings("unchecked")
  public static <T extends @Nullable Object> Provider<T> asDaggerProvider(
      final javax.inject.Provider<T> provider) {
    checkNotNull(provider);
    if (provider instanceof Provider) {
      return (Provider) provider;
    }
    return new Provider<T>() {
        @Override public T get() {
          return provider.get();
        }
    };
  }

  private Providers() {}
}
