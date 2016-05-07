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
package producerstest.multibindings;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Multibindings;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.producers.Produced;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import java.util.Map;
import java.util.Set;
import producerstest.multibindings.Qualifiers.ObjCount;
import producerstest.multibindings.Qualifiers.PossiblyThrowingMap;
import producerstest.multibindings.Qualifiers.PossiblyThrowingSet;

@ProducerModule
final class MultibindingProducerModule {
  @Produces
  @IntoSet
  static ListenableFuture<String> futureStr() {
    return Futures.immediateFuture("foo");
  }

  @Produces
  @IntoSet
  static String str() {
    return "bar";
  }

  @Produces
  @ElementsIntoSet
  static ListenableFuture<Set<String>> futureStrs() {
    return Futures.<Set<String>>immediateFuture(ImmutableSet.of("foo1", "foo2"));
  }

  @Produces
  @ElementsIntoSet
  static Set<String> strs() {
    return ImmutableSet.of("bar1", "bar2");
  }

  @Produces
  static int strCount(Set<String> strs) {
    return strs.size();
  }

  @Produces
  @IntoSet
  @PossiblyThrowingSet
  static String successfulStringForSet() {
    return "singleton";
  }

  @Produces
  @ElementsIntoSet
  @PossiblyThrowingSet
  static Set<String> successfulStringsForSet() {
    return ImmutableSet.of("double", "ton");
  }

  @Produces
  @IntoSet
  @PossiblyThrowingSet
  static String throwingStringForSet() {
    throw new RuntimeException("monkey");
  }

  @Produces
  @IntoMap
  @IntKey(42)
  static ListenableFuture<String> futureFor42() {
    return Futures.immediateFuture("forty two");
  }

  @Produces
  @IntoMap
  @IntKey(15)
  static String valueFor15() {
    return "fifteen";
  }

  @Produces
  @IntoMap
  @PossiblyThrowingMap
  @IntKey(42)
  static ListenableFuture<String> successfulFutureFor42() {
    return Futures.immediateFuture("forty two");
  }

  @Produces
  @IntoMap
  @PossiblyThrowingMap
  @IntKey(15)
  static String throwingValueFor15() {
    throw new RuntimeException("monkey");
  }

  @Multibindings
  interface MultiBindings {
    Set<Object> objs();

    Map<Object, Object> objMap();
  }

  @Produces
  @ObjCount
  static int objCount(Set<Produced<Object>> objs, Map<Object, Produced<Object>> objMap) {
    return objs.size() + objMap.size();
  }
}
