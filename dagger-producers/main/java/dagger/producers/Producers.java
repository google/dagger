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

package dagger.producers;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.internal.Beta;
import dagger.producers.internal.CancellableProducer;
import dagger.producers.internal.CancellationListener;

/** Utility methods to create {@link Producer}s. */
@Beta
public final class Producers {
  /** Returns a producer that succeeds with the given value. */
  public static <T> Producer<T> immediateProducer(final T value) {
    return new ImmediateProducer<>(Futures.immediateFuture(value));
  }

  /** Returns a producer that fails with the given exception. */
  public static <T> Producer<T> immediateFailedProducer(final Throwable throwable) {
    return new ImmediateProducer<>(Futures.<T>immediateFailedFuture(throwable));
  }

  /** A {@link CancellableProducer} with an immediate result. */
  private static final class ImmediateProducer<T> implements CancellableProducer<T> {
    private final ListenableFuture<T> future;

    ImmediateProducer(ListenableFuture<T> future) {
      this.future = future;
    }

    @Override
    public ListenableFuture<T> get() {
      return future;
    }

    @Override
    public void cancel(boolean mayInterruptIfRunning) {}

    @Override
    public Producer<T> newDependencyView() {
      return this;
    }

    @Override
    public Producer<T> newEntryPointView(CancellationListener cancellationListener) {
      return this;
    }
  }

  private Producers() {}
}
