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

package dagger.functional.kotlinsrc.builder

import com.google.common.truth.Truth.assertThat
import dagger.Component
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BuildMethodCovariantReturnInheritedTest {
  @Component
  internal interface Simple {
    interface BuilderSupertype {
      fun build(): Any
    }

    @Component.Builder interface Builder : BuilderSupertype
  }

  internal interface ComponentSupertype

  @Component
  internal interface GenericBuilderType : ComponentSupertype {
    interface GenericBuilderSupertype<T> {
      fun build(): T
    }

    @Component.Builder interface Builder : GenericBuilderSupertype<ComponentSupertype>
  }

  internal interface ParameterizedComponentSupertype<T>

  @Component
  internal interface GenericComponentSupertypeAndBuilderSupertype :
    ParameterizedComponentSupertype<Any> {

    interface GenericBuilderSupertype<T> {
      fun build(): ParameterizedComponentSupertype<T>
    }

    @Component.Builder interface Builder : GenericBuilderSupertype<Any>
  }

  @Test
  fun simpleComponentTest() {
    val component = DaggerBuildMethodCovariantReturnInheritedTest_Simple.builder().build()
    assertThat(component).isInstanceOf(Simple::class.java)
  }

  @Test
  fun genericBuilderTypeTest() {
    val component = 
        DaggerBuildMethodCovariantReturnInheritedTest_GenericBuilderType.builder().build()
    assertThat(component).isInstanceOf(GenericBuilderType::class.java)
  }

  @Test
  fun genericComponentSupertypeAndBuilderSupertypeTest() {
    val component =
        DaggerBuildMethodCovariantReturnInheritedTest_GenericComponentSupertypeAndBuilderSupertype
            .builder()
            .build()
    assertThat(component).isInstanceOf(GenericComponentSupertypeAndBuilderSupertype::class.java)
  }
}
