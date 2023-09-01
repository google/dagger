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

package dagger.functional.kotlinsrc.multibindings

import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ClassKeyWithGenericsTest {
  @Component(modules = [TestModule::class])
  interface TestComponent {
    val map: Map<Class<*>, String>
  }

  @Module
  internal object TestModule {
    @Provides
    @IntoMap
    @ClassKey(Thing::class)
    fun provideThingValue(): String = "Thing"

    @Provides
    @IntoMap
    @ClassKey(GenericThing::class)
    fun provideAbstractThingValue(): String = "GenericThing"
  }

  class Thing

  class GenericThing<T>

  @Test
  fun test() {
    val map = DaggerClassKeyWithGenericsTest_TestComponent.create().map
    assertThat(map)
        .containsExactly(
            Thing::class.java, "Thing",
            GenericThing::class.java, "GenericThing");
  }
}
