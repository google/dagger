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

package dagger.producers.monitoring;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import dagger.internal.Beta;

/**
 * A monitor that measures the timing of the execution of a production component, and logs those
 * timings with the given recorder.
 *
 * <p>This assumes that the given recorders do not throw or return null; for example, by using
 * {@link TimingRecorders#delegatingProductionComponentTimingRecorderFactory}.
 */
// TODO(beder): Reduce the visibility of this class to package-private.
@Beta
public final class TimingProductionComponentMonitor extends ProductionComponentMonitor {
  private final ProductionComponentTimingRecorder recorder;
  private final Ticker ticker;
  private final Stopwatch stopwatch;

  TimingProductionComponentMonitor(ProductionComponentTimingRecorder recorder, Ticker ticker) {
    this.recorder = recorder;
    this.ticker = ticker;
    this.stopwatch = Stopwatch.createStarted(ticker);
  }

  @Override
  public ProducerMonitor producerMonitorFor(ProducerToken token) {
    return new TimingProducerMonitor(recorder.producerTimingRecorderFor(token), ticker, stopwatch);
  }

  public static final class Factory extends ProductionComponentMonitor.Factory {
    private final ProductionComponentTimingRecorder.Factory recorderFactory;
    private final Ticker ticker;

    public Factory(ProductionComponentTimingRecorder.Factory recorderFactory) {
      this(recorderFactory, Ticker.systemTicker());
    }

    Factory(ProductionComponentTimingRecorder.Factory recorderFactory, Ticker ticker) {
      this.recorderFactory = recorderFactory;
      this.ticker = ticker;
    }

    @Override
    public ProductionComponentMonitor create(Object component) {
      return new TimingProductionComponentMonitor(recorderFactory.create(component), ticker);
    }
  }
}
