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

package dagger.functional.producers.cancellation;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.BindsInstance;
import dagger.functional.producers.cancellation.CancellationComponent.Dependency;
import dagger.producers.Producer;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import java.util.concurrent.Executor;
import javax.inject.Named;

@ProductionComponent(modules = CancellationModule.class, dependencies = Dependency.class)
interface CancellationComponent {

  @Named("ep1")
  ListenableFuture<String> entryPoint1();

  @Named("ep2")
  Producer<String> entryPoint2();

  @Named("ep3")
  ListenableFuture<String> entryPoint3();

  CancellationSubcomponent.Builder subcomponentBuilder();

  @ProductionComponent.Builder
  interface Builder {
    Builder module(CancellationModule module);

    Builder dependency(Dependency dependency);

    @BindsInstance
    Builder executor(@Production Executor executor);

    CancellationComponent build();
  }

  final class Dependency {

    final ProducerTester tester;

    Dependency(ProducerTester tester) {
      this.tester = checkNotNull(tester);
    }

    @SuppressWarnings("unused") // Dagger uses it
    ListenableFuture<String> getDependencyFuture() {
      return tester.start("dependencyFuture");
    }
  }
}
