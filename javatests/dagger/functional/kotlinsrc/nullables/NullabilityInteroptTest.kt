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
import javax.inject.Inject
import javax.inject.Qualifier
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * A test that ensures nullable bindings created using an {@code @Nullable} annotation are
 * interoperable with nullable bindings created using {@code T?} types in kotlin source.
 */
@RunWith(JUnit4::class)
class NullabilityInteroptTest {
  annotation class Nullable

  @Qualifier annotation class ProvidedWithNullable

  @Qualifier annotation class ProvidedWithNullType

  @Component(modules = [TestModule::class])
  interface TestComponent {
    fun providesUsage(): ProvidesUsage

    fun injectUsage(): InjectUsage

    @ProvidedWithNullable @Nullable fun nullableWithNullable(): String

    @ProvidedWithNullable fun nullableWithNullType(): String?

    @ProvidedWithNullType @Nullable fun nullTypeWithNullable(): String

    @ProvidedWithNullType fun nullTypeWithNullType(): String?
  }

  @Module
  object TestModule {
    @Provides
    @ProvidedWithNullable
    @Nullable
    fun providedWithNullable(): String = PROVIDED_WITH_NULLABLE

    @Provides @ProvidedWithNullType fun providedWithNullType(): String? = PROVIDED_WITH_NULL_TYPE

    @Provides
    fun providesUsage(
      @ProvidedWithNullable nullableWithNullType: String?,
      @ProvidedWithNullable @Nullable nullableWithNullable: String,
      @ProvidedWithNullType nullTypeWithNullType: String?,
      @ProvidedWithNullType @Nullable nullTypeWithNullable: String,
    ): ProvidesUsage {
      return ProvidesUsage(
        nullableWithNullType,
        nullableWithNullable,
        nullTypeWithNullType,
        nullTypeWithNullable,
      )
    }
  }

  class ProvidesUsage
  constructor(
    val nullableWithNullType: String?,
    val nullableWithNullable: String,
    val nullTypeWithNullType: String?,
    val nullTypeWithNullable: String,
  )

  class InjectUsage
  @Inject
  constructor(
    @ProvidedWithNullable val nullableWithNullType: String?,
    @ProvidedWithNullable @Nullable val nullableWithNullable: String,
    @ProvidedWithNullType val nullTypeWithNullType: String?,
    @ProvidedWithNullType @Nullable val nullTypeWithNullable: String,
  )

  @Test
  fun testEntryPoints() {
    val component = DaggerNullabilityInteroptTest_TestComponent.create()
    assertThat(component.nullableWithNullable()).isEqualTo(PROVIDED_WITH_NULLABLE)
    assertThat(component.nullableWithNullType()).isEqualTo(PROVIDED_WITH_NULLABLE)
    assertThat(component.nullTypeWithNullable()).isEqualTo(PROVIDED_WITH_NULL_TYPE)
    assertThat(component.nullTypeWithNullType()).isEqualTo(PROVIDED_WITH_NULL_TYPE)
  }

  @Test
  fun testInjectUsage() {
    val injectUsage = DaggerNullabilityInteroptTest_TestComponent.create().injectUsage()
    assertThat(injectUsage.nullableWithNullable).isEqualTo(PROVIDED_WITH_NULLABLE)
    assertThat(injectUsage.nullableWithNullType).isEqualTo(PROVIDED_WITH_NULLABLE)
    assertThat(injectUsage.nullTypeWithNullable).isEqualTo(PROVIDED_WITH_NULL_TYPE)
    assertThat(injectUsage.nullTypeWithNullType).isEqualTo(PROVIDED_WITH_NULL_TYPE)
  }

  @Test
  fun testProvidesUsage() {
    val providesUsage = DaggerNullabilityInteroptTest_TestComponent.create().providesUsage()
    assertThat(providesUsage.nullableWithNullable).isEqualTo(PROVIDED_WITH_NULLABLE)
    assertThat(providesUsage.nullableWithNullType).isEqualTo(PROVIDED_WITH_NULLABLE)
    assertThat(providesUsage.nullTypeWithNullable).isEqualTo(PROVIDED_WITH_NULL_TYPE)
    assertThat(providesUsage.nullTypeWithNullType).isEqualTo(PROVIDED_WITH_NULL_TYPE)
  }

  companion object {
    const val PROVIDED_WITH_NULLABLE: String = "ProvidedWithNullable"
    const val PROVIDED_WITH_NULL_TYPE: String = "ProvidedWithNullType"
  }
}
