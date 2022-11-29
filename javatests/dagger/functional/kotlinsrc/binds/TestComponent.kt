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

import dagger.Component
import dagger.functional.kotlinsrc.binds.subpackage.ExposedModule
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
@Component(modules = [SimpleBindingModule::class, ExposedModule::class])
interface TestComponent {
  fun someObject(): Any

  fun notExposedString(): String

  @SomeQualifier fun reusableObject(): Any

  fun fooOfStrings(): Foo<String>

  fun fooOfObjects(): Foo<Any>

  @SomeQualifier fun qualifiedFooOfStrings(): Foo<String>

  fun fooOfIntegers(): Foo<Int>

  fun foosOfNumbers(): Set<Foo<out Number>>

  fun someObjects(): Set<Any>

  fun charSequences(): Set<CharSequence>

  fun integerObjectMap(): Map<Int, Any>

  fun integerProviderOfObjectMap(): Map<Int, Provider<Any>>

  @SomeQualifier fun qualifiedIntegerObjectMap(): Map<Int, Any>

  @SomeQualifier fun uniquePrimitive(): Int

  fun primitiveSet(): Set<Int>

  fun primitiveValueMap(): Map<Int, Int>
}
