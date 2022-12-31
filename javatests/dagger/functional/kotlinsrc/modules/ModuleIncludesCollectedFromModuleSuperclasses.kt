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

import dagger.Component
import dagger.Module
import dagger.Provides

/** This tests that @Module.includes are traversed for supertypes of a module. */
internal class ModuleIncludesCollectedFromModuleSuperclasses {
  @Component(modules = [TopLevelModule::class])
  interface C {
    fun foo(): Foo<String>
    fun includedInTopLevelModule(): Int
    fun includedFromModuleInheritance(): String
  }

  @Module(includes = [IncludedTopLevel::class])
  class TopLevelModule : FooModule<String>()
  class Foo<T>

  @Module(includes = [IncludedFromModuleInheritance::class])
  abstract class FooModule<T> : FooCreator() {
    @Provides
    fun fooOfT(): Foo<T> {
      return createFoo()
    }
  }

  open class FooCreator {
    fun <T> createFoo(): Foo<T> {
      return Foo()
    }
  }

  @Module
  class IncludedTopLevel {
    @Provides fun i(): Int = 123
  }

  @Module
  class IncludedFromModuleInheritance {
    @Provides fun inheritedProvision(): String = "inherited"
  }
}
