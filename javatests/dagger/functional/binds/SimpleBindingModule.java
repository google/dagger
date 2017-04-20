/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.functional.binds;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.Reusable;
import dagger.functional.SomeQualifier;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Named;
import javax.inject.Singleton;

@Module(includes = InterfaceModule.class)
abstract class SimpleBindingModule {
  @Binds
  abstract Object bindObject(FooOfStrings impl);

  @Binds
  @Reusable
  @SomeQualifier
  abstract Object bindReusableObject(FooOfStrings impl);

  @Binds
  abstract Foo<String> bindFooOfStrings(FooOfStrings impl);

  @Binds
  abstract Foo<? extends Number> bindFooOfNumbers(Foo<Integer> fooOfIntegers);

  @Binds
  @Singleton
  @SomeQualifier
  abstract Foo<String> bindQualifiedFooOfStrings(FooOfStrings impl);

  @Provides
  static Foo<Integer> provideFooOfIntegers() {
    return new Foo<Integer>() {};
  }

  @Provides
  static Foo<Double> provideFooOfDoubles() {
    return new Foo<Double>() {};
  }

  @Binds
  @IntoSet
  abstract Foo<? extends Number> bindFooOfIntegersIntoSet(Foo<Integer> fooOfIntegers);

  @Binds
  @IntoSet
  abstract Foo<? extends Number> bindFooExtendsNumberIntoSet(Foo<Double> fooOfDoubles);

  @Binds
  @ElementsIntoSet
  abstract Set<Object> bindSetOfFooNumbersToObjects(Set<Foo<? extends Number>> setOfFooNumbers);

  @Binds
  @IntoSet
  abstract Object bindFooOfStringsIntoSetOfObjects(FooOfStrings impl);

  @Provides
  static HashSet<String> provideStringHashSet() {
    return new HashSet<>(Arrays.asList("hash-string1", "hash-string2"));
  }

  @Provides
  static TreeSet<CharSequence> provideCharSequenceTreeSet() {
    return new TreeSet<CharSequence>(Arrays.asList("tree-charSequence1", "tree-charSequence2"));
  }

  @Provides
  static Collection<CharSequence> provideCharSequenceCollection() {
    return Arrays.<CharSequence>asList("list-charSequence");
  }

  @Binds
  @ElementsIntoSet
  abstract Set<CharSequence> bindHashSetOfStrings(HashSet<String> set);

  @Binds
  @ElementsIntoSet
  abstract Set<CharSequence> bindTreeSetOfCharSequences(TreeSet<CharSequence> set);

  @Binds
  @ElementsIntoSet
  abstract Set<CharSequence> bindCollectionOfCharSequences(Collection<CharSequence> collection);

  @Binds
  @IntoMap
  @IntKey(123)
  abstract Object bind123ForMap(@Named("For-123") String string);

  @Binds
  @IntoMap
  @IntKey(456)
  abstract Object bind456ForMap(@Named("For-456") String string);

  @Provides
  @IntoMap
  @IntKey(789)
  static Object provide789ForMap() {
    return "789-string";
  }

  @Binds
  @SomeQualifier
  abstract int primitiveToPrimitive(int intValue);

  @Binds
  @IntoSet
  abstract int intValueIntoSet(int intValue);

  @Binds
  @IntoMap
  @IntKey(10)
  abstract int intValueIntoMap(int intValue);

  @Provides
  static int intValue() {
    return 100;
  }

  @Binds
  @IntoMap
  @IntKey(123)
  @SomeQualifier
  abstract Object bindFooOfStringsIntoQualifiedMap(FooOfStrings fooOfStrings);
  
  @Provides
  @Named("For-123")
  static String provide123String() {
    return "123-string";
  }

  @Provides
  @Named("For-456")
  static String provide456String() {
    return "456-string";
  }
}
