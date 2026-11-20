/*
 * Copyright (C) 2026 The Dagger Authors.
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

package dagger.functional.kotlinsrc.multipackage

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.functional.kotlinsrc.multipackage.a.BoundedSubcomponent
import dagger.functional.kotlinsrc.multipackage.a.UsesInaccessibleBoundedGenerics
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BoundedGenericsMultipackageTest {
  @Component
  internal interface TestComponent {
    fun usesBoundedGenerics(): UsesInaccessibleBoundedGenerics

    fun boundedSubcomponent(): BoundedSubcomponent
  }

  @Test
  fun internalCyclicAndIntersectionBoundsWorkAcrossModules() {
    val component = DaggerBoundedGenericsMultipackageTest_TestComponent.create()
    assertThat(component.usesBoundedGenerics().summary()).isEqualTo("bound:other:bound")
    assertThat(component.boundedSubcomponent().boundedModuleValue()).isEqualTo("bound:other")
  }
}
