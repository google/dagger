/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional;

import dagger.Lazy;
import dagger.MembersInjector;
import javax.inject.Inject;
import javax.inject.Provider;

@SuppressWarnings("unused")
final class InjectedThing {
  @Inject byte primitiveByte;
  @Inject char primitiveChar;
  @Inject short primitiveShort;
  @Inject int primitiveInt;
  @Inject long primitiveLong;
  @Inject boolean primitiveBoolean;
  @Inject float primitiveFloat;
  @Inject double primitiveDouble;

  @Inject Provider<Byte> byteProvider;
  @Inject Provider<Character> charProvider;
  @Inject Provider<Short> shortProvider;
  @Inject Provider<Integer> intProvider;
  @Inject Provider<Long> longProvider;
  @Inject Provider<Boolean> booleanProvider;
  @Inject Provider<Float> floatProvider;
  @Inject Provider<Double> doubleProvider;

  @Inject Lazy<Byte> lazyByte;
  @Inject Lazy<Character> lazyChar;
  @Inject Lazy<Short> lazyShort;
  @Inject Lazy<Integer> lazyInt;
  @Inject Lazy<Long> lazyLong;
  @Inject Lazy<Boolean> lazyBoolean;
  @Inject Lazy<Float> lazyFloat;
  @Inject Lazy<Double> lazyDouble;

  @Inject Byte boxedBype;
  @Inject Character boxedChar;
  @Inject Short boxedShort;
  @Inject Integer boxedInt;
  @Inject Long boxedLong;
  @Inject Boolean boxedBoolean;
  @Inject Float boxedFloat;
  @Inject Double boxedDouble;

  @Inject byte[] byteArray;
  @Inject char[] charArray;
  @Inject short[] shortArray;
  @Inject int[] intArray;
  @Inject long[] longArray;
  @Inject boolean[] booleanArray;
  @Inject float[] floatArray;
  @Inject double[] doubleArray;

  @Inject Provider<byte[]> byteArrayProvider;
  @Inject Provider<char[]> charArrayProvider;
  @Inject Provider<short[]> shortArrayProvider;
  @Inject Provider<int[]> intArrayProvider;
  @Inject Provider<long[]> longArrayProvider;
  @Inject Provider<boolean[]> booleanArrayProvider;
  @Inject Provider<float[]> floatArrayProvider;
  @Inject Provider<double[]> doubleArrayProvider;

  @Inject Lazy<byte[]> lazyByteArray;
  @Inject Lazy<char[]> lazyCharArray;
  @Inject Lazy<short[]> lazyShortArray;
  @Inject Lazy<int[]> lazyIntArray;
  @Inject Lazy<long[]> lazyLongArray;
  @Inject Lazy<boolean[]> lazyBooleanArray;
  @Inject Lazy<float[]> lazy;
  @Inject Lazy<double[]> lazyDoubleArray;

  @Inject Thing thing;
  @Inject Provider<Thing> thingProvider;
  @Inject Lazy<Thing> lazyThing;
  @Inject Provider<Lazy<Thing>> lazyThingProvider;
  @Inject MembersInjector<Thing> thingMembersInjector;

  @Inject InjectedThing(
      byte primitiveByte,
      char primitiveChar,
      short primitiveShort,
      int primitiveInt,
      long primitiveLong,
      boolean primitiveBoolean,
      float primitiveFloat,
      double primitiveDouble,

      Provider<Byte> byteProvider,
      Provider<Character> charProvider,
      Provider<Short> shortProvider,
      Provider<Integer> intProvider,
      Provider<Long> longProvider,
      Provider<Boolean> booleanProvider,
      Provider<Float> floatProvider,
      Provider<Double> doubleProvider,

      Lazy<Byte> lazyByte,
      Lazy<Character> lazyChar,
      Lazy<Short> lazyShort,
      Lazy<Integer> lazyInt,
      Lazy<Long> lazyLong,
      Lazy<Boolean> lazyBoolean,
      Lazy<Float> lazyFloat,
      Lazy<Double> lazyDouble,

      Byte boxedBype,
      Character boxedChar,
      Short boxedShort,
      Integer boxedInt,
      Long boxedLong,
      Boolean boxedBoolean,
      Float boxedFloat,
      Double boxedDouble,

      byte[] byteArray,
      char[] charArray,
      short[] shortArray,
      int[] intArray,
      long[] longArray,
      boolean[] booleanArray,
      float[] floatArray,
      double[] doubleArray,

      Provider<byte[]> byteArrayProvider,
      Provider<char[]> charArrayProvider,
      Provider<short[]> shortArrayProvider,
      Provider<int[]> intArrayProvider,
      Provider<long[]> longArrayProvider,
      Provider<boolean[]> booleanArrayProvider,
      Provider<float[]> floatArrayProvider,
      Provider<double[]> doubleArrayProvider,

      Lazy<byte[]> lazyByteArray,
      Lazy<char[]> lazyCharArray,
      Lazy<short[]> lazyShortArray,
      Lazy<int[]> lazyIntArray,
      Lazy<long[]> lazyLongArray,
      Lazy<boolean[]> lazyBooleanArray,
      Lazy<float[]> lazy,
      Lazy<double[]> lazyDoubleArray,

      Thing thing,
      Provider<Thing> thingProvider,
      Lazy<Thing> lazyThing,
      Provider<Lazy<Thing>> lazyThingProvider,
      MembersInjector<Thing> thingMembersInjector) {}

