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

package dagger.functional.kotlinsrc.builder

import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests that `@BindsInstance` works when applied to the parameter of a builder's setter method. */
@RunWith(JUnit4::class)
class BuilderBindsInstanceParameterTest {
  @Component
  internal interface TestComponent {
    fun s(): String
    fun i(): Int

    @Component.Builder
    interface Builder {
      // https://github.com/google/dagger/issues/1464
      fun s(@BindsInstance notTheSameNameAsMethod: String): Builder
      fun i(@BindsInstance i: Int): Builder
      fun build(): TestComponent
    }
  }

  @Test
  fun builder_bindsInstanceOnParameter_allowed() {
    val component =
      DaggerBuilderBindsInstanceParameterTest_TestComponent.builder().s("hello").i(42).build()
    assertThat(component.s()).isEqualTo("hello")
    assertThat(component.i()).isEqualTo(42)
  }
}
