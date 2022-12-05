/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.kotlinsrc.basic

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.Module
import dagger.Provides
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Regression test for b/212604806. */
@RunWith(JUnit4::class)
class ComponentNestedTypeTest {
  @Component(modules = [TestModule::class])
  internal interface TestComponent {

    // Dagger generated component implementation that extends TestComponent will implement this
    // method, so the component implementation will keep a reference to the
    // dagger.functional.kotlinsrc.basic.subpackage.NestedType. The reference to
    // dagger.functional.kotlinsrc.basic.subpackage.NestedType may collide with the NestedType
    // defined inside of TestComponent, because javapoet may strip the package prefix of the type as
    // it does not have enough information about the super class/interfaces.
    fun nestedType(): dagger.functional.kotlinsrc.basic.subpackage.NestedType

    interface NestedType
  }

  class SomeType : dagger.functional.kotlinsrc.basic.subpackage.NestedType

  @Module
  internal class TestModule {
    @Provides
    fun provideSomeType(): dagger.functional.kotlinsrc.basic.subpackage.NestedType {
      return SomeType()
    }
  }

  @Test
  fun typeNameWontClashWithNestedTypeName() {
    val component =
      DaggerComponentNestedTypeTest_TestComponent.builder().testModule(TestModule()).build()
    assertThat(component.nestedType()).isNotNull()
  }
}
