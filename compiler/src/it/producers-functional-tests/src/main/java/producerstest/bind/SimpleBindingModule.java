/*
 * Copyright (C) 2016 Google, Inc.
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
package producerstest.bind;

import com.google.common.util.concurrent.MoreExecutors;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoSet;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import javax.inject.Qualifier;
import javax.inject.Singleton;

@ProducerModule(includes = SimpleBindingModule.ExecutorModule.class)
abstract class SimpleBindingModule {
  @Binds
  abstract Object bindObject(FooOfStrings impl);

  @Binds
  abstract Foo<String> bindFooOfStrings(FooOfStrings impl);

  @Binds
  abstract Foo<? extends Number> bindFooOfNumbers(Foo<Integer> fooOfIntegers);

  @Binds
  @Singleton
  @SomeQualifier
  abstract Foo<String> bindQualifiedFooOfStrings(FooOfStrings impl);

  @Produces
  static FooOfStrings produceFooOfStrings() {
    return new FooOfStrings();
  }

  @Produces
  static Foo<Integer> produceFooOfIntegers() {
    return new Foo<Integer>() {};
  }

  @Produces
  static Foo<Double> produceFooOfDoubles() {
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

  @Produces
  static HashSet<String> produceStringHashSet() {
    return new HashSet<>(Arrays.asList("hash-string1", "hash-string2"));
  }

  @Produces
  static TreeSet<CharSequence> produceCharSequenceTreeSet() {
    return new TreeSet<CharSequence>(Arrays.asList("tree-charSequence1", "tree-charSequence2"));
  }

  @Produces
  static Collection<CharSequence> produceCharSequenceCollection() {
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

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @interface SomeQualifier {}

  @Module
  static final class ExecutorModule {
    @Provides @Production
    static Executor provideExecutor() {
      return MoreExecutors.directExecutor();
    }
  }
}
