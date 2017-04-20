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

import static com.google.common.truth.Truth.assertThat;
import static dagger.functional.PrimitivesModule.BOUND_BOOLEAN;
import static dagger.functional.PrimitivesModule.BOUND_BOOLEAN_ARRAY;
import static dagger.functional.PrimitivesModule.BOUND_BYTE;
import static dagger.functional.PrimitivesModule.BOUND_BYTE_ARRAY;
import static dagger.functional.PrimitivesModule.BOUND_CHAR;
import static dagger.functional.PrimitivesModule.BOUND_CHAR_ARRAY;
import static dagger.functional.PrimitivesModule.BOUND_DOUBLE;
import static dagger.functional.PrimitivesModule.BOUND_DOUBLE_ARRAY;
import static dagger.functional.PrimitivesModule.BOUND_FLOAT;
import static dagger.functional.PrimitivesModule.BOUND_FLOAT_ARRAY;
import static dagger.functional.PrimitivesModule.BOUND_INT;
import static dagger.functional.PrimitivesModule.BOUND_INT_ARRAY;
import static dagger.functional.PrimitivesModule.BOUND_LONG;
import static dagger.functional.PrimitivesModule.BOUND_LONG_ARRAY;
import static dagger.functional.PrimitivesModule.BOUND_SHORT;
import static dagger.functional.PrimitivesModule.BOUND_SHORT_ARRAY;

