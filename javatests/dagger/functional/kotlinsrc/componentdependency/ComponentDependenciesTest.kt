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

package dagger.functional.kotlinsrc.componentdependency

import com.google.common.truth.Truth.assertThat
import dagger.Component
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests component dependencies. */
@RunWith(JUnit4::class)
internal class ComponentDependenciesTest {
  interface One {
    fun string(): String
  }

  interface Two {
    fun string(): String
  }

  interface Merged : One, Two

  @Component(dependencies = [Merged::class])
  interface TestComponent {
    fun string(): String

    @Component.Builder
    interface Builder {
      fun dep(dep: Merged): Builder
      fun build(): TestComponent
    }
  }

  @Test
  fun testSameMethodTwice() {
    val component =
      DaggerComponentDependenciesTest_TestComponent.builder()
        .dep(
          object : Merged {
            override fun string() = "test"
          }
        )
        .build()
    assertThat(component.string()).isEqualTo("test")
  }

  interface OneOverride {
    fun string(): Any
  }

  interface TwoOverride {
    fun string(): Any
  }

  interface MergedOverride : OneOverride, TwoOverride {
    override fun string(): String
  }

  @Component(dependencies = [MergedOverride::class])
  interface TestOverrideComponent {
    fun string(): String

    @Component.Builder
    interface Builder {
      fun dep(dep: MergedOverride): Builder
      fun build(): TestOverrideComponent
    }
  }

  @Test
  fun testPolymorphicOverridesStillCompiles() {
    val component =
      DaggerComponentDependenciesTest_TestOverrideComponent.builder()
        .dep(
          object : MergedOverride {
            override fun string() = "test"
          }
        )
        .build()
    assertThat(component.string()).isEqualTo("test")
  }
}
