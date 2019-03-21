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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Collects {@link DaggerStatistics} over the course of Dagger annotation processing. */
@Singleton // for state sharing
final class DaggerStatisticsCollector {

  private final Ticker ticker;
  private final Stopwatch totalRuntimeStopwatch;
  private final Map<ProcessingStep, Stopwatch> stepStopwatches = new HashMap<>();

  private final DaggerStatistics.Builder statisticsBuilder = DaggerStatistics.builder();
  private DaggerStatistics.RoundStatistics.Builder roundBuilder = DaggerStatistics.roundBuilder();

  private final Optional<DaggerStatisticsRecorder> statisticsRecorder;

  @Inject
  DaggerStatisticsCollector(Ticker ticker, Optional<DaggerStatisticsRecorder> statisticsRecorder) {
    this.ticker = ticker;
    this.totalRuntimeStopwatch = Stopwatch.createUnstarted(ticker);
    this.statisticsRecorder = statisticsRecorder;
  }

  /** Called when Dagger annotation processing starts. */
  void processingStarted() {
    checkState(!totalRuntimeStopwatch.isRunning());
    totalRuntimeStopwatch.start();
  }

  /** Called when the given {@code step} starts processing for a round. */
  void stepStarted(ProcessingStep step) {
    Stopwatch stopwatch =
        stepStopwatches.computeIfAbsent(step, unused -> Stopwatch.createUnstarted(ticker));
    stopwatch.start();
  }

  /** Called when the given {@code step} finishes processing for a round. */
  void stepFinished(ProcessingStep step) {
    Stopwatch stopwatch = stepStopwatches.get(step);
    roundBuilder.addStepDuration(step, elapsedTime(stopwatch));
    stopwatch.reset();
  }

  /** Called when Dagger finishes a processing round. */
  void roundFinished() {
    statisticsBuilder.addRound(roundBuilder.build());
    roundBuilder = DaggerStatistics.roundBuilder();
  }

  /** Called when Dagger annotation processing completes. */
  void processingStopped() {
    checkState(totalRuntimeStopwatch.isRunning());
    totalRuntimeStopwatch.stop();
    statisticsBuilder.setTotalProcessingTime(elapsedTime(totalRuntimeStopwatch));

    statisticsRecorder.ifPresent(
        recorder -> recorder.recordStatistics(statisticsBuilder.build()));
  }

  @SuppressWarnings("StopwatchNanosToDuration") // intentional
  private Duration elapsedTime(Stopwatch stopwatch) {
    // Using the java 7 method here as opposed to the Duration-returning version to avoid issues
    // when other annotation processors rely on java 7's guava
    return Duration.ofNanos(stopwatch.elapsed(NANOSECONDS));
  }
}
