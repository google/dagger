/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.functional.kotlinsrc.multibindings

import com.google.auto.value.AutoAnnotation
import com.google.common.truth.Truth.assertThat
import dagger.Component
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.functional.kotlinsrc.multibindings.NestedAnnotationContainer.NestedWrappedKey
import dagger.functional.kotlinsrc.multibindings.subpackage.ContributionsModule
import dagger.multibindings.ClassKey
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import dagger.multibindings.LongKey
import dagger.multibindings.Multibinds
import dagger.multibindings.StringKey
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Named
import javax.inject.Provider
import kotlin.reflect.KClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [MultibindingComponent]. */
@RunWith(JUnit4::class)
class MultibindingTest {
  private val multibindingComponent =
    DaggerMultibindingComponent.builder().multibindingDependency { 0.0 }.build()

  @Test
  fun map() {
    val map = multibindingComponent.map()
    assertThat(map).hasSize(2)
    assertThat(map).containsEntry("foo", "foo value")
    assertThat(map).containsEntry("bar", "bar value")
  }

  @Test
  fun mapOfArrays() {
    val map = multibindingComponent.mapOfArrays()
    assertThat(map).hasSize(2)
    assertThat(map).containsKey("foo")
    assertThat(map["foo"]).asList().containsExactly("foo1", "foo2").inOrder()
    assertThat(map).containsKey("bar")
    assertThat(map["bar"]).asList().containsExactly("bar1", "bar2").inOrder()
  }

  @Test
  fun mapOfProviders() {
    val mapOfProviders = multibindingComponent.mapOfProviders()
    assertThat(mapOfProviders).hasSize(2)
    assertThat(mapOfProviders["foo"]!!.get()).isEqualTo("foo value")
    assertThat(mapOfProviders["bar"]!!.get()).isEqualTo("bar value")
  }

  @Test
  fun mapKeysAndValues() {
    assertThat(multibindingComponent.mapKeys()).containsExactly("foo", "bar")
    assertThat(multibindingComponent.mapValues()).containsExactly("foo value", "bar value")
  }

  @Test
  fun nestedKeyMap() {
    assertThat(multibindingComponent.nestedKeyMap())
      .containsExactly(
        AutoAnnotationHolder.nestedWrappedKey(java.lang.Integer::class.java),
        "integer",
        AutoAnnotationHolder.nestedWrappedKey(java.lang.Long::class.java),
        "long"
      )
  }

  @Test
  fun unwrappedAnnotationKeyMap() {
    assertThat(multibindingComponent.unwrappedAnnotationKeyMap())
      .containsExactly(AutoAnnotationHolder.testStringKey("foo\n"), "foo annotation")
  }

  @Test
  fun wrappedAnnotationKeyMap() {
    assertThat(multibindingComponent.wrappedAnnotationKeyMap())
      .containsExactly(
        AutoAnnotationHolder.testWrappedAnnotationKey(
          AutoAnnotationHolder.testStringKey("foo"),
          intArrayOf(1, 2, 3),
          arrayOf(),
          arrayOf(java.lang.Long::class.java, java.lang.Integer::class.java)
        ),
        "wrapped foo annotation"
      )
  }

  @Test
  fun booleanKeyMap() {
    assertThat(multibindingComponent.booleanKeyMap()).containsExactly(true, "true")
  }

  @Test
  fun byteKeyMap() {
    assertThat(multibindingComponent.byteKeyMap()).containsExactly(100.toByte(), "100 byte")
  }

  @Test
  fun charKeyMap() {
    assertThat(multibindingComponent.characterKeyMap())
      .containsExactly('a', "a char", '\n', "newline char")
  }

  @Test
  fun classKeyMap() {
    assertThat(multibindingComponent.classKeyMap())
      .containsExactly(java.lang.Integer::class.java, "integer", java.lang.Long::class.java, "long")
  }

  @Test
  fun numberClassKeyMap() {
    assertThat(multibindingComponent.numberClassKeyMap())
      .containsExactly(BigDecimal::class.java, "bigdecimal", BigInteger::class.java, "biginteger")
  }

  @Test
  fun intKeyMap() {
    assertThat(multibindingComponent.integerKeyMap()).containsExactly(100, "100 int")
  }

  @Test
  fun longKeyMap() {
    assertThat(multibindingComponent.longKeyMap()).containsExactly(100.toLong(), "100 long")
  }

  @Test
  fun shortKeyMap() {
    assertThat(multibindingComponent.shortKeyMap()).containsExactly(100.toShort(), "100 short")
  }

  @Test
  fun setBindings() {
    assertThat(multibindingComponent.set())
      .containsExactly(-90, -17, -1, 5, 6, 832, 1742, -101, -102)
  }

  @Test
  fun complexQualifierSet() {
    assertThat(multibindingComponent.complexQualifierStringSet()).containsExactly("foo")
  }

