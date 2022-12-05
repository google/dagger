/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.functional.kotlinsrc.basic

import dagger.Component
import javax.inject.Inject
import javax.inject.Provider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * This is a regression test that makes sure component method order does not affect initialization
 * order.
 */
@RunWith(JUnit4::class)
class ComponentMethodTest {
  internal class Dep1 @Inject constructor(@Suppress("UNUSED_PARAMETER") dep2: Dep2)
  internal class Dep2 @Inject constructor(@Suppress("UNUSED_PARAMETER") dep3: Dep3)
  internal class Dep3 @Inject constructor()

  @Component
  internal interface NonTopologicalOrderComponent {
    fun dep1Provider(): Provider<Dep1>
    fun dep2Provider(): Provider<Dep2>
  }

  @Component
  internal interface TopologicalOrderComponent {
    fun dep2Provider(): Provider<Dep2>
    fun dep1Provider(): Provider<Dep1>
  }

  @Test
  fun testNonTopologicalOrderComponent() {
    val component = DaggerComponentMethodTest_NonTopologicalOrderComponent.create()
    component.dep1Provider().get()
    component.dep2Provider().get()
  }

  @Test
  fun testTopologicalOrderComponent() {
    val component = DaggerComponentMethodTest_TopologicalOrderComponent.create()
    component.dep1Provider().get()
    component.dep2Provider().get()
  }
}
