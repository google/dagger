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

package dagger.functional.kotlinsrc.builderbinds

import dagger.BindsInstance
import dagger.Component
import javax.inject.Named

@Component
interface TestComponent {
  fun count(): Int
  fun l(): Long

  @Named("input") fun input(): String

  @Named("nullable input") fun nullableInput(): String?

  fun listOfString(): List<String>

  @Named("subtype") fun boundInSubtype(): Int

  @Component.Builder
  interface Builder : BuilderSupertype {
    @BindsInstance fun count(count: Int): Builder

    @BindsInstance fun l(l: Long): Builder

    @BindsInstance fun input(@Named("input") input: String): Builder

    @BindsInstance fun nullableInput(@Named("nullable input") nullableInput: String?): Builder

    @BindsInstance fun listOfString(listOfString: List<String>): Builder

    fun build(): TestComponent
  }
}
