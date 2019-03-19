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

package dagger.functional.producers.provisions;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.functional.producers.ExecutorModule;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import javax.inject.Inject;
import javax.inject.Qualifier;

/** Tests for requesting provisions from producers. */
final class Provisions {
  static final class InjectedClass {
    @Inject InjectedClass() {}
  }

  static final class WrappedProducer<T> {
    final Producer<T> producer;

    WrappedProducer(Producer<T> producer) {
      this.producer = producer;
    }
  }

  static final class Output {
    final Producer<InjectedClass> injectedClass1;
    final Producer<InjectedClass> injectedClass2;

    Output(Producer<InjectedClass> injectedClass1, Producer<InjectedClass> injectedClass2) {
      this.injectedClass1 = injectedClass1;
      this.injectedClass2 = injectedClass2;
    }
  }

  @Qualifier @interface First {}
  @Qualifier @interface Second {}

  @ProducerModule
  static final class TestModule {
    @Produces @First static WrappedProducer<InjectedClass> firstProducer(
        Producer<InjectedClass> injectedClass) {
      return new WrappedProducer<>(injectedClass);
    }

    @Produces @Second static WrappedProducer<InjectedClass> secondProducer(
        Producer<InjectedClass> injectedClass) {
      return new WrappedProducer<>(injectedClass);
    }

    @Produces static Output output(
        @First WrappedProducer<InjectedClass> producer1,
        @Second WrappedProducer<InjectedClass> producer2) {
      return new Output(producer1.producer, producer2.producer);
    }
  }

  @ProductionComponent(modules = {ExecutorModule.class, TestModule.class})
  interface TestComponent {
    ListenableFuture<Output> output();
  }

  private Provisions() {}
}
