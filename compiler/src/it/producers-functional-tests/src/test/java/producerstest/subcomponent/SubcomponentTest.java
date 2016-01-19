/*
 * Copyright (C) 2016 Google, Inc.
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
package producerstest.subcomponent;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import producerstest.subcomponent.Subcomponents.ChildComponent;
import producerstest.subcomponent.Subcomponents.GrandchildComponent;
import producerstest.subcomponent.Subcomponents.ParentComponent;
import producerstest.subcomponent.Subcomponents.ParentProductionComponent;

@RunWith(JUnit4.class)
public final class SubcomponentTest {
  @Test
  public void topLevelComponent_child() throws Exception {
    Executor executor = MoreExecutors.directExecutor();
    ParentComponent parent = DaggerSubcomponents_ParentComponent.create();
    ChildComponent child = parent.newChildComponentBuilder().executor(executor).build();
    assertThat(child.fromChild().get()).isEqualTo("child:parent");
  }

  @Test
  public void topLevelComponent_injectsChildBuilder() throws Exception {
    Executor executor = MoreExecutors.directExecutor();
    ParentComponent parent = DaggerSubcomponents_ParentComponent.create();
    ChildComponent child = parent.injectsChildBuilder().childBuilder().executor(executor).build();
    assertThat(child.fromChild().get()).isEqualTo("child:parent");
  }

  @Test
  public void topLevelComponent_grandchild() throws Exception {
    Executor executor = MoreExecutors.directExecutor();
    ParentComponent parent = DaggerSubcomponents_ParentComponent.create();
    ChildComponent child = parent.newChildComponentBuilder().executor(executor).build();
    GrandchildComponent grandchild =
        child.newGrandchildComponentBuilder().executor(executor).build();
    assertThat(grandchild.fromGrandchild().get()).isEqualTo("grandchild:child:parent");
  }

  @Test
  public void topLevelProductionComponent_child() throws Exception {
    Executor executor = MoreExecutors.directExecutor();
    ParentProductionComponent parent =
        DaggerSubcomponents_ParentProductionComponent.builder().executor(executor).build();
    ChildComponent child = parent.newChildComponentBuilder().executor(executor).build();
    assertThat(child.fromChild().get()).isEqualTo("child:parentproduction");
  }

  @Test
  public void topLevelProductionComponent_grandchild() throws Exception {
    Executor executor = MoreExecutors.directExecutor();
    ParentProductionComponent parent =
        DaggerSubcomponents_ParentProductionComponent.builder().executor(executor).build();
    ChildComponent child = parent.newChildComponentBuilder().executor(executor).build();
    GrandchildComponent grandchild =
        child.newGrandchildComponentBuilder().executor(executor).build();
    assertThat(grandchild.fromGrandchild().get()).isEqualTo("grandchild:child:parentproduction");
  }
}
