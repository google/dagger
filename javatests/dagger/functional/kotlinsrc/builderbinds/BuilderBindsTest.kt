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

package dagger.functional.kotlinsrc.builderbinds

import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import java.lang.IllegalStateException
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BuilderBindsTest {
  @Test
  fun builderBinds() {
    val builder =
      DaggerTestComponent.builder()
        .count(5)
        .l(10L)
        .input("foo")
        .nullableInput("bar")
        .listOfString(listOf("x", "y", "z"))
    builder.boundInSubtype(20)
    val component = builder.build()
    assertThat(component.count()).isEqualTo(5)
    assertThat(component.input()).isEqualTo("foo")
    assertThat(component.nullableInput()).isEqualTo("bar")
    assertThat(component.listOfString()).containsExactly("x", "y", "z").inOrder()
  }

  @Test
  fun builderBindsNullableWithNull() {
    val builder =
      DaggerTestComponent.builder()
        .count(5)
        .l(10L)
        .input("foo")
        .nullableInput(null)
        .listOfString(ImmutableList.of())
    builder.boundInSubtype(20)
    val component = builder.build()
    assertThat(component.count()).isEqualTo(5)
    assertThat(component.input()).isEqualTo("foo")
    assertThat(component.nullableInput()).isNull()
    assertThat(component.listOfString()).isEmpty()
  }

  @Test
  fun builderBindsPrimitiveNotSet() {
    try {
      val builder =
        DaggerTestComponent.builder()
          .l(10L)
          .input("foo")
          .nullableInput("bar")
          .listOfString(ImmutableList.of())
      builder.boundInSubtype(20)
      builder.build()
      fail("expected IllegalStateException")
    } catch (expected: IllegalStateException) {}
  }

  @Test
  fun builderBindsNonNullableNotSet() {
    try {
      val builder =
        DaggerTestComponent.builder()
          .count(5)
          .l(10L)
          .nullableInput("foo")
          .listOfString(ImmutableList.of())
      builder.boundInSubtype(20)
      builder.build()
      fail("expected IllegalStateException")
    } catch (expected: IllegalStateException) {}
  }

  @Test
  fun builderBindsNullableNotSet() {
    val builder =
      DaggerTestComponent.builder().count(5).l(10L).input("foo").listOfString(ImmutableList.of())
    builder.boundInSubtype(20)
    val component = builder.build()
    assertThat(component.count()).isEqualTo(5)
    assertThat(component.input()).isEqualTo("foo")
    assertThat(component.nullableInput()).isNull()
    assertThat(component.listOfString()).isEmpty()
  }
}
