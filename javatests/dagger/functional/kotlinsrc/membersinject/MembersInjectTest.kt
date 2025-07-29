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

package dagger.functional.kotlinsrc.membersinject

import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import dagger.MembersInjector
import dagger.functional.kotlinsrc.membersinject.subpackage.a.AGrandchild
import dagger.functional.kotlinsrc.membersinject.subpackage.a.AParent
import dagger.functional.kotlinsrc.membersinject.subpackage.b.BChild
import dagger.internal.Provider
import javax.inject.Inject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MembersInjectTest {
  @Test
  fun testMembersInject_arrays() {
    val component = DaggerMembersInjectComponent.builder().build()
    val childOfStringArray = ChildOfStringArray()
    component.inject(childOfStringArray)
  }

  @Test
  fun testMembersInject_nestedArrays() {
    val component = DaggerMembersInjectComponent.builder().build()
    val childOfArrayOfParentOfStringArray = ChildOfArrayOfParentOfStringArray()
    component.inject(childOfArrayOfParentOfStringArray)
  }

  @Test
  fun testMembersInject_primitives() {
    val component = DaggerMembersInjectComponent.builder().build()
    val childOfPrimitiveIntArray = ChildOfPrimitiveIntArray()
    component.inject(childOfPrimitiveIntArray)
  }

  @Test
  fun testMembersInject_overrides() {
    val component = DaggerMembersInjectionVisibilityComponent.create()
    val aParent = AParent()
    component.inject(aParent)
    assertThat(aParent.aParentField()).isNotNull()
    assertThat(aParent.aParentMethod()).isNotNull()
    val aChild = BChild()
    component.inject(aChild)
    assertThat(aChild.aParentField()).isNotNull()
    assertThat(aChild.aParentMethod()).isNull()
    assertThat(aChild.aChildField()).isNotNull()
    assertThat(aChild.aChildMethod()).isNotNull()
    val aGrandchild = AGrandchild()
    component.inject(aGrandchild)
    assertThat(aGrandchild.aParentField()).isNotNull()
    assertThat(aGrandchild.aParentMethod()).isNotNull()
    assertThat(aGrandchild.aChildField()).isNotNull()
    assertThat(aGrandchild.aChildMethod()).isNull()
    assertThat(aGrandchild.aGrandchildField()).isNotNull()
    assertThat(aGrandchild.aGrandchildMethod()).isNotNull()
  }

  @Test
  fun testNonRequestedMembersInjector() {
    val child = NonRequestedChild()
    val provider = Provider { "field!" }
    val injector = NonRequestedChild_MembersInjector.create(provider)
    injector.injectMembers(child)
    assertThat(child.t).isEqualTo("field!")
  }

  class A : B() // No injected members

  open class B : C() // No injected members

  open class C {
    @Inject lateinit var value: String
  }

  @Component
  internal interface NonLocalMembersComponent {
    fun aMembersInjector(): MembersInjector<A>

    @Component.Factory
    interface Factory {
      fun create(@BindsInstance value: String): NonLocalMembersComponent
    }
  }

  @Test
  fun testNonLocalMembersInjection() {
    val membersInjector =
      DaggerMembersInjectTest_NonLocalMembersComponent.factory().create("test").aMembersInjector()
    val testA = A()
    membersInjector.injectMembers(testA)
    assertThat(testA.value).isEqualTo("test")
  }
}
