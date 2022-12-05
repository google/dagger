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

import dagger.Lazy
import dagger.MembersInjector
import javax.inject.Inject
import javax.inject.Provider

@Suppress("UNUSED_PARAMETER", "BadInject")
class InjectedThing
@Inject
internal constructor(
  primitiveByte: Byte,
  primitiveChar: Char,
  primitiveShort: Short,
  primitiveInt: Int,
  primitiveLong: Long,
  primitiveBoolean: Boolean,
  primitiveFloat: Float,
  primitiveDouble: Double,
  byteProvider: Provider<Byte>,
  charProvider: Provider<Char>,
  shortProvider: Provider<Short>,
  intProvider: Provider<Int>,
  longProvider: Provider<Long>,
  booleanProvider: Provider<Boolean>,
  floatProvider: Provider<Float>,
  doubleProvider: Provider<Double>,
  lazyByte: Lazy<Byte>,
  lazyChar: Lazy<Char>,
  lazyShort: Lazy<Short>,
  lazyInt: Lazy<Int>,
  lazyLong: Lazy<Long>,
  lazyBoolean: Lazy<Boolean>,
  lazyFloat: Lazy<Float>,
  lazyDouble: Lazy<Double>,
  boxedBype: Byte,
  boxedChar: Char,
  boxedShort: Short,
  boxedInt: Int,
  boxedLong: Long,
  boxedBoolean: Boolean,
  boxedFloat: Float,
  boxedDouble: Double,
  byteArray: ByteArray,
  charArray: CharArray,
  shortArray: ShortArray,
  intArray: IntArray,
  longArray: LongArray,
  booleanArray: BooleanArray,
  floatArray: FloatArray,
  doubleArray: DoubleArray,
  byteArrayProvider: Provider<ByteArray>,
  charArrayProvider: Provider<CharArray>,
  shortArrayProvider: Provider<ShortArray>,
  intArrayProvider: Provider<IntArray>,
  longArrayProvider: Provider<LongArray>,
  booleanArrayProvider: Provider<BooleanArray>,
  floatArrayProvider: Provider<FloatArray>,
  doubleArrayProvider: Provider<DoubleArray>,
  lazyByteArray: Lazy<ByteArray>,
  lazyCharArray: Lazy<CharArray>,
  lazyShortArray: Lazy<ShortArray>,
  lazyIntArray: Lazy<IntArray>,
  lazyLongArray: Lazy<LongArray>,
  lazyBooleanArray: Lazy<BooleanArray>,
  lazy: Lazy<FloatArray>,
  lazyDoubleArray: Lazy<DoubleArray>,
  thing: Thing,
  thingProvider: Provider<Thing>,
  lazyThing: Lazy<Thing>,
  lazyThingProvider: Provider<Lazy<Thing>>,
  thingMembersInjector: MembersInjector<Thing>
) {

  // TODO(b/261299441): @JvmField is needed here because otherwise the annotation will be placed on
  // the private backing field and Dagger will fail. The @JvmField will force the backing field
  // visibility to match the property visibility (internal in this case).
  @JvmField @Inject internal var primitiveByte: Byte = 0.toByte()
  @JvmField @Inject internal var primitiveChar: Char = 0.toChar()
  @JvmField @Inject internal var primitiveShort: Short = 0.toShort()
  @JvmField @Inject internal var primitiveInt: Int = 0.toInt()
  @JvmField @Inject internal var primitiveLong: Long = 0.toLong()
  @JvmField @Inject internal var primitiveBoolean: Boolean = false
  @JvmField @Inject internal var primitiveFloat: Float = 0.toFloat()
  @JvmField @Inject internal var primitiveDouble: Double = 0.toDouble()

  @Inject internal lateinit var byteProvider: Provider<Byte>
  @Inject internal lateinit var charProvider: Provider<Char>
  @Inject internal lateinit var shortProvider: Provider<Short>
  @Inject internal lateinit var intProvider: Provider<Int>
  @Inject internal lateinit var longProvider: Provider<Long>
  @Inject internal lateinit var booleanProvider: Provider<Boolean>
  @Inject internal lateinit var floatProvider: Provider<Float>
  @Inject internal lateinit var doubleProvider: Provider<Double>
  @Inject internal lateinit var lazyByte: Lazy<Byte>
  @Inject internal lateinit var lazyChar: Lazy<Char>
  @Inject internal lateinit var lazyShort: Lazy<Short>
  @Inject internal lateinit var lazyInt: Lazy<Int>
  @Inject internal lateinit var lazyLong: Lazy<Long>
  @Inject internal lateinit var lazyBoolean: Lazy<Boolean>
  @Inject internal lateinit var lazyFloat: Lazy<Float>
  @Inject internal lateinit var lazyDouble: Lazy<Double>
  @Inject internal lateinit var byteArray: ByteArray
  @Inject internal lateinit var charArray: CharArray
  @Inject internal lateinit var shortArray: ShortArray
  @Inject internal lateinit var intArray: IntArray
  @Inject internal lateinit var longArray: LongArray
  @Inject internal lateinit var booleanArray: BooleanArray
  @Inject internal lateinit var floatArray: FloatArray
  @Inject internal lateinit var doubleArray: DoubleArray
  @Inject internal lateinit var byteArrayProvider: Provider<ByteArray>
  @Inject internal lateinit var charArrayProvider: Provider<CharArray>
  @Inject internal lateinit var shortArrayProvider: Provider<ShortArray>
  @Inject internal lateinit var intArrayProvider: Provider<IntArray>
  @Inject internal lateinit var longArrayProvider: Provider<LongArray>
  @Inject internal lateinit var booleanArrayProvider: Provider<BooleanArray>
  @Inject internal lateinit var floatArrayProvider: Provider<FloatArray>
  @Inject internal lateinit var doubleArrayProvider: Provider<DoubleArray>
  @Inject internal lateinit var lazyByteArray: Lazy<ByteArray>
  @Inject internal lateinit var lazyCharArray: Lazy<CharArray>
  @Inject internal lateinit var lazyShortArray: Lazy<ShortArray>
  @Inject internal lateinit var lazyIntArray: Lazy<IntArray>
  @Inject internal lateinit var lazyLongArray: Lazy<LongArray>
  @Inject internal lateinit var lazyBooleanArray: Lazy<BooleanArray>
  @Inject internal lateinit var lazy: Lazy<FloatArray>
  @Inject internal lateinit var lazyDoubleArray: Lazy<DoubleArray>
  @Inject internal lateinit var thing: Thing
  @Inject internal lateinit var thingProvider: Provider<Thing>
  @Inject internal lateinit var lazyThing: Lazy<Thing>
  @Inject internal lateinit var lazyThingProvider: Provider<Lazy<Thing>>
  @Inject internal lateinit var thingMembersInjector: MembersInjector<Thing>

  @Inject internal fun primitiveByte(primitiveByte: Byte) {}
  @Inject internal fun primitiveChar(primitiveChar: Char) {}
  @Inject internal fun primitiveShort(primitiveShort: Short) {}
  @Inject internal fun primitiveInt(primitiveInt: Int) {}
  @Inject internal fun primitiveLong(primitiveLong: Long) {}
  @Inject internal fun primitiveBoolean(primitiveBoolean: Boolean) {}
  @Inject internal fun primitiveFloat(primitiveFloat: Float) {}
  @Inject internal fun primitiveDouble(primitiveDouble: Double) {}
  @Inject internal fun byteProvider(byteProvider: Provider<Byte>) {}
  @Inject internal fun charProvider(charProvider: Provider<Char>) {}
  @Inject internal fun shortProvider(shortProvider: Provider<Short>) {}
  @Inject internal fun intProvider(intProvider: Provider<Int>) {}
  @Inject internal fun longProvider(longProvider: Provider<Long>) {}
  @Inject internal fun booleanProvider(booleanProvider: Provider<Boolean>) {}
  @Inject internal fun floatProvider(floatProvider: Provider<Float>) {}
  @Inject internal fun doubleProvider(doubleProvider: Provider<Double>) {}
  @Inject internal fun lazyByte(lazyByte: Lazy<Byte>) {}
  @Inject internal fun lazyChar(lazyChar: Lazy<Char>) {}
  @Inject internal fun lazyShort(lazyShort: Lazy<Short>) {}
  @Inject internal fun lazyInt(lazyInt: Lazy<Int>) {}
  @Inject internal fun lazyLong(lazyLong: Lazy<Long>) {}
  @Inject internal fun lazyBoolean(lazyBoolean: Lazy<Boolean>) {}
  @Inject internal fun lazyFloat(lazyFloat: Lazy<Float>) {}
  @Inject internal fun lazyDouble(lazyDouble: Lazy<Double>) {}
  @Inject internal fun byteArray(byteArray: ByteArray) {}
  @Inject internal fun charArray(charArray: CharArray) {}
  @Inject internal fun shortArray(shortArray: ShortArray) {}
  @Inject internal fun intArray(intArray: IntArray) {}
  @Inject internal fun longArray(longArray: LongArray) {}
  @Inject internal fun booleanArray(booleanArray: BooleanArray) {}
  @Inject internal fun floatArray(floatArray: FloatArray) {}
  @Inject internal fun doubleArray(doubleArray: DoubleArray) {}
  @Inject internal fun byteArrayProvider(byteArrayProvider: Provider<ByteArray>) {}
  @Inject internal fun charArrayProvider(charArrayProvider: Provider<CharArray>) {}
  @Inject internal fun shortArrayProvider(shortArrayProvider: Provider<ShortArray>) {}
  @Inject internal fun intArrayProvider(intArrayProvider: Provider<IntArray>) {}
  @Inject internal fun longArrayProvider(longArrayProvider: Provider<LongArray>) {}
  @Inject internal fun booleanArrayProvider(booleanArrayProvider: Provider<BooleanArray>) {}
  @Inject internal fun floatArrayProvider(floatArrayProvider: Provider<FloatArray>) {}
  @Inject internal fun doubleArrayProvider(doubleArrayProvider: Provider<DoubleArray>) {}
  @Inject internal fun lazyByteArray(lazyByteArray: Lazy<ByteArray>) {}
  @Inject internal fun lazyCharArray(lazyCharArray: Lazy<CharArray>) {}
  @Inject internal fun lazyShortArray(lazyShortArray: Lazy<ShortArray>) {}
  @Inject internal fun lazyIntArray(lazyIntArray: Lazy<IntArray>) {}
  @Inject internal fun lazyLongArray(lazyLongArray: Lazy<LongArray>) {}
  @Inject internal fun lazyBooleanArray(lazyBooleanArray: Lazy<BooleanArray>) {}
  @Inject internal fun lazy(lazy: Lazy<FloatArray>) {}
  @Inject internal fun lazyThingProvider(lazyThingProvider: Provider<Lazy<Thing>>) {}
  @Inject internal fun lazyDoubleArray(lazyDoubleArray: Lazy<DoubleArray>) {}
  @Inject internal fun thing(thing: Thing) {}
  @Inject internal fun thingProvider(thingProvider: Provider<Thing>) {}
  @Inject internal fun lazyThing(lazyThing: Lazy<Thing>) {}
  @Inject internal fun thingMembersInjector(thingMembersInjector: MembersInjector<Thing>) {}
}
