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

package dagger.functional.kotlinsrc.modules

import com.google.common.truth.Truth
import dagger.Component
import dagger.functional.kotlinsrc.modules.subpackage.FooForProvision
import dagger.functional.kotlinsrc.modules.subpackage.PublicModule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
internal class ModuleIncludesTest {
  @Component(modules = [PublicModule::class])
  interface TestComponent {
    fun getObject(): Any
    fun fooForProvision(): FooForProvision
  }

  @Test
  fun publicModuleIncludingPackagePrivateModuleThatDoesNotRequireInstance() {
    val component = DaggerModuleIncludesTest_TestComponent.create()
    Truth.assertThat(component.getObject()).isEqualTo("foo42")
  }
}
