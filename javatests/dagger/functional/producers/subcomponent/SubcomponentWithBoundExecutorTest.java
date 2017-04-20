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

import static com.google.common.truth.Truth.assertThat;

import dagger.functional.producers.subcomponent.SubcomponentsWithBoundExecutor.ChildComponent;
import dagger.functional.producers.subcomponent.SubcomponentsWithBoundExecutor.ExecutorModule;
import dagger.functional.producers.subcomponent.SubcomponentsWithBoundExecutor.GrandchildComponent;
import dagger.functional.producers.subcomponent.SubcomponentsWithBoundExecutor.GrandchildComponentWithoutBuilder;
import dagger.functional.producers.subcomponent.SubcomponentsWithBoundExecutor.ParentComponent;
import dagger.functional.producers.subcomponent.SubcomponentsWithBoundExecutor.ParentProductionComponent;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SubcomponentWithBoundExecutorTest {
  private ParentComponent parentComponent;
  private ParentProductionComponent parentProductionComponent;
  private final AtomicInteger executorConstructionCount = new AtomicInteger();
  private final AtomicInteger executionCount = new AtomicInteger();

  @Before
  public void setUp() {
    parentComponent =
        DaggerSubcomponentsWithBoundExecutor_ParentComponent.builder()
            .executorModule(new ExecutorModule(executorConstructionCount, executionCount))
            .build();
    parentProductionComponent =
        DaggerSubcomponentsWithBoundExecutor_ParentProductionComponent.builder()
            .executorModule(new ExecutorModule(executorConstructionCount, executionCount))
            .build();
  }

  @Test
  public void topLevelComponent_child() throws Exception {
    ChildComponent child = parentComponent.newChildComponentBuilder().build();
    assertThat(child.fromChild().get()).isEqualTo("child:parent");
    assertThat(executorConstructionCount.get()).isEqualTo(1);
    assertThat(executionCount.get()).isEqualTo(1);
  }

  @Test
  public void topLevelComponent_injectsChildBuilder() throws Exception {
    ChildComponent child = parentComponent.injectsChildBuilder().childBuilder().build();
    assertThat(child.fromChild().get()).isEqualTo("child:parent");
    assertThat(executorConstructionCount.get()).isEqualTo(1);
    assertThat(executionCount.get()).isEqualTo(1);
  }

  @Test
  public void topLevelComponent_grandchild() throws Exception {
    ChildComponent child = parentComponent.newChildComponentBuilder().build();
    GrandchildComponent grandchild = child.newGrandchildComponentBuilder().build();
    assertThat(grandchild.fromGrandchild().get()).isEqualTo("grandchild:child:parent");
    assertThat(executorConstructionCount.get()).isEqualTo(1);
    assertThat(executionCount.get()).isEqualTo(2);
  }

  @Test
  public void topLevelComponent_grandchildWithoutBuilder() throws Exception {
    ChildComponent child = parentComponent.newChildComponentBuilder().build();
    GrandchildComponentWithoutBuilder grandchild = child.newGrandchildComponent();
    assertThat(grandchild.fromGrandchild().get()).isEqualTo("grandchild:child:parent");
    assertThat(executorConstructionCount.get()).isEqualTo(1);
    assertThat(executionCount.get()).isEqualTo(2);
  }

  @Test
  public void topLevelProductionComponent_child() throws Exception {
    ChildComponent child = parentProductionComponent.newChildComponentBuilder().build();
    assertThat(child.fromChild().get()).isEqualTo("child:parentproduction");
    assertThat(executorConstructionCount.get()).isEqualTo(1);
    assertThat(executionCount.get()).isEqualTo(2);
  }

  @Test
  public void topLevelProductionComponent_grandchild() throws Exception {
    ChildComponent child = parentProductionComponent.newChildComponentBuilder().build();
    GrandchildComponent grandchild = child.newGrandchildComponentBuilder().build();
    assertThat(grandchild.fromGrandchild().get()).isEqualTo("grandchild:child:parentproduction");
    assertThat(executorConstructionCount.get()).isEqualTo(1);
    assertThat(executionCount.get()).isEqualTo(3);
  }

  @Test
  public void topLevelProductionComponent_grandchildWithoutBuilder() throws Exception {
    ChildComponent child = parentProductionComponent.newChildComponentBuilder().build();
    GrandchildComponentWithoutBuilder grandchild = child.newGrandchildComponent();
    assertThat(grandchild.fromGrandchild().get()).isEqualTo("grandchild:child:parentproduction");
    assertThat(executorConstructionCount.get()).isEqualTo(1);
    assertThat(executionCount.get()).isEqualTo(3);
  }
}
