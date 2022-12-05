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

import dagger.Module
import dagger.Provides

@Module
object PrimitivesModule {
  const val BOUND_BYTE: Byte = -41
  const val BOUND_CHAR = 'g'
  const val BOUND_SHORT: Short = 21840
  const val BOUND_INT = 1894833693
  const val BOUND_LONG = -4369839828653523584L
  const val BOUND_BOOLEAN = true
  const val BOUND_FLOAT = 0.9964542f
  const val BOUND_DOUBLE = 0.12681322049667765

  /*
   * While we can't ensure that these constants stay constant, this is a test so we're just going to
   * keep our fingers crossed that we're not going to be jerks.
   */
  val BOUND_BYTE_ARRAY = byteArrayOf(1, 2, 3)
  val BOUND_CHAR_ARRAY = charArrayOf('g', 'a', 'k')
  val BOUND_SHORT_ARRAY = shortArrayOf(2, 4)
  val BOUND_INT_ARRAY = intArrayOf(3, 1, 2)
  val BOUND_LONG_ARRAY = longArrayOf(1, 1, 2, 3, 5)
  val BOUND_BOOLEAN_ARRAY = booleanArrayOf(false, true, false, false)
  val BOUND_FLOAT_ARRAY = floatArrayOf(0.1f, 0.01f, 0.001f)
  val BOUND_DOUBLE_ARRAY = doubleArrayOf(0.2, 0.02, 0.002)

  @Provides fun provideByte(): Byte = BOUND_BYTE
  @Provides fun provideChar(): Char = BOUND_CHAR
  @Provides fun provideShort(): Short = BOUND_SHORT
  @Provides fun provideInt(): Int = BOUND_INT
  @Provides fun provideLong(): Long = BOUND_LONG
  @Provides fun provideBoolean(): Boolean = BOUND_BOOLEAN
  @Provides fun provideFloat(): Float = BOUND_FLOAT
  @Provides fun boundDouble(): Double = BOUND_DOUBLE
  @Provides fun provideByteArray(): ByteArray = BOUND_BYTE_ARRAY
  @Provides fun provideCharArray(): CharArray = BOUND_CHAR_ARRAY
  @Provides fun provideShortArray(): ShortArray = BOUND_SHORT_ARRAY
  @Provides fun provideIntArray(): IntArray = BOUND_INT_ARRAY
  @Provides fun provideLongArray(): LongArray = BOUND_LONG_ARRAY
  @Provides fun provideBooleanArray(): BooleanArray = BOUND_BOOLEAN_ARRAY
  @Provides fun provideFloatArray(): FloatArray = BOUND_FLOAT_ARRAY
  @Provides fun boundDoubleArray(): DoubleArray = BOUND_DOUBLE_ARRAY
}
