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

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;

/**
 * A monitor that measures the timing of the execution of a producer method, and logs those timings
 * with the given recorder.
 */
final class TimingProducerMonitor extends ProducerMonitor {
  private final ProducerTimingRecorder recorder;
  private final Stopwatch stopwatch;
  private final Stopwatch componentStopwatch;
  private long startNanos = -1;

  TimingProducerMonitor(
      ProducerTimingRecorder recorder, Ticker ticker, Stopwatch componentStopwatch) {
    this.recorder = recorder;
    this.stopwatch = Stopwatch.createUnstarted(ticker);
    this.componentStopwatch = componentStopwatch;
  }

  @Override
  public void methodStarting() {
    startNanos = componentStopwatch.elapsed(NANOSECONDS);
    stopwatch.start();
  }

  @Override
  public void methodFinished() {
    // TODO(beder): Is a system ticker the appropriate way to track CPU time? Should we use
    // ThreadCpuTicker instead?
    long durationNanos = stopwatch.elapsed(NANOSECONDS);
    recorder.recordMethod(startNanos, durationNanos);
  }

  @Override
  public void succeeded(Object o) {
    long latencyNanos = stopwatch.elapsed(NANOSECONDS);
    recorder.recordSuccess(latencyNanos);
  }

  @Override
  public void failed(Throwable t) {
    if (stopwatch.isRunning()) {
      long latencyNanos = stopwatch.elapsed(NANOSECONDS);
      recorder.recordFailure(t, latencyNanos);
    } else {
      recorder.recordSkip(t);
    }
  }
}