  @Test
  fun emptySet() {
    assertThat(multibindingComponent.emptySet()).isEmpty()
  }

  @Test
  fun emptyQualifiedSet() {
    assertThat(multibindingComponent.emptyQualifiedSet()).isEmpty()
  }

  @Test
  fun emptyMap() {
    assertThat(multibindingComponent.emptyMap()).isEmpty()
  }

  @Test
  fun emptyQualifiedMap() {
    assertThat(multibindingComponent.emptyQualifiedMap()).isEmpty()
  }

  @Test
  fun maybeEmptySet() {
    assertThat(multibindingComponent.maybeEmptySet()).containsExactly("foo")
  }

  @Test
  fun maybeEmptyQualifiedSet() {
    assertThat(multibindingComponent.maybeEmptyQualifiedSet()).containsExactly("qualified foo")
  }

  @Test
  fun maybeEmptyMap() {
    assertThat(multibindingComponent.maybeEmptyMap()).containsEntry("key", "foo value")
  }

  @Test
  fun maybeEmptyQualifiedMap() {
    assertThat(multibindingComponent.maybeEmptyQualifiedMap())
      .containsEntry("key", "qualified foo value")
  }

  // Note: @AutoAnnotation requires a static method. Normally, we would just use a companion object
  // but that generates both a static and non-static method so we need to use a normal object.
  internal object AutoAnnotationHolder {
    @JvmStatic
    @AutoAnnotation
    fun testStringKey(value: String): StringKey {
      return AutoAnnotation_MultibindingTest_AutoAnnotationHolder_testStringKey(value)
    }

    @JvmStatic
    @AutoAnnotation
    fun nestedWrappedKey(value: Class<*>): NestedWrappedKey {
      return AutoAnnotation_MultibindingTest_AutoAnnotationHolder_nestedWrappedKey(value)
    }

    @JvmStatic
    @AutoAnnotation
    fun testWrappedAnnotationKey(
      value: StringKey,
      integers: IntArray,
      annotations: Array<ClassKey>,
      classes: Array<Class<out Number>>
    ): WrappedAnnotationKey {
      return AutoAnnotation_MultibindingTest_AutoAnnotationHolder_testWrappedAnnotationKey(
        value,
        integers,
        annotations,
        classes
      )
    }
  }
}

@MapKey(unwrapValue = true) internal annotation class BooleanKey(val value: Boolean)

@MapKey(unwrapValue = true) internal annotation class ByteKey(val value: Byte)

@MapKey(unwrapValue = true) internal annotation class CharKey(val value: Char)

@MapKey(unwrapValue = true) internal annotation class NumberClassKey(val value: KClass<out Number>)

@MapKey(unwrapValue = true) internal annotation class ShortKey(val value: Short)

@MapKey(unwrapValue = true) internal annotation class UnwrappedAnnotationKey(val value: StringKey)

class NestedAnnotationContainer {
  @MapKey(unwrapValue = false) annotation class NestedWrappedKey(val value: KClass<*>)
}

@MapKey(unwrapValue = false)
annotation class WrappedAnnotationKey(
  val value: StringKey,
  val integers: IntArray,
  val annotations: Array<ClassKey>,
  val classes: Array<KClass<out Number>>
)

@Component(
  modules = [MultibindingModule::class, MultibindsModule::class, ContributionsModule::class],
  dependencies = [MultibindingDependency::class]
)
internal interface MultibindingComponent {
  fun map(): Map<String, String>
  fun mapOfArrays(): Map<String, Array<String>>
  fun mapOfProviders(): Map<String, Provider<String>>
  fun mapKeys(): Set<String>
  fun mapValues(): Collection<String>
  fun set(): Set<Int>
  fun nestedKeyMap(): Map<NestedAnnotationContainer.NestedWrappedKey, String>
  fun numberClassKeyMap(): Map<Class<out Number>, String>
  fun classKeyMap(): Map<Class<*>, String>
  fun longKeyMap(): Map<Long, String>
  fun integerKeyMap(): Map<Int, String>
  fun shortKeyMap(): Map<Short, String>
  fun byteKeyMap(): Map<Byte, String>
  fun booleanKeyMap(): Map<Boolean, String>
  fun characterKeyMap(): Map<Char, String>
  fun unwrappedAnnotationKeyMap(): Map<StringKey, String>
  fun wrappedAnnotationKeyMap(): Map<WrappedAnnotationKey, String>

  @Named("complexQualifier") fun complexQualifierStringSet(): Set<String>
  fun emptySet(): Set<Any>

  @Named("complexQualifier") fun emptyQualifiedSet(): Set<Any>
  fun emptyMap(): Map<String, Any>

  @Named("complexQualifier") fun emptyQualifiedMap(): Map<String, Any>
  fun maybeEmptySet(): Set<CharSequence>

  @Named("complexQualifier") fun maybeEmptyQualifiedSet(): Set<CharSequence>
  fun maybeEmptyMap(): Map<String, CharSequence>

