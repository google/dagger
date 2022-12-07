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
 * Tests for factories for components with modules that do not require an instance to be passed to
 * the factory. Includes both tests where the module does not have a corresponding parameter in the
 * factory method and where it does have a parameter, for cases where that's allowed.
 */
@RunWith(JUnit4::class)
class FactoryImplicitModulesTest {
  @Component(modules = [AbstractModule::class])
  internal interface AbstractModuleComponent {
    fun string(): String

    @Component.Factory
    interface Factory {
      fun create(): AbstractModuleComponent
    }
  }

  @Test
  fun abstractModule() {
    val component = DaggerFactoryImplicitModulesTest_AbstractModuleComponent.factory().create()
    assertThat(component.string()).isEqualTo("foo")
  }

  @Component(modules = [InstantiableConcreteModule::class])
  internal interface InstantiableConcreteModuleComponent {
    val int: Int

    @Component.Factory
    interface Factory {
      fun create(): InstantiableConcreteModuleComponent
    }
  }

  @Test
  fun instantiableConcreteModule() {
    val component =
      DaggerFactoryImplicitModulesTest_InstantiableConcreteModuleComponent.factory().create()
    assertThat(component.int).isEqualTo(42)
  }

  @Component(modules = [InstantiableConcreteModule::class])
  internal interface InstantiableConcreteModuleWithFactoryParameterComponent {
    val int: Int

    @Component.Factory
    interface Factory {
      fun create(
        module: InstantiableConcreteModule?
      ): InstantiableConcreteModuleWithFactoryParameterComponent
    }
  }

  @Test
  fun instantiableConcreteModule_withFactoryParameter() {
    val component =
      DaggerFactoryImplicitModulesTest_InstantiableConcreteModuleWithFactoryParameterComponent
        .factory()
        .create(InstantiableConcreteModule())
    assertThat(component.int).isEqualTo(42)
  }

  @Test
  fun instantiableConcreteModule_withFactoryParameter_failsOnNull() {
    try {
      DaggerFactoryImplicitModulesTest_InstantiableConcreteModuleWithFactoryParameterComponent
        .factory()
        .create(null)
      fail()
    } catch (expected: NullPointerException) {}
  }

  @Component(modules = [ConcreteModuleThatCouldBeAbstract::class])
  internal interface ConcreteModuleThatCouldBeAbstractComponent {
    val double: Double

    @Component.Factory
    interface Factory {
      fun create(): ConcreteModuleThatCouldBeAbstractComponent
    }
  }

  @Test
  fun concreteModuleThatCouldBeAbstract() {
    val component =
      DaggerFactoryImplicitModulesTest_ConcreteModuleThatCouldBeAbstractComponent.factory().create()
    assertThat(component.double).isEqualTo(42.0)
  }

  @Component(modules = [ConcreteModuleThatCouldBeAbstract::class])
  internal interface ConcreteModuleThatCouldBeAbstractWithFactoryParameterComponent {
    val double: Double

    @Component.Factory
    interface Factory {
      fun create(
        module: ConcreteModuleThatCouldBeAbstract?
      ): ConcreteModuleThatCouldBeAbstractWithFactoryParameterComponent
    }
  }

  @Test
  fun concreteModuleThatCouldBeAbstract_withFactoryParameter() {
    val component: ConcreteModuleThatCouldBeAbstractWithFactoryParameterComponent =
      DaggerFactoryImplicitModulesTest_ConcreteModuleThatCouldBeAbstractWithFactoryParameterComponent
        .factory()
        .create(ConcreteModuleThatCouldBeAbstract())
    assertThat(component.double).isEqualTo(42.0)
  }

  @Test
  fun concreteModuleThatCouldBeAbstract_withFactoryParameter_failsOnNull() {
    // This matches what builders do when there's a setter for such a module; the setter checks that
    // the argument is not null but otherwise ignores it.
    // It's possible that we shouldn't even allow such a parameter for a factory, since unlike a
    // builder, where the setter can just not be called, a factory doesn't give the option of not
    // passing *something* for the unused parameter.
    try {
      DaggerFactoryImplicitModulesTest_ConcreteModuleThatCouldBeAbstractWithFactoryParameterComponent
          .factory()
          .create(null)
      fail()
    } catch (expected: NullPointerException) {}
  }
}
