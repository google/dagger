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
import javax.inject.Inject;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PrunedBindingDependedOnInSuperInitializationTest {
  interface PrunedDependency {}

  static class WillHavePrunedDependency {
    @Inject WillHavePrunedDependency(PrunedDependency pruned) {}
  }

  @Subcomponent
  interface Child {
    Provider<WillHavePrunedDependency> frameworkInstance();
  }

  @Module
  static class ParentModule {
    @Provides
    static WillHavePrunedDependency pruneDependency() {
      return new WillHavePrunedDependency(new PrunedDependency() {});
    }
  }

  @Component(modules = ParentModule.class)
  interface Parent {
    Child child();
  }

  @Test
  public void prunedFrameworkInstanceBindingUsedInInitializationDoesntThrow() {
    Parent parent = DaggerPrunedBindingDependedOnInSuperInitializationTest_Parent.create();
    // This test ensures that pruned bindings that are used during unpruned initialization
    // statements do not throw exceptions. If the subcomponent initialization succeeds, the test
    // should pass
    parent.child();
  }
}
