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
import dagger.BindsInstance
import dagger.Component
import java.util.Random
import javax.inject.Provider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for component factories with multiple parameters. */
@RunWith(JUnit4::class)
class FactoryMixedParametersTest {
  @Component(
    modules =
      [
        AbstractModule::class,
        UninstantiableConcreteModule::class,
        InstantiableConcreteModule::class
      ],
    dependencies = [Dependency::class]
  )
  internal interface MixedArgComponent {
    fun getString(): String
    val getInt: Int
    val getLong: Long
    fun getSomeObject(): Any
    val getDouble: Double
    fun getRandomProvider(): Provider<Random>

    @Component.Factory
    interface Factory {
      fun create(
        @BindsInstance d: Double,
        dependency: Dependency,
        module: UninstantiableConcreteModule,
        @BindsInstance random: Random
      ): MixedArgComponent
    }
  }

  @Test
  fun mixedArgComponent() {
    val random = Random()
    val component =
      DaggerFactoryMixedParametersTest_MixedArgComponent.factory()
        .create(3.0, Dependency(), UninstantiableConcreteModule(2L), random)
    assertThat(component.getString()).isEqualTo("foo")
    assertThat(component.getInt).isEqualTo(42)
    assertThat(component.getDouble).isEqualTo(3.0)
    assertThat(component.getSomeObject()).isEqualTo("bar")
    assertThat(component.getLong).isEqualTo(2L)
    assertThat(component.getRandomProvider().get()).isSameInstanceAs(random)
    assertThat(component.getRandomProvider().get()).isSameInstanceAs(random)
  }
}
