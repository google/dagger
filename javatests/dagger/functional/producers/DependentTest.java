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

package dagger.functional.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DependentTest {
  @ProductionComponent(
    modules = {ExecutorModule.class, DependentProducerModule.class},
    dependencies = {DependedComponent.class, DependedProductionComponent.class}
  )
  interface DependentComponent {
    ListenableFuture<List<String>> greetings();
  }

  @ProducerModule
  static final class DependentProducerModule {
    @Produces
    ListenableFuture<List<String>> greetings(Integer numGreetings, String greeting) {
      List<String> greetings = ImmutableList.of(
          String.valueOf(numGreetings), greeting, Ascii.toUpperCase(greeting));
      return Futures.immediateFuture(greetings);
    }
  }

  @Component(modules = DependedModule.class)
  interface DependedComponent {
    String getGreeting();
  }

  @Module
  static final class DependedModule {
    @Provides
    String provideGreeting() {
      return "Hello world!";
    }
  }

  @ProductionComponent(modules = {ExecutorModule.class, DependedProducerModule.class})
  interface DependedProductionComponent {
    ListenableFuture<Integer> numGreetings();
  }

  @Module
  static final class ExecutorModule {
    @Provides
    @Production
    Executor executor() {
      return MoreExecutors.directExecutor();
    }
  }

  @ProducerModule
  static final class DependedProducerModule {
    @Produces
    int produceNumberOfGreetings() {
      return 2;
    }
  }

  @Test public void dependentComponent() throws Exception {
    DependentComponent dependentComponent =
        DaggerDependentTest_DependentComponent.builder()
            .dependedProductionComponent(DaggerDependentTest_DependedProductionComponent.create())
            .dependedComponent(DaggerDependentTest_DependedComponent.create())
            .build();
    assertThat(dependentComponent).isNotNull();
    assertThat(dependentComponent.greetings().get()).containsExactly(
        "2", "Hello world!", "HELLO WORLD!");
  }

  @Test public void reuseBuilderWithDependentComponent() throws Exception {
    DaggerDependentTest_DependentComponent.Builder dependentComponentBuilder =
        DaggerDependentTest_DependentComponent.builder();

    DependentComponent componentUsingComponents =
        dependentComponentBuilder
            .dependedProductionComponent(DaggerDependentTest_DependedProductionComponent.create())
            .dependedComponent(DaggerDependentTest_DependedComponent.create())
            .build();

    DependentComponent componentUsingJavaImpls = dependentComponentBuilder
        .dependedProductionComponent(new DependedProductionComponent() {
          @Override public ListenableFuture<Integer> numGreetings() {
            return Futures.immediateFuture(3);
          }
        })
        .dependedComponent(new DependedComponent() {
          @Override public String getGreeting() {
            return "Goodbye world!";
          }
        })
        .build();

    assertThat(componentUsingJavaImpls.greetings().get()).containsExactly(
        "3", "Goodbye world!", "GOODBYE WORLD!");
    assertThat(componentUsingComponents.greetings().get()).containsExactly(
        "2", "Hello world!", "HELLO WORLD!");

  }
}
