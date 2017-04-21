/*
 * Copyright (C) 2016 The Dagger Authors.
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
import javax.inject.Provider;

/**
 * A {@link Provider} of {@link Lazy} instances that each delegate to a given {@link Provider}.
 */
public final class ProviderOfLazy<T> implements Provider<Lazy<T>> {

  private final Provider<T> provider;

  private ProviderOfLazy(Provider<T> provider) {
    assert provider != null;
    this.provider = provider;
  }

  /**
   * Returns a new instance of {@link Lazy Lazy&lt;T&gt;}, which calls {@link Provider#get()} at
   * most once on the {@link Provider} held by this object.
   */
  @Override
  public Lazy<T> get() {
    return DoubleCheck.lazy(provider);
  }

  /**
   * Creates a new {@link Provider Provider&lt;Lazy&lt;T&gt;&gt;} that decorates the given
   * {@link Provider}.
   *
   * @see #get()
   */
  public static <T> Provider<Lazy<T>> create(Provider<T> provider) {
    return new ProviderOfLazy<T>(checkNotNull(provider));
  }
}
