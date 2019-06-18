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

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import java.time.Duration;

/** Statistics collected over the course of Dagger annotation processing. */
@AutoValue
abstract class DaggerStatistics {
  /** Returns a new {@link Builder}. */
  static Builder builder() {
    return new AutoValue_DaggerStatistics.Builder();
  }

  /** Returns a new {@link RoundStatistics} builder. */
  static RoundStatistics.Builder roundBuilder() {
    return new AutoValue_DaggerStatistics_RoundStatistics.Builder();
  }

  /** Total time spent in Dagger annotation processing. */
  abstract Duration totalProcessingTime();

  /** List of statistics for processing rounds that the Dagger processor handled. */
  abstract ImmutableList<RoundStatistics> rounds();

  /** Records the number of {@code @Inject} constructor factories generated in this compilation. */
  abstract int injectFactoriesGenerated();

  /** Records the number of {@link dagger.MembersInjector}s generated in this compilation. */
  abstract int membersInjectorsGenerated();

  /** Builder for {@link DaggerStatistics}. */
  @AutoValue.Builder
  @CanIgnoreReturnValue
  abstract static class Builder {
    /** Sets the given duration for the total time spent in Dagger processing. */
    abstract Builder setTotalProcessingTime(Duration totalProcessingTime);

    /** Returns a builder for adding processing round statistics. */
    @CheckReturnValue
    abstract ImmutableList.Builder<RoundStatistics> roundsBuilder();

    /** Adds the given {@code round} statistics. */
    final Builder addRound(RoundStatistics round) {
      roundsBuilder().add(round);
      return this;
    }

    /** Sets the number of {@code @Inject} constructor factories generated in this compilation. */
    abstract Builder setInjectFactoriesGenerated(int count);

    /** Sets the number of {@link dagger.MembersInjector}s generated in this compilation. */
    abstract Builder setMembersInjectorsGenerated(int count);

    /** Creates a new {@link DaggerStatistics} instance. */
    @CheckReturnValue
    abstract DaggerStatistics build();
  }

  /** Statistics for each processing step in a single processing round. */
  @AutoValue
  abstract static class RoundStatistics {
    /** Map of processing step class to duration of that step for this round. */
    abstract ImmutableMap<Class<? extends ProcessingStep>, Duration> stepDurations();

    /** Builder for {@link RoundStatistics}. */
    @AutoValue.Builder
    abstract static class Builder {
      /** Returns a builder for adding durations for each processing step for the round. */
      abstract ImmutableMap.Builder<Class<? extends ProcessingStep>, Duration>
          stepDurationsBuilder();

      /** Adds the given {@code duration} for the given {@code step}. */
      @CanIgnoreReturnValue
      final Builder addStepDuration(ProcessingStep step, Duration duration) {
        stepDurationsBuilder().put(step.getClass(), duration);
        return this;
      }

      /** Creates a new {@link RoundStatistics} instance. */
      abstract RoundStatistics build();
    }
  }
}
