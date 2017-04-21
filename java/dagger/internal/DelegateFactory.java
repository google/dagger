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

import javax.inject.Provider;

/**
 * A DelegateFactory that is used to stitch Provider/Lazy indirection based dependency cycles.
 * 
 * @since 2.0.1
 */
public final class DelegateFactory<T> implements Factory<T> {
  private Provider<T> delegate;

  @Override
  public T get() {
    if (delegate == null) {
      throw new IllegalStateException();
    }
    return delegate.get();
  }

  public void setDelegatedProvider(Provider<T> delegate) {
    if (delegate == null) {
      throw new IllegalArgumentException();
    }
    if (this.delegate != null) {
      throw new IllegalStateException();
    }
    this.delegate = delegate;
  }
}

