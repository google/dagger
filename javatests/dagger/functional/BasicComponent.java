/*
 * Copyright (C) 2014 The Dagger Authors.
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

import dagger.Component;
import dagger.Lazy;
import dagger.MembersInjector;
import dagger.functional.NullableModule.Nullable;
import javax.inject.Provider;

@Component(modules = {PrimitivesModule.class, NullableModule.class})
interface BasicComponent
    extends Injector<Thing>,
        // Implements two types that define the same method, not overridden here, to test that the
        // method is implemented only once.
        ComponentSupertypeOne,
        ComponentSupertypeTwo {
  byte getByte();
  char getChar();
  short getShort();
  int getInt();
  long getLong();
  boolean getBoolean();
  float getFloat();
  double getDouble();

  Byte getBoxedByte();
  Character getBoxedChar();
  Short getBoxedShort();
  Integer getBoxedInt();
  Long getBoxedLong();
  Boolean getBoxedBoolean();
  Float getBoxedFloat();
  Double getBoxedDouble();

  Provider<Byte> getByteProvider();
  Provider<Character> getCharProvider();
  Provider<Short> getShortProvider();
  Provider<Integer> getIntProvider();
  Provider<Long> getLongProvider();
  Provider<Boolean> getBooleanProvider();
  Provider<Float> getFloatProvider();
  Provider<Double> getDoubleProvider();

  byte[] getByteArray();
  char[] getCharArray();
  short[] getShortArray();
  int[] getIntArray();
  long[] getLongArray();
  boolean[] getBooleanArray();
  float[] getFloatArray();
  double[] getDoubleArray();

  Provider<byte[]> getByteArrayProvider();
  Provider<char[]> getCharArrayProvider();
  Provider<short[]> getShortArrayProvider();
  Provider<int[]> getIntArrayProvider();
  Provider<long[]> getLongArrayProvider();
  Provider<boolean[]> getBooleanArrayProvider();
  Provider<float[]> getFloatArrayProvider();
  Provider<double[]> getDoubleArrayProvider();

  Object noOpMembersInjection(Object obviouslyDoesNotHaveMembersToInject);

  Thing thing();
  InjectedThing injectedThing();
  Provider<InjectedThing> injectedThingProvider();
  Lazy<InjectedThing> lazyInjectedThing();
  Provider<Lazy<InjectedThing>> lazyInjectedThingProvider();
  MembersInjector<InjectedThing> injectedThingMembersInjector();
  
  @Nullable Object nullObject();
  Provider<Object> nullObjectProvider();
  Lazy<Object> lazyNullObject();

  TypeWithInheritedMembersInjection typeWithInheritedMembersInjection();
  MembersInjector<TypeWithInheritedMembersInjection>
      typeWithInheritedMembersInjectionMembersInjector();
}
