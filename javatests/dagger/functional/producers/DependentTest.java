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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DependentTest {
  @Test public void dependentComponent() throws Exception {
    DependentComponent dependentComponent =
        DaggerDependentComponent.builder()
            .dependedProductionComponent(DaggerDependedProductionComponent.create())
            .dependedComponent(DaggerDependedComponent.create())
            .build();
    assertThat(dependentComponent).isNotNull();
    assertThat(dependentComponent.greetings().get()).containsExactly(
        "2", "Hello world!", "HELLO WORLD!");
  }

  @Test public void reuseBuilderWithDependentComponent() throws Exception {
    DaggerDependentComponent.Builder dependentComponentBuilder = DaggerDependentComponent.builder();

    DependentComponent componentUsingComponents =
        dependentComponentBuilder
            .dependedProductionComponent(DaggerDependedProductionComponent.create())
            .dependedComponent(DaggerDependedComponent.create())
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
