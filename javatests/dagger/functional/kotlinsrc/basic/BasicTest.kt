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

import com.google.common.truth.Truth.assertThat
import org.junit.experimental.theories.DataPoint
import org.junit.experimental.theories.Theories
import org.junit.experimental.theories.Theory
import org.junit.runner.RunWith

@RunWith(Theories::class)
class BasicTest {
  @Theory
  fun primitives(basicComponent: BasicComponent) {
    assertThat(basicComponent.byteFun()).isEqualTo(PrimitivesModule.BOUND_BYTE)
    assertThat(basicComponent.charFun()).isEqualTo(PrimitivesModule.BOUND_CHAR)
    assertThat(basicComponent.shortFun()).isEqualTo(PrimitivesModule.BOUND_SHORT)
    assertThat(basicComponent.intFun()).isEqualTo(PrimitivesModule.BOUND_INT)
    assertThat(basicComponent.longFun()).isEqualTo(PrimitivesModule.BOUND_LONG)
    assertThat(basicComponent.booleanFun()).isEqualTo(PrimitivesModule.BOUND_BOOLEAN)
    assertThat(basicComponent.floatFun()).isEqualTo(PrimitivesModule.BOUND_FLOAT)
    assertThat(basicComponent.doubleFun()).isEqualTo(PrimitivesModule.BOUND_DOUBLE)

    assertThat(basicComponent.byteVal).isEqualTo(basicComponent.byteFun())
    assertThat(basicComponent.charVal).isEqualTo(basicComponent.charFun())
    assertThat(basicComponent.shortVal).isEqualTo(basicComponent.shortFun())
    assertThat(basicComponent.intVal).isEqualTo(basicComponent.intFun())
    assertThat(basicComponent.longVal).isEqualTo(basicComponent.longFun())
    assertThat(basicComponent.booleanVal).isEqualTo(basicComponent.booleanFun())
    assertThat(basicComponent.floatVal).isEqualTo(basicComponent.floatFun())
    assertThat(basicComponent.doubleVal).isEqualTo(basicComponent.doubleFun())
  }

  @Theory
  fun primitiveProviders(basicComponent: BasicComponent) {
    assertThat(basicComponent.byteProviderFun().get()).isEqualTo(PrimitivesModule.BOUND_BYTE)
    assertThat(basicComponent.charProviderFun().get()).isEqualTo(PrimitivesModule.BOUND_CHAR)
    assertThat(basicComponent.shortProviderFun().get()).isEqualTo(PrimitivesModule.BOUND_SHORT)
    assertThat(basicComponent.intProviderFun().get()).isEqualTo(PrimitivesModule.BOUND_INT)
    assertThat(basicComponent.longProviderFun().get()).isEqualTo(PrimitivesModule.BOUND_LONG)
    assertThat(basicComponent.booleanProviderFun().get()).isEqualTo(PrimitivesModule.BOUND_BOOLEAN)
    assertThat(basicComponent.floatProviderFun().get()).isEqualTo(PrimitivesModule.BOUND_FLOAT)
    assertThat(basicComponent.doubleProviderFun().get()).isEqualTo(PrimitivesModule.BOUND_DOUBLE)

    assertThat(basicComponent.byteProviderVal.get())
        .isEqualTo(basicComponent.byteProviderFun().get())
    assertThat(basicComponent.charProviderVal.get())
        .isEqualTo(basicComponent.charProviderFun().get())
    assertThat(basicComponent.shortProviderVal.get())
        .isEqualTo(basicComponent.shortProviderFun().get())
    assertThat(basicComponent.intProviderVal.get())
        .isEqualTo(basicComponent.intProviderFun().get())
    assertThat(basicComponent.longProviderVal.get())
        .isEqualTo(basicComponent.longProviderFun().get())
    assertThat(basicComponent.booleanProviderVal.get())
        .isEqualTo(basicComponent.booleanProviderFun().get())
    assertThat(basicComponent.floatProviderVal.get())
        .isEqualTo(basicComponent.floatProviderFun().get())
    assertThat(basicComponent.doubleProviderVal.get())
        .isEqualTo(basicComponent.doubleProviderFun().get())
  }

  @Theory
  fun primitiveArrays(basicComponent: BasicComponent) {
    assertThat(basicComponent.byteArrayFun()).isSameInstanceAs(PrimitivesModule.BOUND_BYTE_ARRAY)
    assertThat(basicComponent.charArrayFun()).isSameInstanceAs(PrimitivesModule.BOUND_CHAR_ARRAY)
    assertThat(basicComponent.shortArrayFun()).isSameInstanceAs(PrimitivesModule.BOUND_SHORT_ARRAY)
    assertThat(basicComponent.intArrayFun()).isSameInstanceAs(PrimitivesModule.BOUND_INT_ARRAY)
    assertThat(basicComponent.longArrayFun()).isSameInstanceAs(PrimitivesModule.BOUND_LONG_ARRAY)
    assertThat(basicComponent.booleanArrayFun())
        .isSameInstanceAs(PrimitivesModule.BOUND_BOOLEAN_ARRAY)
    assertThat(basicComponent.floatArrayFun()).isSameInstanceAs(PrimitivesModule.BOUND_FLOAT_ARRAY)
    assertThat(basicComponent.doubleArrayFun())
        .isSameInstanceAs(PrimitivesModule.BOUND_DOUBLE_ARRAY)

    assertThat(basicComponent.byteArrayVal).isSameInstanceAs(basicComponent.byteArrayFun())
    assertThat(basicComponent.charArrayVal).isSameInstanceAs(basicComponent.charArrayFun())
    assertThat(basicComponent.shortArrayVal).isSameInstanceAs(basicComponent.shortArrayFun())
    assertThat(basicComponent.intArrayVal).isSameInstanceAs(basicComponent.intArrayFun())
    assertThat(basicComponent.longArrayVal).isSameInstanceAs(basicComponent.longArrayFun())
    assertThat(basicComponent.booleanArrayVal).isSameInstanceAs(basicComponent.booleanArrayFun())
    assertThat(basicComponent.floatArrayVal).isSameInstanceAs(basicComponent.floatArrayFun())
    assertThat(basicComponent.doubleArrayVal).isSameInstanceAs(basicComponent.doubleArrayFun())
  }