import dagger.Lazy;
import javax.inject.Provider;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class BasicTest {
  @DataPoint
  public static final BasicComponent basicComponent = DaggerBasicComponent.create();
  @DataPoint
  public static final BasicComponent abstractClassBasicComponent =
      DaggerBasicAbstractClassComponent.create();

  @Theory public void primitives(BasicComponent basicComponent) {
    assertThat(basicComponent.getByte()).isEqualTo(BOUND_BYTE);
    assertThat(basicComponent.getChar()).isEqualTo(BOUND_CHAR);
    assertThat(basicComponent.getShort()).isEqualTo(BOUND_SHORT);
    assertThat(basicComponent.getInt()).isEqualTo(BOUND_INT);
    assertThat(basicComponent.getLong()).isEqualTo(BOUND_LONG);
    assertThat(basicComponent.getBoolean()).isEqualTo(BOUND_BOOLEAN);
    assertThat(basicComponent.getFloat()).isWithin(0).of(BOUND_FLOAT);
    assertThat(basicComponent.getDouble()).isWithin(0).of(BOUND_DOUBLE);
  }

  @Theory public void boxedPrimitives(BasicComponent basicComponent) {
    assertThat(basicComponent.getBoxedByte()).isEqualTo(new Byte(BOUND_BYTE));
    assertThat(basicComponent.getBoxedChar()).isEqualTo(new Character(BOUND_CHAR));
    assertThat(basicComponent.getBoxedShort()).isEqualTo(new Short(BOUND_SHORT));
    assertThat(basicComponent.getBoxedInt()).isEqualTo(new Integer(BOUND_INT));
    assertThat(basicComponent.getBoxedLong()).isEqualTo(new Long(BOUND_LONG));
    assertThat(basicComponent.getBoxedBoolean()).isEqualTo(new Boolean(BOUND_BOOLEAN));
    assertThat(basicComponent.getBoxedFloat()).isWithin(0).of(BOUND_FLOAT);
    assertThat(basicComponent.getBoxedDouble()).isWithin(0).of(BOUND_DOUBLE);
  }

  @Theory public void boxedPrimitiveProviders(BasicComponent basicComponent) {
    assertThat(basicComponent.getByteProvider().get()).isEqualTo(new Byte(BOUND_BYTE));
    assertThat(basicComponent.getCharProvider().get()).isEqualTo(new Character(BOUND_CHAR));
    assertThat(basicComponent.getShortProvider().get()).isEqualTo(new Short(BOUND_SHORT));
    assertThat(basicComponent.getIntProvider().get()).isEqualTo(new Integer(BOUND_INT));
    assertThat(basicComponent.getLongProvider().get()).isEqualTo(new Long(BOUND_LONG));
    assertThat(basicComponent.getBooleanProvider().get()).isEqualTo(new Boolean(BOUND_BOOLEAN));
    assertThat(basicComponent.getFloatProvider().get()).isWithin(0).of(BOUND_FLOAT);
    assertThat(basicComponent.getDoubleProvider().get()).isWithin(0).of(BOUND_DOUBLE);
  }

  @Theory public void primitiveArrays(BasicComponent basicComponent) {
    assertThat(basicComponent.getByteArray()).isSameAs(BOUND_BYTE_ARRAY);
    assertThat(basicComponent.getCharArray()).isSameAs(BOUND_CHAR_ARRAY);
    assertThat(basicComponent.getShortArray()).isSameAs(BOUND_SHORT_ARRAY);
    assertThat(basicComponent.getIntArray()).isSameAs(BOUND_INT_ARRAY);
    assertThat(basicComponent.getLongArray()).isSameAs(BOUND_LONG_ARRAY);
    assertThat(basicComponent.getBooleanArray()).isSameAs(BOUND_BOOLEAN_ARRAY);
    assertThat(basicComponent.getFloatArray()).isSameAs(BOUND_FLOAT_ARRAY);
    assertThat(basicComponent.getDoubleArray()).isSameAs(BOUND_DOUBLE_ARRAY);
  }

  @Theory public void primitiveArrayProviders(BasicComponent basicComponent) {
    assertThat(basicComponent.getByteArrayProvider().get()).isSameAs(BOUND_BYTE_ARRAY);
    assertThat(basicComponent.getCharArrayProvider().get()).isSameAs(BOUND_CHAR_ARRAY);
    assertThat(basicComponent.getShortArrayProvider().get()).isSameAs(BOUND_SHORT_ARRAY);
    assertThat(basicComponent.getIntArrayProvider().get()).isSameAs(BOUND_INT_ARRAY);
    assertThat(basicComponent.getLongArrayProvider().get()).isSameAs(BOUND_LONG_ARRAY);
    assertThat(basicComponent.getBooleanArrayProvider().get()).isSameAs(BOUND_BOOLEAN_ARRAY);
    assertThat(basicComponent.getFloatArrayProvider().get()).isSameAs(BOUND_FLOAT_ARRAY);
    assertThat(basicComponent.getDoubleArrayProvider().get()).isSameAs(BOUND_DOUBLE_ARRAY);
  }

  @Theory public void noOpMembersInjection(BasicComponent basicComponent) {
    Object object = new Object();
    assertThat(basicComponent.noOpMembersInjection(object)).isSameAs(object);
  }

  @Theory public void basicObject_noDeps(BasicComponent basicComponent) {
    assertThat(basicComponent.thing()).isNotNull();
  }

  @Theory public void inheritedMembersInjection(BasicComponent basicComponent) {
    assertThat(basicComponent.typeWithInheritedMembersInjection().thing).isNotNull();
  }
  
  @Theory
  public void nullableInjection(BasicComponent basicComponent) {
    assertThat(basicComponent.nullObject()).isNull();
    assertThat(basicComponent.nullObjectProvider().get()).isNull();
    assertThat(basicComponent.lazyNullObject().get()).isNull();
  }
  
  @Theory
  public void providerOfLazy(BasicComponent basicComponent) {
    Provider<Lazy<InjectedThing>> lazyInjectedThingProvider =
        basicComponent.lazyInjectedThingProvider();
    Lazy<InjectedThing> lazyInjectedThing1 = lazyInjectedThingProvider.get();
    Lazy<InjectedThing> lazyInjectedThing2 = lazyInjectedThingProvider.get();
    assertThat(lazyInjectedThing2).isNotSameAs(lazyInjectedThing1);
    assertThat(lazyInjectedThing1.get()).isSameAs(lazyInjectedThing1.get());
    assertThat(lazyInjectedThing2.get()).isSameAs(lazyInjectedThing2.get());
    assertThat(lazyInjectedThing2.get()).isNotSameAs(lazyInjectedThing1.get());
  }
}
