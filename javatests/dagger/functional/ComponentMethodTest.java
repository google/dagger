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

package dagger.functional;

import dagger.Component;
import javax.inject.Inject;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This is a regression test that makes sure component method order does not affect initialization
 * order.
 */
@RunWith(JUnit4.class)
public final class ComponentMethodTest {

  static final class Dep1 {

    @Inject
    Dep1(Dep2 dep2) {}
  }

  static final class Dep2 {

    @Inject
    Dep2(Dep3 dep3) {}
  }

  static final class Dep3 {

    @Inject
    Dep3() {}
  }

  @Component
  interface NonTopologicalOrderComponent {

    Provider<Dep1> dep1Provider();

    Provider<Dep2> dep2Provider();
  }

  @Component
  interface TopologicalOrderComponent {

    Provider<Dep2> dep2Provider();

    Provider<Dep1> dep1Provider();
  }

  @Test
  public void testNonTopologicalOrderComponent() throws Exception {
    NonTopologicalOrderComponent component =
        DaggerComponentMethodTest_NonTopologicalOrderComponent.create();
    component.dep1Provider().get();
    component.dep2Provider().get();
  }

  @Test
  public void testTopologicalOrderComponent() throws Exception {
    TopologicalOrderComponent component =
        DaggerComponentMethodTest_TopologicalOrderComponent.create();
    component.dep1Provider().get();
    component.dep2Provider().get();
  }
}