  @Theory
  fun primitiveArrayProviders(basicComponent: BasicComponent) {
    assertThat(basicComponent.byteArrayProviderFun().get())
      .isSameInstanceAs(PrimitivesModule.BOUND_BYTE_ARRAY)
    assertThat(basicComponent.charArrayProviderFun().get())
      .isSameInstanceAs(PrimitivesModule.BOUND_CHAR_ARRAY)
    assertThat(basicComponent.shortArrayProviderFun().get())
      .isSameInstanceAs(PrimitivesModule.BOUND_SHORT_ARRAY)
    assertThat(basicComponent.intArrayProviderFun().get())
      .isSameInstanceAs(PrimitivesModule.BOUND_INT_ARRAY)
    assertThat(basicComponent.longArrayProviderFun().get())
      .isSameInstanceAs(PrimitivesModule.BOUND_LONG_ARRAY)
    assertThat(basicComponent.booleanArrayProviderFun().get())
      .isSameInstanceAs(PrimitivesModule.BOUND_BOOLEAN_ARRAY)
    assertThat(basicComponent.floatArrayProviderFun().get())
      .isSameInstanceAs(PrimitivesModule.BOUND_FLOAT_ARRAY)
    assertThat(basicComponent.doubleArrayProviderFun().get())
      .isSameInstanceAs(PrimitivesModule.BOUND_DOUBLE_ARRAY)

    assertThat(basicComponent.byteArrayProviderVal.get())
      .isSameInstanceAs(basicComponent.byteArrayProviderFun().get())
    assertThat(basicComponent.charArrayProviderVal.get())
      .isSameInstanceAs(basicComponent.charArrayProviderFun().get())
    assertThat(basicComponent.shortArrayProviderVal.get())
      .isSameInstanceAs(basicComponent.shortArrayProviderFun().get())
    assertThat(basicComponent.intArrayProviderVal.get())
      .isSameInstanceAs(basicComponent.intArrayProviderFun().get())
    assertThat(basicComponent.longArrayProviderVal.get())
      .isSameInstanceAs(basicComponent.longArrayProviderFun().get())
    assertThat(basicComponent.booleanArrayProviderVal.get())
      .isSameInstanceAs(basicComponent.booleanArrayProviderFun().get())
    assertThat(basicComponent.floatArrayProviderVal.get())
      .isSameInstanceAs(basicComponent.floatArrayProviderFun().get())
    assertThat(basicComponent.doubleArrayProviderVal.get())
      .isSameInstanceAs(basicComponent.doubleArrayProviderFun().get())
  }

  @Theory
  fun noOpMembersInjection(basicComponent: BasicComponent) {
    val someObject = Any()
    assertThat(basicComponent.noOpMembersInjectionFun(someObject)).isSameInstanceAs(someObject)
  }

  @Theory
  fun basicObject_noDeps(basicComponent: BasicComponent) {
    assertThat(basicComponent.thingFun()).isNotNull()
    assertThat(basicComponent.thingVal).isNotNull()
  }

  @Theory
  fun inheritedMembersInjection(basicComponent: BasicComponent) {
    assertThat(basicComponent.typeWithInheritedMembersInjectionFun().thing).isNotNull()

    assertThat(basicComponent.typeWithInheritedMembersInjectionVal.thing).isNotNull()
  }

  @Theory
  fun nullableInjection(basicComponent: BasicComponent) {
    assertThat(basicComponent.nullObjectFun()).isNull()
    assertThat(basicComponent.nullObjectProviderFun().get()).isNull()
    assertThat(basicComponent.lazyNullObjectFun().get()).isNull()

    assertThat(basicComponent.nullObjectVal).isNull()
    assertThat(basicComponent.nullObjectProviderVal.get()).isNull()
    assertThat(basicComponent.lazyNullObjectVal.get()).isNull()
  }

  @Theory
  fun providerOfLazy(basicComponent: BasicComponent) {
    val lazyInjectedThingProvider = basicComponent.lazyInjectedThingProviderFun()
    val lazyInjectedThing1 = lazyInjectedThingProvider.get()
    val lazyInjectedThing2 = lazyInjectedThingProvider.get()
    assertThat(lazyInjectedThing2).isNotSameInstanceAs(lazyInjectedThing1)
    assertThat(lazyInjectedThing1.get()).isSameInstanceAs(lazyInjectedThing1.get())
    assertThat(lazyInjectedThing2.get()).isSameInstanceAs(lazyInjectedThing2.get())
    assertThat(lazyInjectedThing2.get()).isNotSameInstanceAs(lazyInjectedThing1.get())
  }

  companion object {
    @JvmField
    @DataPoint
    val basicComponent = DaggerBasicComponent.create()

    @JvmField
    @DataPoint
    val abstractClassBasicComponent = DaggerBasicAbstractClassComponent.create()
  }
}
