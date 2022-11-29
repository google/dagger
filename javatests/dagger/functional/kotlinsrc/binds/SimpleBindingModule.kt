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

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import java.util.TreeSet
import javax.inject.Named
import javax.inject.Singleton

@Module(includes = [InterfaceModule::class])
internal abstract class SimpleBindingModule {

  @Binds
  abstract fun bindObject(impl: FooOfStrings): Any

  @Binds
  @Reusable
  @SomeQualifier
  abstract fun bindReusableObject(impl: FooOfStrings): Any

  @Binds
  abstract fun bindFooOfStrings(impl: FooOfStrings): Foo<String>

  @Binds
  abstract fun bindFooOfNumbers(fooOfIntegers: Foo<Int>): Foo<out Number>

  @Binds
  @Singleton
  @SomeQualifier
  abstract fun bindQualifiedFooOfStrings(impl: FooOfStrings): Foo<String>

  @Binds
  @IntoSet
  abstract fun bindFooOfIntegersIntoSet(fooOfIntegers: Foo<Int>): Foo<out Number>

  @Binds
  @IntoSet
  abstract fun bindFooExtendsNumberIntoSet(fooOfDoubles: Foo<Double>): Foo<out Number>

  @Binds
  @ElementsIntoSet
  abstract fun bindSetOfFooNumbersToObjects(
    setOfFooNumbers: Set<@JvmSuppressWildcards Foo<out Number>>
  ): Set<Any>

  @Binds
  @IntoSet
  abstract fun bindFooOfStringsIntoSetOfObjects(impl: FooOfStrings): Any

  @Binds
  @ElementsIntoSet
  abstract fun bindHashSetOfStrings(set: HashSet<String>): Set<CharSequence>

  @Binds
  @ElementsIntoSet
  abstract fun bindTreeSetOfCharSequences(set: TreeSet<CharSequence>): Set<CharSequence>

  @Binds
  @ElementsIntoSet
  abstract fun bindCollectionOfCharSequences(
    collection: Collection<@JvmSuppressWildcards CharSequence>
  ): Set<CharSequence>

  @Binds
  @IntoMap
  @IntKey(123)
  abstract fun bind123ForMap(@Named("For-123") string: String): Any

  @Binds
  @IntoMap
  @IntKey(456)
  abstract fun bind456ForMap(@Named("For-456") string: String): Any

  @Binds
  @SomeQualifier
  abstract fun primitiveToPrimitive(intValue: Int): Int

  @Binds
  @IntoSet
  abstract fun intValueIntoSet(intValue: Int): Int

  @Binds
  @IntoMap
  @IntKey(10)
  abstract fun intValueIntoMap(intValue: Int): Int

  @Binds
  @IntoMap
  @IntKey(123)
  @SomeQualifier
  abstract fun bindFooOfStringsIntoQualifiedMap(fooOfStrings: FooOfStrings): Any

  companion object {
    @Provides
    fun provideFooOfIntegers(): Foo<Int> = object : Foo<Int> {}

    @Provides
    fun provideFooOfDoubles(): Foo<Double> = object : Foo<Double> {}

    @Provides
    fun provideStringHashSet(): HashSet<String> = hashSetOf("hash-string1", "hash-string2")

    @Provides
    fun provideCharSequenceTreeSet(): TreeSet<CharSequence> = sortedSetOf(
      "tree-charSequence1", "tree-charSequence2"
    )

    @Provides
    fun provideCharSequenceCollection(): Collection<CharSequence> = listOf("list-charSequence")

    @Provides
    @IntoMap
    @IntKey(789)
    fun provide789ForMap(): Any = "789-string"

    @Provides
    fun intValue(): Int = 100

    @Provides
    @Named("For-123")
    fun provide123String(): String = "123-string"

    @Provides
    @Named("For-456")
    fun provide456String(): String = "456-string"
  }
}
