/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.functional.jakarta;

import static com.google.common.truth.Truth.assertThat;

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SimpleJakartaTest {

  @Scope
  public @interface TestScope {}

  @Qualifier
  public @interface TestQualifier {}

  @TestScope
  @Component(modules = TestModule.class)
  interface TestComponent {
    @TestQualifier Foo getQualifiedFoo();
  }

  public static final class Foo {
    @Inject Foo() {}
  }

  @Module
  interface TestModule {
    // By binding this to itself, if the qualifier annotation isn't picked up, it will created a
    // cycle.
    @Binds
    @TestScope
    @TestQualifier
    Foo bind(Foo impl);
  }

  @Test
  public void testFooFactory() {
    TestComponent testComponent = DaggerSimpleJakartaTest_TestComponent.create();
    Foo foo = testComponent.getQualifiedFoo();

    assertThat(foo).isSameInstanceAs(testComponent.getQualifiedFoo());
  }
}