  @Inject void primitiveByte(byte primitiveByte) {}
  @Inject void primitiveChar(char primitiveChar) {}
  @Inject void primitiveShort(short primitiveShort) {}
  @Inject void primitiveInt(int primitiveInt) {}
  @Inject void primitiveLong(long primitiveLong) {}
  @Inject void primitiveBoolean(boolean primitiveBoolean) {}
  @Inject void primitiveFloat(float primitiveFloat) {}
  @Inject void primitiveDouble(double primitiveDouble) {}

  @Inject void byteProvider(Provider<Byte> byteProvider) {}
  @Inject void charProvider(Provider<Character> charProvider) {}
  @Inject void shortProvider(Provider<Short> shortProvider) {}
  @Inject void intProvider(Provider<Integer> intProvider) {}
  @Inject void longProvider(Provider<Long> longProvider) {}
  @Inject void booleanProvider(Provider<Boolean> booleanProvider) {}
  @Inject void floatProvider(Provider<Float> floatProvider) {}
  @Inject void doubleProvider(Provider<Double> doubleProvider) {}

  @Inject void lazyByte(Lazy<Byte> lazyByte) {}
  @Inject void lazyChar(Lazy<Character> lazyChar) {}
  @Inject void lazyShort(Lazy<Short> lazyShort) {}
  @Inject void lazyInt(Lazy<Integer> lazyInt) {}
  @Inject void lazyLong(Lazy<Long> lazyLong) {}
  @Inject void lazyBoolean(Lazy<Boolean> lazyBoolean) {}
  @Inject void lazyFloat(Lazy<Float> lazyFloat) {}
  @Inject void lazyDouble(Lazy<Double> lazyDouble) {}

  @Inject void boxedBype(Byte boxedBype) {}
  @Inject void boxedChar(Character boxedChar) {}
  @Inject void boxedShort(Short boxedShort) {}
  @Inject void boxedInt(Integer boxedInt) {}
  @Inject void boxedLong(Long boxedLong) {}
  @Inject void boxedBoolean(Boolean boxedBoolean) {}
  @Inject void boxedFloat(Float boxedFloat) {}
  @Inject void boxedDouble(Double boxedDouble) {}

  @Inject void byteArray(byte[] byteArray) {}
  @Inject void charArray(char[] charArray) {}
  @Inject void shortArray(short[] shortArray) {}
  @Inject void intArray(int[] intArray) {}
  @Inject void longArray(long[] longArray) {}
  @Inject void booleanArray(boolean[] booleanArray) {}
  @Inject void floatArray(float[] floatArray) {}
  @Inject void doubleArray(double[] doubleArray) {}

  @Inject void byteArrayProvider(Provider<byte[]> byteArrayProvider) {}
  @Inject void charArrayProvider(Provider<char[]> charArrayProvider) {}
  @Inject void shortArrayProvider(Provider<short[]> shortArrayProvider) {}
  @Inject void intArrayProvider(Provider<int[]> intArrayProvider) {}
  @Inject void longArrayProvider(Provider<long[]> longArrayProvider) {}
  @Inject void booleanArrayProvider(Provider<boolean[]> booleanArrayProvider) {}
  @Inject void floatArrayProvider(Provider<float[]> floatArrayProvider) {}
  @Inject void doubleArrayProvider(Provider<double[]> doubleArrayProvider) {}

  @Inject void lazyByteArray(Lazy<byte[]> lazyByteArray) {}
  @Inject void lazyCharArray(Lazy<char[]> lazyCharArray) {}
  @Inject void lazyShortArray(Lazy<short[]> lazyShortArray) {}
  @Inject void lazyIntArray(Lazy<int[]> lazyIntArray) {}
  @Inject void lazyLongArray(Lazy<long[]> lazyLongArray) {}
  @Inject void lazyBooleanArray(Lazy<boolean[]> lazyBooleanArray) {}
  @Inject void lazy(Lazy<float[]> lazy) {}
  @Inject void lazyThingProvider(Provider<Lazy<Thing>> lazyThingProvider) {}
  @Inject void lazyDoubleArray(Lazy<double[]> lazyDoubleArray) {}

  @Inject void thing(Thing thing) {}
  @Inject void thingProvider(Provider<Thing> thingProvider) {}
  @Inject void lazyThing(Lazy<Thing> lazyThing) {}
  @Inject void thingMembersInjector(MembersInjector<Thing> thingMembersInjector) {}
}
