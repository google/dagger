/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.functional.producers.cancellation;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.Provides;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import javax.inject.Named;

@SuppressWarnings("unused") // not actually using dependencies
@ProducerModule(subcomponents = CancellationSubcomponent.class)
final class CancellationModule {

  private final ProducerTester tester;

  CancellationModule(ProducerTester tester) {
    this.tester = checkNotNull(tester);
  }

  @Produces
  @Named("leaf1")
  ListenableFuture<String> produceLeaf1() {
    return tester.start("leaf1");
  }

  @Produces
  @Named("leaf2")
  ListenableFuture<String> produceLeaf2() {
    return tester.start("leaf2");
  }

  @Produces
  @Named("leaf3")
  ListenableFuture<String> produceLeaf3() {
    return tester.start("leaf3");
  }

  @Produces
  @Named("foo")
  ListenableFuture<String> produceFoo(@Named("leaf1") String leaf1, @Named("leaf2") String leaf2) {
    return tester.start("foo");
  }

  @Produces
  @Named("bar")
  ListenableFuture<String> produceBar(@Named("leaf2") String leaf2, @Named("leaf3") String leaf3) {
    return tester.start("bar");
  }

  @Produces
  @Named("baz")
  ListenableFuture<String> produceBaz(
      @Named("foo") Producer<String> foo, @Named("bar") String bar) {
    ListenableFuture<String> fooFuture = foo.get();
    if (!fooFuture.isDone()) {
      assertThat(fooFuture.cancel(true)).isTrue();
      assertThat(fooFuture.isCancelled()).isTrue();
    }
    return tester.start("baz");
  }

  @Provides
  @Named("providesDep")
  static String provideProvidesDep() {
    return "providesDep";
  }

  @Produces
  @Named("qux")
  ListenableFuture<String> produceQux(
      @Named("baz") String baz, @Named("providesDep") String providesDep) {
    return tester.start("qux");
  }

  @Produces
  @Named("ep1")
  ListenableFuture<String> produceEntryPoint1(@Named("qux") String qux) {
    return tester.start("entryPoint1");
  }

  @Produces
  @Named("ep2")
  ListenableFuture<String> produceEntryPoint2(@Named("bar") String bar, String dependency) {
    return tester.start("entryPoint2");
  }

  @Produces
  @Named("ep3")
  static ListenableFuture<String> produceEntryPoint3(Producer<String> dependencyProducer) {
    ListenableFuture<String> dependencyFuture = dependencyProducer.get();
    assertThat(dependencyFuture.isDone()).isFalse();
    assertThat(dependencyFuture.cancel(true)).isTrue();
    assertThat(dependencyFuture.isCancelled()).isTrue();
    return dependencyFuture;
  }
}
