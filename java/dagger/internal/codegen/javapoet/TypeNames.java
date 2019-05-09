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

package dagger.internal.codegen.javapoet;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import dagger.Lazy;
import dagger.MembersInjector;
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
import dagger.producers.internal.DependencyMethodProducer;
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
import javax.inject.Provider;

/** Common names and convenience methods for JavaPoet {@link TypeName} usage. */
public final class TypeNames {

  public static final ClassName ABSTRACT_PRODUCER = ClassName.get(AbstractProducer.class);
  public static final ClassName DEPENDENCY_METHOD_PRODUCER =
      ClassName.get(DependencyMethodProducer.class);
  public static final ClassName DOUBLE_CHECK = ClassName.get(DoubleCheck.class);
  public static final ClassName FACTORY = ClassName.get(Factory.class);
  public static final ClassName FUTURES = ClassName.get(Futures.class);
  public static final ClassName INSTANCE_FACTORY = ClassName.get(InstanceFactory.class);
  public static final ClassName LAZY = ClassName.get(Lazy.class);
  public static final ClassName LIST = ClassName.get(List.class);
  public static final ClassName LISTENABLE_FUTURE = ClassName.get(ListenableFuture.class);
  public static final ClassName MAP_FACTORY = ClassName.get(MapFactory.class);
  public static final ClassName MAP_OF_PRODUCED_PRODUCER =
      ClassName.get(MapOfProducedProducer.class);
  public static final ClassName MAP_OF_PRODUCER_PRODUCER =
      ClassName.get(MapOfProducerProducer.class);
  public static final ClassName MAP_PRODUCER = ClassName.get(MapProducer.class);
  public static final ClassName MAP_PROVIDER_FACTORY = ClassName.get(MapProviderFactory.class);
  public static final ClassName MEMBERS_INJECTOR = ClassName.get(MembersInjector.class);
  public static final ClassName MEMBERS_INJECTORS = ClassName.get(MembersInjectors.class);
  public static final ClassName PRODUCER_TOKEN = ClassName.get(ProducerToken.class);
  public static final ClassName PRODUCED = ClassName.get(Produced.class);
  public static final ClassName PRODUCER = ClassName.get(Producer.class);
  public static final ClassName PRODUCERS = ClassName.get(Producers.class);
  public static final ClassName PRODUCTION_COMPONENT_MONITOR_FACTORY =
      ClassName.get(ProductionComponentMonitor.Factory.class);
  public static final ClassName PROVIDER = ClassName.get(Provider.class);
  public static final ClassName PROVIDER_OF_LAZY = ClassName.get(ProviderOfLazy.class);
  public static final ClassName SET = ClassName.get(Set.class);
  public static final ClassName SET_FACTORY = ClassName.get(SetFactory.class);
  public static final ClassName SET_OF_PRODUCED_PRODUCER =
      ClassName.get(SetOfProducedProducer.class);
  public static final ClassName SET_PRODUCER = ClassName.get(SetProducer.class);
  public static final ClassName SINGLE_CHECK = ClassName.get(SingleCheck.class);

  /**
   * {@link TypeName#VOID} is lowercase-v {@code void} whereas this represents the class, {@link
   * Void}.
   */
  public static final ClassName VOID_CLASS = ClassName.get(Void.class);

  public static ParameterizedTypeName abstractProducerOf(TypeName typeName) {
    return ParameterizedTypeName.get(ABSTRACT_PRODUCER, typeName);
  }

  public static ParameterizedTypeName factoryOf(TypeName factoryType) {
    return ParameterizedTypeName.get(FACTORY, factoryType);
  }

  public static ParameterizedTypeName lazyOf(TypeName typeName) {
    return ParameterizedTypeName.get(LAZY, typeName);
  }

  public static ParameterizedTypeName listOf(TypeName typeName) {
    return ParameterizedTypeName.get(LIST, typeName);
  }

  public static ParameterizedTypeName listenableFutureOf(TypeName typeName) {
    return ParameterizedTypeName.get(LISTENABLE_FUTURE, typeName);
  }

  public static ParameterizedTypeName membersInjectorOf(TypeName membersInjectorType) {
    return ParameterizedTypeName.get(MEMBERS_INJECTOR, membersInjectorType);
  }

  public static ParameterizedTypeName producedOf(TypeName typeName) {
    return ParameterizedTypeName.get(PRODUCED, typeName);
  }

  public static ParameterizedTypeName producerOf(TypeName typeName) {
    return ParameterizedTypeName.get(PRODUCER, typeName);
  }

  public static ParameterizedTypeName dependencyMethodProducerOf(TypeName typeName) {
    return ParameterizedTypeName.get(DEPENDENCY_METHOD_PRODUCER, typeName);
  }

  public static ParameterizedTypeName providerOf(TypeName typeName) {
    return ParameterizedTypeName.get(PROVIDER, typeName);
  }

  public static ParameterizedTypeName setOf(TypeName elementType) {
    return ParameterizedTypeName.get(SET, elementType);
  }

  /**
   * Returns the {@link TypeName} for the raw type of the given type name. If the argument isn't a
   * parameterized type, it returns the argument unchanged.
   */
  public static TypeName rawTypeName(TypeName typeName) {
    return (typeName instanceof ParameterizedTypeName)
        ? ((ParameterizedTypeName) typeName).rawType
        : typeName;
  }

  private TypeNames() {}
}
