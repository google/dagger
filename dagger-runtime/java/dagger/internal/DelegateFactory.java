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
 * A DelegateFactory that is used to stitch Provider/Lazy indirection based dependency cycles.
 *
 * @since 2.0.1
 */
public final class DelegateFactory<T> implements Factory<T> {

  private @Nullable Provider<T> delegate;

  @Override
  public T get() {
    if (delegate == null) {
      throw new IllegalStateException();
    }
    return delegate.get();
  }

  // TODO(ronshapiro): remove this once we can reasonably expect generated code is no longer using
  // this method
  @Deprecated
  public void setDelegatedProvider(Provider<T> delegate) {
    setDelegate(this, delegate);
  }

  /**
   * Legacy javax version of the method to support libraries compiled with an older version of
   * Dagger. Do not use directly.
   */
  @Deprecated
  public void setDelegatedProvider(javax.inject.Provider<T> delegate) {
    setDelegatedProvider(asDaggerProvider(delegate));
  }

  /**
   * Sets {@code delegateFactory}'s delegate provider to {@code delegate}.
   *
   * <p>{@code delegateFactory} must be an instance of {@link DelegateFactory}, otherwise this
   * method will throw a {@link ClassCastException}.
   */
  public static <T> void setDelegate(Provider<T> delegateFactory, Provider<T> delegate) {
    DelegateFactory<T> asDelegateFactory = (DelegateFactory<T>) delegateFactory;
    setDelegateInternal(asDelegateFactory, delegate);
  }

  /**
   * Legacy javax version of the method to support libraries compiled with an older version of
   * Dagger. Do not use directly.
   */
  @Deprecated
  public static <T> void setDelegate(
      javax.inject.Provider<T> delegateFactory, javax.inject.Provider<T> delegate) {
    DelegateFactory<T> asDelegateFactory = (DelegateFactory<T>) delegateFactory;
    setDelegateInternal(asDelegateFactory, asDaggerProvider(delegate));
  }

  private static <T> void setDelegateInternal(
      DelegateFactory<T> delegateFactory, Provider<T> delegate) {
    checkNotNull(delegate);
    if (delegateFactory.delegate != null) {
      throw new IllegalStateException();
    }
    delegateFactory.delegate = delegate;
  }

  /**
   * Returns the factory's delegate.
   *
   * @throws NullPointerException if the delegate has not been set
   */
  Provider<T> getDelegate() {
    return checkNotNull(delegate);
  }
}
