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

package dagger.functional.kotlinsrc.membersinject

import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Inject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

// Regression test for https://github.com/google/dagger/issues/3995.
@RunWith(JUnit4::class)
internal class MembersInjectionWithJavaKeywordNamesTest {
  @Component
  interface MyComponent {
    fun myClass(): MyClass

    @Component.Builder
    interface Builder {
      @BindsInstance fun addInteger(int: Int): Builder
      @BindsInstance fun addString(string: String): Builder
      @BindsInstance fun addLong(long: Long): Builder
      fun build(): MyComponent
    }
  }

  @Suppress("BadInject")
  class MyClass @Inject constructor(val int: Int) {
    @Inject @JvmField var string: String = ""

    var long: Long? = null

    @Inject fun injectMethod(long: Long) {
      this.long = long
    }
  }

  @Test
  fun testParametersWithJavaKeywordNames() {
    val int = 1
    val long = 2L
    val string = "string"
    val myClass =
      DaggerMembersInjectionWithJavaKeywordNamesTest_MyComponent.builder()
        .addInteger(int)
        .addString(string)
        .addLong(long)
        .build()
        .myClass()
    assertThat(myClass.int).isEqualTo(int)
    assertThat(myClass.long).isEqualTo(long)
    assertThat(myClass.string).isEqualTo(string)
  }
}
