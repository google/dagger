/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.functional.basic;

import static com.google.common.truth.Truth.assertThat;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Regression test for b/212604806. */
@RunWith(JUnit4.class)
public final class ComponentNestedTypeTest {
  @Component(modules = TestModule.class)
  interface TestComponent {

    // Dagger generated component implementation that extends TestComponent will implement this
    // method, so the component implementation will keep a reference to the
    // dagger.functional.sub.NestedType. The reference to dagger.functional.sub.NestedType may
    // collide with the NestedType defined inside of TestComponent, because javapoet may strip the
    // package prefix of the type as it does not have enough information about the super
    // class/interfaces.
    dagger.functional.basic.subpackage.NestedType nestedType();

    interface NestedType {}
  }

  public static final class SomeType implements dagger.functional.basic.subpackage.NestedType {}

  @Module
  static final class TestModule {
    @Provides
    static dagger.functional.basic.subpackage.NestedType provideSomeType() {
      return new SomeType();
    }
  }

  @Test
  public void typeNameWontClashWithNestedTypeName() {
    TestComponent component = DaggerComponentNestedTypeTest_TestComponent.create();
    assertThat(component.nestedType()).isNotNull();
  }
}
