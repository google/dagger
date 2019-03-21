/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.producers.internal;

import static dagger.internal.Preconditions.checkNotNull;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.internal.DoubleCheck;
import dagger.producers.Producer;
import javax.inject.Provider;

/**
 * A DelegateProducer that is used to stitch Producer indirection during initialization across
 * partial subcomponent implementations.
 */
public final class DelegateProducer<T> implements CancellableProducer<T> {
  private CancellableProducer<T> delegate;

  @Override
  public ListenableFuture<T> get() {
    return delegate.get();
  }

  // TODO(ronshapiro): remove this once we can reasonably expect generated code is no longer using
  // this method
  @Deprecated
  public void setDelegatedProducer(Producer<T> delegate) {
    setDelegate(this, delegate);
  }

  /**
   * Sets {@code delegateProducer}'s delegate producer to {@code delegate}.
   *
   * <p>{@code delegateProducer} must be an instance of {@link DelegateProducer}, otherwise this
   * method will throw a {@link ClassCastException}.
   */
  public static <T> void setDelegate(Producer<T> delegateProducer, Producer<T> delegate) {
    checkNotNull(delegate);
    DelegateProducer<T> asDelegateProducer = (DelegateProducer<T>) delegateProducer;
    if (asDelegateProducer.delegate != null) {
      throw new IllegalStateException();
    }
    asDelegateProducer.delegate = (CancellableProducer<T>) delegate;
  }

  /**
   * Returns the factory's delegate.
   *
   * @throws NullPointerException if the delegate has not been set
   */
  CancellableProducer<T> getDelegate() {
    return checkNotNull(delegate);
  }

  @Override
  public void cancel(boolean mayInterruptIfRunning) {
    delegate.cancel(mayInterruptIfRunning);
  }

  @Override
  public Producer<T> newDependencyView() {
    return new ProducerView<T>() {
      @Override
      Producer<T> createDelegate() {
        return delegate.newDependencyView();
      }
    };
  }

  @Override
  public Producer<T> newEntryPointView(final CancellationListener cancellationListener) {
    return new ProducerView<T>() {
      @Override
      Producer<T> createDelegate() {
        return delegate.newEntryPointView(cancellationListener);
      }
    };
  }

  private abstract static class ProducerView<T> implements Producer<T> {
    private final Provider<Producer<T>> delegate =
        DoubleCheck.provider(
            new Provider<Producer<T>>() {
              @Override
              public Producer<T> get() {
                return createDelegate();
              }
            });

    abstract Producer<T> createDelegate();

    @Override
    public ListenableFuture<T> get() {
      return delegate.get().get();
    }
  }
}
