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

package dagger.functional.kotlinsrc.binds

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BindsTest {
  private lateinit var component: TestComponent

  @Before
  fun setUp() {
    component = DaggerTestComponent.create()
  }

  @Test
  fun bindDelegates() {
    assertThat(component.someObject()).isInstanceOf(FooOfStrings::class.java)
    assertThat(component.fooOfStrings()).isInstanceOf(FooOfStrings::class.java)
    assertThat(component.fooOfObjects()).isInstanceOf(FooOfObjects::class.java)
    assertThat(component.fooOfIntegers()).isNotNull()
  }

  @Test
  fun bindWithScope() {
    assertThat(component.qualifiedFooOfStrings())
      .isSameInstanceAs(component.qualifiedFooOfStrings())
  }

  @Test
  fun multibindings() {
    assertThat(component.foosOfNumbers()).hasSize(2)
    assertThat(component.someObjects()).hasSize(3)
    assertThat(component.charSequences()).hasSize(5)
    assertThat(component.notExposedString()).isEqualTo("not exposed")
    assertThat(component.integerObjectMap())
      .containsExactly(
        123, "123-string",
        456, "456-string",
        789, "789-string"
      )
    assertThat(component.integerProviderOfObjectMap()).hasSize(3)
    assertThat(component.integerProviderOfObjectMap()[123]!!.get()).isEqualTo("123-string")
    assertThat(component.integerProviderOfObjectMap()[456]!!.get()).isEqualTo("456-string")
    assertThat(component.integerProviderOfObjectMap()[789]!!.get()).isEqualTo("789-string")
    assertThat(component.qualifiedIntegerObjectMap()).hasSize(1)
    assertThat(component.primitiveSet()).containsExactly(100)
    assertThat(component.primitiveValueMap()).containsExactly(10, 100)
  }
}
