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
import org.jspecify.annotations.Nullable
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// This is a regression test for b/290632844.
@RunWith(JUnit4::class)
public final class JspecifyNullableTest {
  @Component(modules = [MyIntComponent.MyModule::class])
  interface MyIntComponent {
    fun getInt(): Int

    @Module
    class MyModule(val intValue: Int) {
      // Check that using @Nullable on the type is ignored (matching KAPT).
      @Provides fun provideInt(): @Nullable Int = intValue
    }
  }

  @Component(modules = [MyNullableStringComponent.MyModule::class])
  interface MyNullableStringComponent {
    fun getString(): String?

    @Module
    class MyModule(val stringValue: String?) {
      // Check that using @Nullable on the type is ignored (matching KAPT).
      @Provides fun provideString(): @Nullable String? = stringValue
    }
  }

  @Test
  public fun testIntWithValue() {
    val component =
      DaggerJspecifyNullableTest_MyIntComponent.builder()
        .myModule(MyIntComponent.MyModule(15))
        .build()
    assertThat(component.getInt()).isEqualTo(15)
  }

  @Test
  public fun testStringWithValue() {
    val component =
      DaggerJspecifyNullableTest_MyNullableStringComponent.builder()
        .myModule(MyNullableStringComponent.MyModule("TEST_VALUE"))
        .build()
    assertThat(component.getString()).isEqualTo("TEST_VALUE")
  }

  @Test
  public fun testStringWithNull() {
    val component =
      DaggerJspecifyNullableTest_MyNullableStringComponent.builder()
        .myModule(MyNullableStringComponent.MyModule(null))
        .build()
    assertThat(component.getString()).isNull()
  }
}
