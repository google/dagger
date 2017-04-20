/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.functional.producers.subcomponent;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Qualifier;

final class SubcomponentsWithBoundExecutor {
  @Qualifier
  @interface FromParent {}

  @Qualifier
  @interface FromChild {}

  @Qualifier
  @interface FromGrandchild {}

  static final class CountingExecutor implements Executor {
    private final AtomicInteger executionCount;

    CountingExecutor(AtomicInteger executionCount) {
      this.executionCount = executionCount;
    }

    @Override
    public void execute(Runnable runnable) {
      executionCount.incrementAndGet();
      runnable.run();
    }
  }

  @Module
  static final class ExecutorModule {
    private final AtomicInteger constructionCount;
    private final AtomicInteger executionCount;

    ExecutorModule(AtomicInteger constructionCount, AtomicInteger executionCount) {
      this.constructionCount = constructionCount;
      this.executionCount = executionCount;
    }

    @Provides
    @Production
    Executor executor() {
      constructionCount.incrementAndGet();
      return new CountingExecutor(executionCount);
    }
  }

  @Module
  static final class ParentModule {
    @Provides
    @FromParent
    static String fromParent() {
      return "parent";
    }
  }

  @Component(modules = {ParentModule.class, ExecutorModule.class})
  interface ParentComponent {
    InjectsChildBuilder injectsChildBuilder();

    ChildComponent.Builder newChildComponentBuilder();
  }

  @ProducerModule
  static final class ParentProducerModule {
    @Produces
    @FromParent
    static String fromParent() {
      return "parentproduction";
    }
  }

  @ProductionComponent(modules = {ParentProducerModule.class, ExecutorModule.class})
  interface ParentProductionComponent {
    ChildComponent.Builder newChildComponentBuilder();

    @ProductionComponent.Builder
    interface Builder {
      Builder executorModule(ExecutorModule executorModule);

      ParentProductionComponent build();
    }
  }

  @ProducerModule
  static final class ChildProducerModule {
    @Produces
    @FromChild
    static String fromChild(@FromParent String fromParent) {
      return "child:" + fromParent;
    }
  }

  @ProductionSubcomponent(modules = ChildProducerModule.class)
  interface ChildComponent {
    @FromChild
    ListenableFuture<String> fromChild();

    GrandchildComponent.Builder newGrandchildComponentBuilder();
    GrandchildComponentWithoutBuilder newGrandchildComponent();

    @ProductionSubcomponent.Builder
    interface Builder {
      ChildComponent build();
    }
  }

  static final class InjectsChildBuilder {
    private final Provider<ChildComponent.Builder> childBuilder;

    @Inject
    InjectsChildBuilder(Provider<ChildComponent.Builder> childBuilder) {
      this.childBuilder = childBuilder;
    }

    ChildComponent.Builder childBuilder() {
      return childBuilder.get();
    }
  }

  @ProducerModule
  static final class GrandchildProducerModule {
    @Produces
    @FromGrandchild
    static String fromGranchild(@FromChild String fromChild) {
      return "grandchild:" + fromChild;
    }
  }

  @ProductionSubcomponent(modules = GrandchildProducerModule.class)
  interface GrandchildComponent {
    @FromGrandchild
    ListenableFuture<String> fromGrandchild();

    @ProductionSubcomponent.Builder
    interface Builder {
      GrandchildComponent build();
    }
  }

  @ProductionSubcomponent(modules = GrandchildProducerModule.class)
  interface GrandchildComponentWithoutBuilder {
    @FromGrandchild
    ListenableFuture<String> fromGrandchild();
  }

  private SubcomponentsWithBoundExecutor() {}
}
