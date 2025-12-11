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
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [LazyMaps]. */
@RunWith(JUnit4::class)
class LazyMapsTest {
  @Test
  fun mapOfLazies() {
    val component = DaggerLazyMaps_TestComponent.create()
    val laziesMap = component.mapOfLazy()
    val firstGet = laziesMap["key"]!!.get()
    assertThat(firstGet).isEqualTo("value-1")
    assertThat(firstGet).isSameInstanceAs(laziesMap["key"]!!.get())
    assertThat(component.mapOfLazy()["key"]!!.get()).isEqualTo("value-2")

    val bindsGet = laziesMap["binds-key"]!!.get()
    assertThat(bindsGet).isEqualTo("value-3")
    assertThat(bindsGet).isSameInstanceAs(laziesMap["binds-key"]!!.get())
    assertThat(component.mapOfLazy()["binds-key"]!!.get()).isEqualTo("value-4")
  }

  @Test
  fun mapOfProviderOfLaziesReturnsDifferentLazy() {
    val component = DaggerLazyMaps_TestComponent.create()
    val providersOfLaziesMap = component.mapOfProviderOfLazy()
    assertThat(providersOfLaziesMap["key"]!!.get().get())
      .isNotEqualTo(providersOfLaziesMap["key"]!!.get().get())
  }
}
