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

package dagger.functional.kotlinsrc.basic

import dagger.Component
import dagger.Lazy
import dagger.MembersInjector
import javax.inject.Provider

@Component(modules = [PrimitivesModule::class, NullableModule::class])
interface BasicComponent :
  Injector<Thing>,
  // Implements two types that define the same method, not overridden here, to test that the
  // method is implemented only once.
  ComponentSupertypeOne,
  ComponentSupertypeTwo {

  fun byteFun(): Byte
  fun charFun(): Char
  fun shortFun(): Short
  fun intFun(): Int
  fun longFun(): Long
  fun booleanFun(): Boolean
  fun floatFun(): Float
  fun doubleFun(): Double

  fun byteProviderFun(): Provider<Byte>
  fun charProviderFun(): Provider<Char>
  fun shortProviderFun(): Provider<Short>
  fun intProviderFun(): Provider<Int>
  fun longProviderFun(): Provider<Long>
  fun booleanProviderFun(): Provider<Boolean>
  fun floatProviderFun(): Provider<Float>
  fun doubleProviderFun(): Provider<Double>

  fun byteArrayFun(): ByteArray
  fun charArrayFun(): CharArray
  fun shortArrayFun(): ShortArray
  fun intArrayFun(): IntArray
  fun longArrayFun(): LongArray
  fun booleanArrayFun(): BooleanArray
  fun floatArrayFun(): FloatArray
  fun doubleArrayFun(): DoubleArray

  fun byteArrayProviderFun(): Provider<ByteArray>
  fun charArrayProviderFun(): Provider<CharArray>
  fun shortArrayProviderFun(): Provider<ShortArray>
  fun intArrayProviderFun(): Provider<IntArray>
  fun longArrayProviderFun(): Provider<LongArray>
  fun booleanArrayProviderFun(): Provider<BooleanArray>
  fun floatArrayProviderFun(): Provider<FloatArray>
  fun doubleArrayProviderFun(): Provider<DoubleArray>

  fun noOpMembersInjectionFun(obviouslyDoesNotHaveMembersToInject: Any): Any

  fun thingFun(): Thing
  fun injectedThingFun(): InjectedThing
  fun injectedThingProviderFun(): Provider<InjectedThing>
  fun lazyInjectedThingFun(): Lazy<InjectedThing>
  fun lazyInjectedThingProviderFun(): Provider<Lazy<InjectedThing>>
  fun injectedThingMembersInjectorFun(): MembersInjector<InjectedThing>

  @NullableModule.Nullable fun nullObjectFun(): Any
  fun nullObjectProviderFun(): Provider<Any>
  fun lazyNullObjectFun(): Lazy<Any>
  fun typeWithInheritedMembersInjectionFun(): TypeWithInheritedMembersInjection
  fun typeWithInheritedMembersInjectionMembersInjectorFun():
      MembersInjector<TypeWithInheritedMembersInjection>

  val byteVal: Byte
  val charVal: Char
  val shortVal: Short
  val intVal: Int
  val longVal: Long
  val booleanVal: Boolean
  val floatVal: Float
  val doubleVal: Double

  val byteProviderVal: Provider<Byte>
  val charProviderVal: Provider<Char>
  val shortProviderVal: Provider<Short>
  val intProviderVal: Provider<Int>
  val longProviderVal: Provider<Long>
  val booleanProviderVal: Provider<Boolean>
  val floatProviderVal: Provider<Float>
  val doubleProviderVal: Provider<Double>

  val byteArrayVal: ByteArray
  val charArrayVal: CharArray
  val shortArrayVal: ShortArray
  val intArrayVal: IntArray
  val longArrayVal: LongArray
  val booleanArrayVal: BooleanArray
  val floatArrayVal: FloatArray
  val doubleArrayVal: DoubleArray

  val byteArrayProviderVal: Provider<ByteArray>
  val charArrayProviderVal: Provider<CharArray>
  val shortArrayProviderVal: Provider<ShortArray>
  val intArrayProviderVal: Provider<IntArray>
  val longArrayProviderVal: Provider<LongArray>
  val booleanArrayProviderVal: Provider<BooleanArray>
  val floatArrayProviderVal: Provider<FloatArray>
  val doubleArrayProviderVal: Provider<DoubleArray>

  val thingVal: Thing
  val injectedThingVal: InjectedThing
  val injectedThingProviderVal: Provider<InjectedThing>
  val lazyInjectedThingVal: Lazy<InjectedThing>
  val lazyInjectedThingProviderVal: Provider<Lazy<InjectedThing>>
  val injectedThingMembersInjectorVal: MembersInjector<InjectedThing>

  // TODO(b/261506732): Support nullable and qualifiers on properties without using @get:
  @get:NullableModule.Nullable val nullObjectVal: Any
  val nullObjectProviderVal: Provider<Any>
  val lazyNullObjectVal: Lazy<Any>
  val typeWithInheritedMembersInjectionVal: TypeWithInheritedMembersInjection
  val typeWithInheritedMembersInjectionMembersInjectorVal:
      MembersInjector<TypeWithInheritedMembersInjection>
}
