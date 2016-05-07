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
package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import dagger.MembersInjector;
import dagger.internal.DelegateFactory;
import dagger.internal.DoubleCheck;
import dagger.internal.Factory;
import dagger.internal.InstanceFactory;
import dagger.internal.MapFactory;
import dagger.internal.MapProviderFactory;
import dagger.internal.MembersInjectors;
import dagger.internal.ProviderOfLazy;
import dagger.internal.SetFactory;
import dagger.internal.SingleCheck;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.internal.AbstractProducer;
import dagger.producers.internal.MapOfProducedProducer;
import dagger.producers.internal.MapOfProducerProducer;
import dagger.producers.internal.MapProducer;
import dagger.producers.internal.Producers;
import dagger.producers.internal.SetOfProducedProducer;
import dagger.producers.internal.SetProducer;
import dagger.producers.monitoring.ProducerToken;
import dagger.producers.monitoring.ProductionComponentMonitor;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.inject.Provider;

/**
 * Common names and convenience methods for JavaPoet {@link TypeName} usage.
 */
final class TypeNames {

  static final ClassName ABSTRACT_PRODUCER = ClassName.get(AbstractProducer.class);
  static final ClassName ASYNC_FUNCTION = ClassName.get(AsyncFunction.class);
  static final ClassName DELEGATE_FACTORY = ClassName.get(DelegateFactory.class);
  static final ClassName DOUBLE_CHECK = ClassName.get(DoubleCheck.class);
  static final ClassName EXECUTOR = ClassName.get(Executor.class);
  static final ClassName FACTORY = ClassName.get(Factory.class);
  static final ClassName FUTURES = ClassName.get(Futures.class);
  static final ClassName ILLEGAL_STATE_EXCEPTION = ClassName.get(IllegalStateException.class);
  static final ClassName IMMUTABLE_SET = ClassName.get(ImmutableSet.class);
  static final ClassName INSTANCE_FACTORY = ClassName.get(InstanceFactory.class);
  static final ClassName LIST = ClassName.get(List.class);
  static final ClassName LISTENABLE_FUTURE = ClassName.get(ListenableFuture.class);
  static final ClassName MAP_FACTORY = ClassName.get(MapFactory.class);
  static final ClassName MAP_OF_PRODUCED_PRODUCER = ClassName.get(MapOfProducedProducer.class);
  static final ClassName MAP_OF_PRODUCER_PRODUCER = ClassName.get(MapOfProducerProducer.class);
  static final ClassName MAP_PRODUCER = ClassName.get(MapProducer.class);
  static final ClassName MAP_PROVIDER_FACTORY = ClassName.get(MapProviderFactory.class);
  static final ClassName MEMBERS_INJECTOR = ClassName.get(MembersInjector.class);
  static final ClassName MEMBERS_INJECTORS = ClassName.get(MembersInjectors.class);
  static final ClassName PRODUCER_TOKEN = ClassName.get(ProducerToken.class);
  static final ClassName PRODUCED = ClassName.get(Produced.class);
  static final ClassName PRODUCER = ClassName.get(Producer.class);
  static final ClassName PRODUCERS = ClassName.get(Producers.class);
  static final ClassName PROVIDER = ClassName.get(Provider.class);
  static final ClassName PROVIDER_OF_LAZY = ClassName.get(ProviderOfLazy.class);
  static final ClassName SET = ClassName.get(Set.class);
  static final ClassName SET_FACTORY = ClassName.get(SetFactory.class);
  static final ClassName SET_OF_PRODUCED_PRODUCER = ClassName.get(SetOfProducedProducer.class);
  static final ClassName SET_PRODUCER = ClassName.get(SetProducer.class);
  static final ClassName SINGLE_CHECK = ClassName.get(SingleCheck.class);
  static final ClassName STRING = ClassName.get(String.class);
  static final ClassName UNSUPPORTED_OPERATION_EXCEPTION =
      ClassName.get(UnsupportedOperationException.class);

  /**
   * {@link TypeName#VOID} is lowercase-v {@code void} whereas this represents the class, {@link
   * Void}.
   */
  static final ClassName VOID_CLASS = ClassName.get(Void.class);

  static final TypeName SET_OF_FACTORIES =
      ParameterizedTypeName.get(
          ClassName.get(Set.class), ClassName.get(ProductionComponentMonitor.Factory.class));

  static ParameterizedTypeName listOf(TypeName typeName) {
    return ParameterizedTypeName.get(LIST, typeName);
  }

  static ParameterizedTypeName abstractProducerOf(TypeName typeName) {
    return ParameterizedTypeName.get(ABSTRACT_PRODUCER, typeName);
  }

  static ParameterizedTypeName producedOf(TypeName typeName) {
    return ParameterizedTypeName.get(PRODUCED, typeName);
  }

  static ParameterizedTypeName listenableFutureOf(TypeName typeName) {
    return ParameterizedTypeName.get(LISTENABLE_FUTURE, typeName);
  }

  static ParameterizedTypeName providerOf(TypeName typeName) {
    return ParameterizedTypeName.get(PROVIDER, typeName);
  }

  static ParameterizedTypeName membersInjectorOf(TypeName membersInjectorType) {
    return ParameterizedTypeName.get(MEMBERS_INJECTOR, membersInjectorType);
  }

  static ParameterizedTypeName factoryOf(TypeName factoryType) {
    return ParameterizedTypeName.get(FACTORY, factoryType);
  }

  private TypeNames() {}
}
