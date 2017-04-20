/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.producers;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Lazy;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import javax.inject.Provider;
import javax.inject.Qualifier;

@ProducerModule(includes = ResponseModule.class)
final class ResponseProducerModule {
  @Qualifier
  @interface RequestsProducerAndProduced {}

  @Produces
  static ListenableFuture<String> greeting() {
    return Futures.immediateFuture("Hello");
  }

  @Produces
  @RequestsProducerAndProduced
  static ListenableFuture<String> intermediateGreeting(
      // TODO(beder): Allow Producer and Provider of the same type (which would force the binding
      // to be a provision binding), and add validation for that.
      @SuppressWarnings("unused") String greeting,
      Producer<String> greetingProducer,
      @SuppressWarnings("unused") Produced<String> greetingProduced,
      @SuppressWarnings("unused") Provider<Integer> requestNumberProvider,
      @SuppressWarnings("unused") Lazy<Integer> requestNumberLazy) {
    return greetingProducer.get();
  }

  @Produces
  static Response response(
      @RequestsProducerAndProduced String greeting, Request request, int requestNumber) {
    return new Response(String.format("%s, %s #%d!", greeting, request.name(), requestNumber));
  }
}
