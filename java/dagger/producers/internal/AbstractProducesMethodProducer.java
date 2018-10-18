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

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import java.util.concurrent.Executor;
import javax.inject.Provider;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * An {@link AbstractProducer} for all {@link dagger.producers.Produces} methods.
 *
 * @param <D> the type of asynchronous dependencies. These will be collected in {@link
 *     #collectDependencies()} and then made available to the {@code @Produces method in} {@link
 *     #callProducesMethod(Object)}. If there is only one asynchronous dependency, {@code D} can be
 *     the key for that dependency. If there are multiple, they should be wrapped in a list and
 *     unwrapped in {@link #callProducesMethod(Object)}.
 * @param <T> the produced type
 */
public abstract class AbstractProducesMethodProducer<D, T> extends AbstractProducer<T>
    implements AsyncFunction<D, T>, Executor {
  private final Provider<ProductionComponentMonitor> monitorProvider;
  @NullableDecl private final ProducerToken token;
  private final Provider<Executor> executorProvider;
  private volatile ProducerMonitor monitor = null;

  protected AbstractProducesMethodProducer(
      Provider<ProductionComponentMonitor> monitorProvider,
      @NullableDecl ProducerToken token,
      Provider<Executor> executorProvider) {
    this.monitorProvider = checkNotNull(monitorProvider);
    this.token = token;
    this.executorProvider = checkNotNull(executorProvider);
  }

  @Override
  protected final ListenableFuture<T> compute() {
    monitor = monitorProvider.get().producerMonitorFor(token);
    monitor.requested();
    ListenableFuture<T> result = Futures.transformAsync(collectDependencies(), this, this);
    monitor.addCallbackTo(result);
    return result;
  }

  /**
   * Collects the asynchronous dependencies to be passed to {@link
   * Futures#transformAsync(ListenableFuture, AsyncFunction, Executor)}.
   */
  protected abstract ListenableFuture<D> collectDependencies();

  /** @deprecated this may only be called from the internal {@link #compute()} */
  @Deprecated
  @Override
  public final ListenableFuture<T> apply(D asyncDependencies) throws Exception {
    // NOTE(beder): We don't worry about catching exceptions from the monitor methods themselves
    // because we'll wrap all monitoring in non-throwing monitors before we pass them to the
    // factories.
    monitor.methodStarting();
    try {
      return callProducesMethod(asyncDependencies);
    } finally {
      monitor.methodFinished();
    }
  }

  /**
   * Calls the {@link dagger.producers.Produces} method. This will always be called on the {@link
   * Executor} provided to this producer.
   */
  protected abstract ListenableFuture<T> callProducesMethod(D asyncDependencies) throws Exception;

  /** @deprecated this may only be called from the internal {@link #compute()} */
  @Deprecated
  @Override
  public final void execute(Runnable runnable) {
    monitor.ready();
    executorProvider.get().execute(runnable);
  }
}
