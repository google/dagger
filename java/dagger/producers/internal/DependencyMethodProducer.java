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

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import java.util.Collections;
import java.util.Set;

/**
 * Abstract class for implementing producers derived from methods on component dependencies.
 *
 * <p>Unlike most other {@link CancellableProducer} implementations, cancelling the future returned
 * by a {@linkplain #newDependencyView dependency view} injected into an {@code @Produces} method
 * will actually cancel the underlying future. This is because the future comes from outside the
 * component's producer graph (including possibly from another object that isn't a component at
 * all), so if we don't cancel it when the user asks to cancel it, there might just be no way to
 * cancel it at all.
 */
public abstract class DependencyMethodProducer<T> implements CancellableProducer<T> {

  /** Weak set of all incomplete futures this producer has returned. */
  private final Set<ListenableFuture<T>> futures =
      Collections.newSetFromMap(new MapMaker().weakKeys().<ListenableFuture<T>, Boolean>makeMap());

  private boolean cancelled = false;

  /** Calls a method on a component dependency to get a future. */
  protected abstract ListenableFuture<T> callDependencyMethod();

  @Override
  public final ListenableFuture<T> get() {
    synchronized (futures) {
      if (cancelled) {
        return Futures.immediateCancelledFuture();
      }

      final ListenableFuture<T> future = callDependencyMethod();
      if (!future.isDone() && futures.add(future)) {
        future.addListener(
            new Runnable() {
              @Override
              public void run() {
                synchronized (futures) {
                  futures.remove(future);
                }
              }
            },
            directExecutor());
      }
      return future;
    }
  }

  @Override
  public final void cancel(boolean mayInterruptIfRunning) {
    synchronized (futures) {
      cancelled = true;
      for (ListenableFuture<T> future : futures) {
        // futures is a concurrent set so that the concurrent removal that will happen here is not
        // a problem
        future.cancel(mayInterruptIfRunning);
      }
    }
  }

  @Override
  public final Producer<T> newDependencyView() {
    return this;
  }

  @Override
  public final Producer<T> newEntryPointView(final CancellationListener cancellationListener) {
    return new Producer<T>() {
      private final Set<ListenableFuture<T>> entryPointFutures =
          Collections.newSetFromMap(
              new MapMaker().weakKeys().<ListenableFuture<T>, Boolean>makeMap());

      @Override
      public ListenableFuture<T> get() {
        final ListenableFuture<T> future = DependencyMethodProducer.this.get();
        if (!future.isDone() && entryPointFutures.add(future)) {
          future.addListener(
              new Runnable() {
                @Override
                public void run() {
                  entryPointFutures.remove(future);
                  if (future.isCancelled()) {
                    // TODO(cgdecker): Make this also propagate the actual value that was passed for
                    // mayInterruptIfRunning
                    cancellationListener.onProducerFutureCancelled(true);
                  }
                }
              },
              directExecutor());
        }
        return future;
      }
    };
  }
}
