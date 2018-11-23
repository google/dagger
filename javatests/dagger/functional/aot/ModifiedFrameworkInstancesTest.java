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

package dagger.functional.aot;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.multibindings.IntoSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModifiedFrameworkInstancesTest {
  static class DependsOnModifiableBinding {
    @Inject
    DependsOnModifiableBinding(Set<Integer> modifiableDependency) {}
  }

  @Module
  interface ChildModule {
    @Provides
    @IntoSet
    static int contribution() {
      return 1;
    }
  }

  @Subcomponent(modules = ChildModule.class)
  interface Child {
    Provider<DependsOnModifiableBinding> frameworkInstanceWithModifiedDependency();
  }

  @Module
  interface ParentModule {
    @Provides
    @IntoSet
    static int contribution() {
      return 2;
    }
  }

  @Component(modules = ParentModule.class)
  interface Parent {
    Child child();
  }

  @Test
  public void dependsOnModifiedFrameworkInstance() {
    DaggerModifiedFrameworkInstancesTest_Parent.create()
        .child()
        .frameworkInstanceWithModifiedDependency()
        // Ensure that modified framework instances that are dependencies to other framework 
        // instances from superclass implementations are initialized correctly. This fixes a
        // regression where a null instance would be passed to the superclass initialization, and
        // then a NullPointerException would be thrown when the factory attempted to satisfy the
        // dependency in get(). If get() succeeds, this test should pass.
        .get();
  }
}
