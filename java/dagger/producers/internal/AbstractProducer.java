/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import dagger.producers.monitoring.ProducerMonitor;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import dagger.producers.monitoring.internal.Monitors;
import javax.annotation.Nullable;
import javax.inject.Provider;

/**
 * An abstract {@link Producer} implementation that memoizes the result of its compute method.
 *
 * @author Jesse Beder
 * @since 2.0
 */
public abstract class AbstractProducer<T> implements Producer<T> {
  private final Provider<ProductionComponentMonitor> monitorProvider;
  @Nullable private final ProducerToken token;
  private volatile ListenableFuture<T> instance = null;
  protected volatile ProducerMonitor monitor = null;

  protected AbstractProducer() {
    this(Monitors.noOpProductionComponentMonitorProvider(), null);
  }

  protected AbstractProducer(
      Provider<ProductionComponentMonitor> monitorProvider, @Nullable ProducerToken token) {
    this.monitorProvider = checkNotNull(monitorProvider);
    this.token = token;
  }

  /**
   * Computes this producer's future, which is then cached in {@link #get}. The {@link #monitor}
   * will be non-null from the point of this call forward.
   */
  protected abstract ListenableFuture<T> compute();

  @Override
  public final ListenableFuture<T> get() {
    // double-check idiom from EJ2: Item 71
    ListenableFuture<T> result = instance;
    if (result == null) {
      synchronized (this) {
        result = instance;
        if (result == null) {
          monitor = monitorProvider.get().producerMonitorFor(token);
          monitor.requested();
          instance = result = compute();
          if (result == null) {
            throw new NullPointerException("compute returned null");
          }
          monitor.addCallbackTo(result);
        }
      }
    }
    return result;
  }
}
