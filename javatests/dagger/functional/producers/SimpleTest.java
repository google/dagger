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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Qualifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleTest {
  @ProductionComponent(modules = {ExecutorModule.class, ResponseProducerModule.class})
  interface SimpleComponent {
    ListenableFuture<Response> response();
  }

  @Module
  static final class ExecutorModule {
    @Provides
    @Production
    Executor executor() {
      return MoreExecutors.directExecutor();
    }
  }

  @ProducerModule(includes = ResponseModule.class)
  static final class ResponseProducerModule {
    @Qualifier
    @interface
    RequestsProducerAndProduced {}

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

  @Module
  static final class ResponseModule {
    @Provides
    static int requestNumber() {
      return 5;
    }
  }

  static final class Request {
    private final String name;

    @Inject
    Request() {
      this.name = "Request";
    }

    String name() {
      return this.name;
    }
  }

  static final class Response {
    private final String data;

    Response(String data) {
      this.data = data;
    }

    String data() {
      return this.data;
    }
  }

  @Test public void testSimpleComponent() throws Exception {
    SimpleComponent simpleComponent = DaggerSimpleTest_SimpleComponent.create();
    assertThat(simpleComponent).isNotNull();
    assertThat(simpleComponent.response().get().data()).isEqualTo("Hello, Request #5!");
  }
}