  @Named("complexQualifier") fun maybeEmptyQualifiedMap(): Map<String, CharSequence>
}

internal fun interface MultibindingDependency {
  fun doubleDependency(): Double
}

@Module
internal object MultibindingModule {
  @Provides
  @IntoMap
  @StringKey("foo")
  fun provideFooKey(@Suppress("UNUSED_PARAMETER") doubleDependency: Double): String = "foo value"

  @Provides @IntoMap @StringKey("bar") fun provideBarKey(): String = "bar value"

  @Provides
  @IntoMap
  @StringKey("foo")
  fun provideFooArrayValue(
    @Suppress("UNUSED_PARAMETER") doubleDependency: Double
  ): Array<String> = arrayOf("foo1", "foo2")

  @Provides
  @IntoMap
  @StringKey("bar")
  fun provideBarArrayValue(): Array<String> = arrayOf("bar1", "bar2")

  @Provides @IntoSet fun provideFiveToSet(): Int = 5

  @Provides @IntoSet fun provideSixToSet(): Int = 6

  @Provides @ElementsIntoSet fun provideElementsIntoSet(): Set<Int> = setOf(-101, -102)

  @Provides
  fun provideMapKeys(map: Map<String, @JvmSuppressWildcards Provider<String>>): Set<String> =
    map.keys

  @Provides fun provideMapValues(map: Map<String, String>): Collection<String> = map.values

  @Provides
  @IntoMap
  @NestedWrappedKey(java.lang.Integer::class)
  fun valueForInteger(): String = "integer"

  @Provides @IntoMap @NestedWrappedKey(java.lang.Long::class) fun valueForLong(): String = "long"

  @Provides
  @IntoMap
  @ClassKey(java.lang.Integer::class)
  fun valueForClassInteger(): String = "integer"

  @Provides @IntoMap @ClassKey(java.lang.Long::class) fun valueForClassLong(): String = "long"

  @Provides
  @IntoMap
  @NumberClassKey(BigDecimal::class)
  fun valueForNumberClassBigDecimal(): String = "bigdecimal"

  @Provides
  @IntoMap
  @NumberClassKey(BigInteger::class)
  fun valueForNumberClassBigInteger(): String = "biginteger"

  @Provides @IntoMap @LongKey(100) fun valueFor100Long(): String = "100 long"

  @Provides @IntoMap @IntKey(100) fun valueFor100Int(): String = "100 int"

  @Provides @IntoMap @ShortKey(100) fun valueFor100Short(): String = "100 short"

  @Provides @IntoMap @ByteKey(100) fun valueFor100Byte(): String = "100 byte"

  @Provides @IntoMap @BooleanKey(true) fun valueForTrue(): String = "true"

  @Provides @IntoMap @CharKey('a') fun valueForA(): String = "a char"

  @Provides @IntoMap @CharKey('\n') fun valueForNewline(): String = "newline char"

  @Provides
  @IntoMap
  @UnwrappedAnnotationKey(StringKey("foo\n"))
  fun valueForUnwrappedAnnotationKeyFoo(): String = "foo annotation"

  @Provides
  @IntoMap
  @WrappedAnnotationKey(
    value = StringKey("foo"),
    integers = [1, 2, 3],
    annotations = [],
    classes = [java.lang.Long::class, java.lang.Integer::class]
  )
  fun valueForWrappedAnnotationKeyFoo(): String = "wrapped foo annotation"

  @Provides @IntoSet @Named("complexQualifier") fun valueForComplexQualifierSet(): String = "foo"

  @Provides @IntoSet fun setContribution(): CharSequence = "foo"

  @Provides
  @IntoSet
  @Named("complexQualifier")
  fun qualifiedSetContribution(): CharSequence = "qualified foo"

  @Provides @IntoMap @StringKey("key") fun mapContribution(): CharSequence = "foo value"

  @Provides
  @IntoMap
  @Named("complexQualifier")
  @StringKey("key")
  fun qualifiedMapContribution(): CharSequence = "qualified foo value"
}

/**
 * A module that uses [@Multibinds][Multibinds]-annotated abstract methods to declare multibindings.
 */
@Module
internal abstract class MultibindsModule {
  @Multibinds abstract fun emptySet(): Set<Any>

  @Multibinds abstract fun emptyMap(): Map<String, Any>

  @Multibinds abstract fun set(): Set<CharSequence>

  @Multibinds abstract fun map(): Map<String, CharSequence>

  @Multibinds @Named("complexQualifier") abstract fun emptyQualifiedSet(): Set<Any>

  @Multibinds @Named("complexQualifier") abstract fun emptyQualifiedMap(): Map<String, Any>

  @Multibinds @Named("complexQualifier") abstract fun qualifiedSet(): Set<CharSequence>

  @Multibinds @Named("complexQualifier") abstract fun qualifiedMap(): Map<String, CharSequence>
}
