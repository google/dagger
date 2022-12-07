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

package dagger.functional.kotlinsrc.factory

import com.google.common.truth.Truth.assertThat
import dagger.Component
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for factories for components that have a module that must have an instance provided by the
 * user.
 */
@RunWith(JUnit4::class)
class FactoryRequiredModulesTest {
  @Component(modules = [UninstantiableConcreteModule::class])
  internal interface UninstantiableConcreteModuleComponent {
    val long: Long

    @Component.Factory
    interface Factory {
      fun create(module: UninstantiableConcreteModule?): UninstantiableConcreteModuleComponent
    }
  }

  @Test
  fun uninstantiableConcreteModule() {
    val component =
      DaggerFactoryRequiredModulesTest_UninstantiableConcreteModuleComponent.factory()
        .create(UninstantiableConcreteModule(42L))
    assertThat(component.long).isEqualTo(42L)
  }

  @Test
  fun uninstantiableConcreteModule_failsOnNull() {
    try {
      DaggerFactoryRequiredModulesTest_UninstantiableConcreteModuleComponent.factory().create(null)
      fail()
    } catch (expected: NullPointerException) {}
  }
}
