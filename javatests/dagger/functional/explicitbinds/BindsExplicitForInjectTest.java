/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.functional.explicitbinds;

import static com.google.common.truth.Truth.assertThat;

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BindsExplicitForInjectTest {

  static class Foo {
    @Inject
    Foo() {}
  }

  @Module
  interface TestModule {
    @Binds
    Foo bindFoo();
  }

  @Singleton
  @Component(modules = TestModule.class)
  interface TestComponent {
    Foo getFoo1();

    Foo getFoo2();
  }

  @Test
  public void testScopedBindsExplicitForInject() {
    TestComponent component = DaggerBindsExplicitForInjectTest_TestComponent.create();
    assertThat(component.getFoo1()).isNotNull();
    assertThat(component.getFoo1()).isNotSameInstanceAs(component.getFoo2());
  }
}
