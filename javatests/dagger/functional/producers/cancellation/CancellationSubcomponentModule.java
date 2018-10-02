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
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import javax.inject.Named;

@SuppressWarnings("unused") // not actually using dependencies
@ProducerModule
final class CancellationSubcomponentModule {

  private final ProducerTester tester;

  CancellationSubcomponentModule(ProducerTester tester) {
    this.tester = checkNotNull(tester);
  }

  @Produces
  @Named("subLeaf")
  ListenableFuture<String> produceSubLeaf() {
    return tester.start("subLeaf");
  }

  @Produces
  @Named("subTask1")
  ListenableFuture<String> produceSubTask1(
      @Named("subLeaf") String subLeaf, @Named("qux") String qux) {
    return tester.start("subTask1");
  }

  @Produces
  @Named("subTask2")
  ListenableFuture<String> produceSubTask2(@Named("foo") String foo, Producer<String> dependency) {
    ListenableFuture<String> dependencyFuture = dependency.get();
    assertThat(dependencyFuture.cancel(true)).isTrue();
    assertThat(dependencyFuture.isCancelled()).isTrue();
    return tester.start("subTask2");
  }

  @Produces
  @Named("subEntryPoint")
  ListenableFuture<String> produceSubEntryPoint(
      @Named("subTask1") String subTask1, @Named("subTask2") String subTask2) {
    return tester.start("subEntryPoint");
  }
}
