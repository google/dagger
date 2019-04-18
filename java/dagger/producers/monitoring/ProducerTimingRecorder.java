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

import dagger.producers.Produces;
import dagger.producers.ProductionComponent;

/**
 * A hook for recording the timing of the execution of individual
 * {@linkplain Produces producer methods}. See {@link ProductionComponentTimingRecorder} for how to
 * install these monitors.
 *
 * <p>If any of the recorder's methods throw, then the exception will be logged and processing will
 * continue unaffected.
 *
 * <p>All timings are measured at nanosecond precision, but not necessarily nanosecond resolution.
 * That is, timings will be reported in nanoseconds, but the timing source will not necessarily
 * update at nanosecond resolution. For example, {@link System#nanoTime()} would satisfy these
 * constraints.
 *
 * @since 2.1
 */
public abstract class ProducerTimingRecorder {
  /**
   * Reports that the producer method has finished executing with the given statistics.
   *
   * <p>If the producer was skipped due to any of its inputs failing, then this will not be called.
   *
   * @param startedNanos the wall-clock time, in nanoseconds, when the producer method started
   *     executing, measured from when the first method on the {@linkplain ProductionComponent
   *     production component} was called.
   * @param durationNanos the wall-clock time, in nanoseconds, that the producer method took to
   *     execute.
   */
  @SuppressWarnings("GoodTime") // should accept a java.time.Duration x2 (?)
  public void recordMethod(long startedNanos, long durationNanos) {}

  /**
   * Reports that the producer's future has succeeded with the given statistics.
   *
   * <p>If the producer was skipped due to any of its inputs failing, then this will not be called.
   *
   * @param latencyNanos the wall-clock time, in nanoseconds, of the producer's latency, measured
   *     from when the producer method started to when the future finished.
   */
  @SuppressWarnings("GoodTime") // should accept a java.time.Duration
  public void recordSuccess(long latencyNanos) {}

  /**
   * Reports that the producer's future has failed with the given statistics.
   *
   * @param exception the exception that the future failed with.
   * @param latencyNanos the wall-clock time, in nanoseconds, of the producer's latency, measured
   *     from when the producer method started to when the future finished.
   */
  @SuppressWarnings("GoodTime") // should accept a java.time.Duration
  public void recordFailure(Throwable exception, long latencyNanos) {}

  /**
   * Reports that the producer was skipped because one of its inputs failed.
   *
   * @param exception the exception that its input failed with. If multiple inputs failed, this
   *    exception will be chosen arbitrarily from the input failures.
   */
  public void recordSkip(Throwable exception) {}

  /** Returns a producer recorder that does nothing. */
  public static ProducerTimingRecorder noOp() {
    return NO_OP;
  }

  private static final ProducerTimingRecorder NO_OP = new ProducerTimingRecorder() {};
}
