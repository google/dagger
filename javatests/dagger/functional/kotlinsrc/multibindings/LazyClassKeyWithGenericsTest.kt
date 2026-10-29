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

package dagger.functional.kotlinsrc.multibindings

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.LazyClassKey
import javax.inject.Provider
import javax.inject.Singleton
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LazyClassKeyWithGenericsTest {
  @Component(modules = [TestModule::class])
  @Singleton
  internal interface TestComponent {
    fun map(): Map<Class<*>, String>

    fun intMap(): Provider<Map<Class<*>, Provider<Int>>>
  }

  @Module
  internal object TestModule {
    @Provides
    @IntoMap
    @LazyClassKey(Thing::class)
    fun provideThingValue(): String = "Thing"

    @Provides
    @IntoMap
    @LazyClassKey(GenericThing::class)
    fun provideGenericThingValue(): String = "GenericThing"

    @Provides
    @IntoMap
    @LazyClassKey(Outer.NestedThing::class)
    fun provideNestedThingValue(): String = "NestedThing"

    @Provides
    @IntoMap
    @LazyClassKey(Thing::class)
    fun provideThingIntValue(): Int = 2

    @Provides
    @IntoMap
    @LazyClassKey(GenericThing::class)
    fun provideGenericThingIntValue(): Int = 1
  }

  internal class Thing

  internal class GenericThing<T>

  internal class Outer {
    internal class NestedThing
  }

  @Test
  fun test() {
    val testComponent = DaggerLazyClassKeyWithGenericsTest_TestComponent.create()
    val map = testComponent.map()
    val intMap = testComponent.intMap().get()
    // MapSubject#containsExactly uses entrySet, which is banned from DaggerClassKey map.
    assertThat(map[Thing::class.java]).isEqualTo("Thing")
    assertThat(map[GenericThing::class.java]).isEqualTo("GenericThing")
    assertThat(map[Outer.NestedThing::class.java]).isEqualTo("NestedThing")
    assertThat(intMap[Thing::class.java]!!.get()).isEqualTo(2)
    assertThat(intMap[GenericThing::class.java]!!.get()).isEqualTo(1)
  }
}
