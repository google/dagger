/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.functional.kotlinsrc.nullables

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Reusable
import java.lang.NullPointerException
import javax.inject.Inject
import javax.inject.Provider
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class NullabilityTest {
  internal annotation class Nullable

  @Component(dependencies = [NullComponent::class])
  internal interface NullComponentWithDependency {
    @Nullable fun string(): String?
    fun number(): Number
    fun stringProvider(): Provider<String>
    fun numberProvider(): Provider<Number>
  }

  @Component(modules = [NullModule::class])
  internal interface NullComponent {
    @Nullable fun string(): String?
    @Nullable fun integer(): Int?
    fun nullFoo(): NullFoo
    fun number(): Number
    fun stringProvider(): Provider<String>
    fun numberProvider(): Provider<Number>
  }

  @Module
  internal class NullModule {
    var numberValue: Number? = null
    var integerCallCount = 0

    @Nullable @Provides fun provideNullableString(): String? = null

    @Provides fun provideNumber(): Number = numberValue!!

    @Nullable
    @Provides
    @Reusable
    fun provideNullReusableInteger(): Int? {
      integerCallCount++
      return null
    }
  }

  @Suppress("BadInject") // This is just for testing purposes.
  internal class NullFoo
  @Inject
  constructor(
    @param:Nullable val string: String?,
    val number: Number,
    val stringProvider: Provider<String>,
    val numberProvider: Provider<Number>
  ) {
    var methodInjectedString: String? = null
    lateinit var methodInjectedNumber: Number
    lateinit var methodInjectedStringProvider: Provider<String>
    lateinit var methodInjectedNumberProvider: Provider<Number>

    @Inject
    fun inject(
      @Nullable string: String?,
      number: Number,
      stringProvider: Provider<String>,
      numberProvider: Provider<Number>
    ) {
      methodInjectedString = string
      methodInjectedNumber = number
      methodInjectedStringProvider = stringProvider
      methodInjectedNumberProvider = numberProvider
    }

    @JvmField @Nullable @Inject var fieldInjectedString: String? = null
    @Inject lateinit var fieldInjectedNumber: Number
    @Inject lateinit var fieldInjectedStringProvider: Provider<String>
    @Inject lateinit var fieldInjectedNumberProvider: Provider<Number>
  }

  @Test
  fun testNullability_provides() {
    val module = NullModule()
    val component = DaggerNullabilityTest_NullComponent.builder().nullModule(module).build()

    // Can't construct NullFoo because it depends on Number, and Number was null.
    try {
      component.nullFoo()
      fail()
    } catch (npe: NullPointerException) {
      // NOTE: In Java we would check that the Dagger error message is something like:
      //   "Cannot return null from a non-@Nullable @Provides method"
      // However, in Kotlin there's no way to return a null value from a non-null return type
      // without explicitly using `!!`, which results in an error before Dagger's runtime
      // checkNotNull has a chance to run.
    }

    // set number to non-null so we can create
    module.numberValue = 1
    val nullFoo = component.nullFoo()

    // Then set it back to null so we can test its providers.
    module.numberValue = null
    validate(nullFoo.string, nullFoo.stringProvider, nullFoo.numberProvider)
    validate(
      nullFoo.methodInjectedString,
      nullFoo.methodInjectedStringProvider,
      nullFoo.methodInjectedNumberProvider
    )
    validate(
      nullFoo.fieldInjectedString,
      nullFoo.fieldInjectedStringProvider,
      nullFoo.fieldInjectedNumberProvider
    )
  }

  @Test
  fun testNullability_reusuable() {
    val module = NullModule()
    val component = DaggerNullabilityTest_NullComponent.builder().nullModule(module).build()

    // Test that the @Nullable @Reusuable binding is cached properly even when the value is null.
    assertThat(module.integerCallCount).isEqualTo(0)
    assertThat(component.integer()).isNull()
    assertThat(module.integerCallCount).isEqualTo(1)
    assertThat(component.integer()).isNull()
    assertThat(module.integerCallCount).isEqualTo(1)
  }

  @Test
  fun testNullability_components() {
    val nullComponent: NullComponent =
      object : NullComponent {
        override fun string(): String? = null
        override fun integer(): Int? = null
        override fun stringProvider(): Provider<String> = Provider { null!! }
        override fun numberProvider(): Provider<Number> = Provider { null!! }
        override fun number(): Number = null!!
        override fun nullFoo(): NullFoo = null!!
      }
    val component =
      DaggerNullabilityTest_NullComponentWithDependency.builder()
        .nullComponent(nullComponent)
        .build()
    validate(component.string(), component.stringProvider(), component.numberProvider())

    // Also validate that the component's number() method fails
    try {
      component.number()
      fail()
    } catch (npe: NullPointerException) {
      // NOTE: In Java we would check that the Dagger error message is something like:
      //   "Cannot return null from a non-@Nullable @Provides method"
      // However, in Kotlin there's no way to return a null value from a non-null return type
      // without explicitly using `!!`, which results in an error before Dagger's runtime
      // checkNotNull has a chance to run.
    }
  }

  private fun validate(
    string: String?,
    stringProvider: Provider<String>,
    numberProvider: Provider<Number>
  ) {
    assertThat(string).isNull()
    assertThat(numberProvider).isNotNull()
    try {
      numberProvider.get()
      fail()
    } catch (npe: NullPointerException) {
      // NOTE: In Java we would check that the Dagger error message is something like:
      //   "Cannot return null from a non-@Nullable @Provides method"
      // However, in Kotlin there's no way to return a null value from a non-null return type
      // without explicitly using `!!`, which results in an error before Dagger's runtime
      // checkNotNull has a chance to run.
    }
    assertThat(stringProvider.get()).isNull()
  }
}
