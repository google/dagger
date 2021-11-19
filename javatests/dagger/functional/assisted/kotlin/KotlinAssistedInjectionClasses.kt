/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.functional.assisted.kotlin

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Inject


class Dep @Inject constructor()

class AssistedDep

/** Assisted injection for a kotlin class. */
class Foo @AssistedInject constructor(val dep: Dep, @Assisted val assistedDep: AssistedDep)

/** Assisted injection for a kotlin data class. */
data class FooData
@AssistedInject
constructor(val dep: Dep, @Assisted val assistedDep: AssistedDep)

/** Assisted factory for a kotlin class */
@AssistedFactory
interface FooFactory {
  fun create(assistedDep: AssistedDep): Foo
}

/** Assisted factory for a kotlin data class */
@AssistedFactory
interface FooDataFactory {
  fun create(assistedDep: AssistedDep): FooData
}

/** Kotlin classes for regression test of https://github.com/google/dagger/issues/3065. */
class BarManager
@AssistedInject
internal constructor(@Assisted val bar: Bar, @Assisted val name: String) {
  @AssistedFactory
  interface Factory {
    operator fun Bar.invoke(name: String): BarManager
  }
}

class Bar
