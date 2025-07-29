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

package dagger.functional.kotlinsrc.assisted.kotlin

import com.google.common.truth.Truth.assertThat
import dagger.Component
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// This is a regression test for https://github.com/google/dagger/issues/2299
@RunWith(JUnit4::class)
class KotlinAssistedInjectionTest {
  @Component
  internal interface TestComponent {
    fun fooFactory(): FooFactory

    fun fooDataFactory(): FooDataFactory

    fun barManagerFactory(): BarManager.Factory

  }

  @Test
  fun testFooFactory() {
    val fooFactory = DaggerKotlinAssistedInjectionTest_TestComponent.create().fooFactory()
    val assistedDep = AssistedDep()
    val foo = fooFactory.create(assistedDep)
    assertThat(foo.assistedDep).isEqualTo(assistedDep)
  }

  @Test
  fun testFooDataFactory() {
    val fooDataFactory = DaggerKotlinAssistedInjectionTest_TestComponent.create().fooDataFactory()
    val assistedDep = AssistedDep()
    val fooData = fooDataFactory.create(assistedDep)
    assertThat(fooData.assistedDep).isEqualTo(assistedDep)
  }

  @Test
  fun testBarManager() {
    val barManagerFactory =
      DaggerKotlinAssistedInjectionTest_TestComponent.create().barManagerFactory()
    val bar = Bar()
    val name = "someName"
    val barManager = barManagerFactory.run { bar(name) }
    assertThat(barManager.bar).isEqualTo(bar)
    assertThat(barManager.name).isEqualTo(name)
  }
}
