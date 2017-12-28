/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.functional.producers.fluentfuture;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.BindsInstance;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoSet;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import java.util.Set;
import java.util.concurrent.Executor;

final class FluentFutures {
  interface Dependency {
    FluentFuture<Float> floatFuture();
  }

  @ProducerModule
  static final class Module {
    @Produces
    static FluentFuture<Integer> intFuture() {
      return FluentFuture.from(immediateFuture(5));
    }

    @Produces
    static FluentFuture<String> stringFuture(int i) {
      return FluentFuture.from(immediateFuture("hello"));
    }

    @Produces
    @IntoSet
    static FluentFuture<Double> doubleFuture(int i) {
      return FluentFuture.from(immediateFuture((double) i));
    }

    @Produces
    @IntoSet
    static double dependencyInput(float f) {
      return (double) f;
    }

    @Produces
    @ElementsIntoSet
    static Set<FluentFuture<Double>> setOfDoubleFutures(int i) {
      return ImmutableSet.of(
          FluentFuture.from(immediateFuture((double) i + 1)),
          FluentFuture.from(immediateFuture((double) i + 2)));
    }
  }

  @ProductionComponent(modules = Module.class, dependencies = Dependency.class)
  interface Component {
    ListenableFuture<String> string();

    ListenableFuture<Set<Double>> setOfDouble();

    @ProductionComponent.Builder
    interface Builder {
      Builder dependency(Dependency dependency);

      @BindsInstance
      Builder executor(@Production Executor executor);

      Component build();
    }
  }
}
