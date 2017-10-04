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

package dagger.functional.producers.optional;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.Provides;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Retention;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.inject.Qualifier;

/** Classes to support testing {@code BindsOptionalOf} functionality. */
final class OptionalBindingComponents {

  /** A qualifier. */
  @Qualifier
  @Retention(RUNTIME)
  @interface SomeQualifier {}

  /** A value object that contains various optionally-bound objects. */
  @AutoValue
  abstract static class Values {
    abstract Optional<Value> optionalInstance();

    abstract Optional<Producer<Value>> optionalProducer();

    abstract Optional<Produced<Value>> optionalProduced();
  }

  enum Value {
    VALUE,
    QUALIFIED_VALUE
  }

  @Module
  static final class ExecutorModule {
    @Provides
    @Production
    static Executor executor() {
      return Executors.newSingleThreadExecutor();
    }
  }

  /** Binds optionals and {@link Values}. */
  @ProducerModule
  abstract static class OptionalBindingModule {
    @BindsOptionalOf
    abstract Value value();

    @BindsOptionalOf
    @SomeQualifier
    abstract Value qualifiedValue();

    @BindsOptionalOf
    abstract Object nullableObject();

    @Produces
    static Values values(
        Optional<Value> optionalInstance,
        Optional<Producer<Value>> optionalProducer,
        Optional<Produced<Value>> optionalProduced) {
      return new AutoValue_OptionalBindingComponents_Values(
          optionalInstance, optionalProducer, optionalProduced);
    }

    @Produces
    @SomeQualifier
    static Values qualifiedValues(
        Optional<Value> optionalInstance,
        Optional<Producer<Value>> optionalProducer,
        Optional<Produced<Value>> optionalProduced) {
      return new AutoValue_OptionalBindingComponents_Values(
          optionalInstance, optionalProducer, optionalProduced);
    }
  }

  /** Binds {@link Value} using {@link Producer}s. */
  @ProducerModule
  abstract static class ConcreteBindingProducerModule {
    @Produces
    static Value value() {
      return Value.VALUE;
    }

    @Produces
    @SomeQualifier
    static Value qualifiedValue() {
      return Value.QUALIFIED_VALUE;
    }

    // @Produces @Nullable has no effect (and ProducesMethodValidator warns when the two are used
    // together. Use a @Provides method and let it be wrapped into a producerFromProvider for the
    // purposes of the test
    @Provides
    @Nullable
    static Object nullableObject() {
      return null;
    }
  }

  /** Binds {@link Value} using {@link Provider}s. */
  @Module
  abstract static class ConcreteBindingModule {
    @Provides
    static Value value() {
      return Value.VALUE;
    }

    @Provides
    @SomeQualifier
    static Value qualifiedValue() {
      return Value.QUALIFIED_VALUE;
    }

    @Provides
    @Nullable
    static Object nullableObject() {
      return null;
    }
  }

  interface OptionalBindingComponent {
    ListenableFuture<Values> values();

    ListenableFuture<Optional<Value>> optionalInstance();

    ListenableFuture<Optional<Producer<Value>>> optionalProducer();

    ListenableFuture<Optional<Produced<Value>>> optionalProduced();

    @SomeQualifier
    ListenableFuture<Values> qualifiedValues();

    @SomeQualifier
    ListenableFuture<Optional<Value>> qualifiedOptionalInstance();

    @SomeQualifier
    ListenableFuture<Optional<Producer<Value>>> qualifiedOptionalProducer();

    @SomeQualifier
    ListenableFuture<Optional<Produced<Value>>> qualifiedOptionalProduced();

    // Nullable bindings can satisfy optional bindings except for Optional<Foo>.
    ListenableFuture<Optional<Producer<Object>>> optionalNullableProducer();

    ListenableFuture<Optional<Produced<Object>>> optionalNullableProduced();
  }

  @ProductionComponent(modules = {ExecutorModule.class, OptionalBindingModule.class})
  interface AbsentOptionalBindingComponent extends OptionalBindingComponent {
    PresentOptionalBindingSubcomponent presentChild();
  }

  @ProductionComponent(
    modules = {
      ExecutorModule.class,
      OptionalBindingModule.class,
      ConcreteBindingProducerModule.class
    }
  )
  interface PresentOptionalBindingComponent extends OptionalBindingComponent {}

  @ProductionSubcomponent(modules = ConcreteBindingProducerModule.class)
  interface PresentOptionalBindingSubcomponent extends OptionalBindingComponent {}

  @ProductionComponent(
    modules = {ExecutorModule.class, OptionalBindingModule.class, ConcreteBindingModule.class}
  )
  interface PresentOptionalProvisionBindingComponent extends OptionalBindingComponent {}
}
