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

import com.google.common.truth.MapSubject
import com.google.common.truth.Truth.assertThat
import dagger.Binds
import dagger.Component
import dagger.Module
import dagger.Provides
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BindsCollectionsWithoutMultibindingsTest {
  @Module
  internal abstract class M {
    @Binds
    abstract fun bindStringSet(set: HashSet<String>): Set<String>

    @Binds
    abstract fun bindStringMap(map: HashMap<String, String>): Map<String, String>

    companion object {
      @Provides
      fun provideHashSet(): HashSet<String> = hashSetOf("binds", "set")

      @Provides
      fun provideHashMap(): HashMap<String, String> = hashMapOf(
        "binds" to "map",
        "without" to "multibindings"
      )
    }
  }

  @Component(modules = [M::class])
  internal interface C {
    fun set(): Set<String>
    fun map(): Map<String, String>
  }

  @Test
  fun works() {
    val component = DaggerBindsCollectionsWithoutMultibindingsTest_C.create()
    assertThat(component.set()).containsExactly("binds", "set")
    assertThat(component.map())
        .containsExactly(
          "binds", "map",
          "without", "multibindings"
        )
  }
}
