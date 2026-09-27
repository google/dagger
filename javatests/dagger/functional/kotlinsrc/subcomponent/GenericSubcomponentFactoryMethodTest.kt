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

package dagger.functional.kotlinsrc.subcomponent

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for subcomponent factory methods defined on generic supertypes. */
@RunWith(JUnit4::class)
class GenericSubcomponentFactoryMethodTest {
  @Component(modules = [ParentModule::class])
  internal interface Parent : SubcomponentProvider<Child, ChildModule> {}

  @Module
  internal class ParentModule {
    @Provides fun provideInt(): Int = 42
  }

  @Subcomponent(modules = [ChildModule::class])
  internal interface Child {
    fun string(): String
  }

  @Module
  internal class ChildModule(val s: String) {
    @Provides fun provideString(i: Int): String = s + i
  }

  interface SubcomponentProvider<C, M> {
    fun createSubcomponent(module: M): C
  }

  @Test
  fun factoryMethod_genericSupertype() {
    val parent: Parent = DaggerGenericSubcomponentFactoryMethodTest_Parent.create()
    val child: Child = parent.createSubcomponent(ChildModule("hello "))
    assertThat(child.string()).isEqualTo("hello 42")
  }
}
