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

import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

/** Common names and convenience methods for JavaPoet {@link TypeName} usage. */
public final class TypeNames {

  // Dagger Core classnames
  public static final ClassName ASSISTED = ClassName.get("dagger.assisted", "Assisted");
  public static final ClassName ASSISTED_FACTORY =
      ClassName.get("dagger.assisted", "AssistedFactory");
  public static final ClassName ASSISTED_INJECT =
      ClassName.get("dagger.assisted", "AssistedInject");
  public static final ClassName BINDS = ClassName.get("dagger", "Binds");
  public static final ClassName BINDS_INSTANCE = ClassName.get("dagger", "BindsInstance");
  public static final ClassName BINDS_OPTIONAL_OF = ClassName.get("dagger", "BindsOptionalOf");
  public static final ClassName COMPONENT = ClassName.get("dagger", "Component");
  public static final ClassName COMPONENT_BUILDER = COMPONENT.nestedClass("Builder");
  public static final ClassName COMPONENT_FACTORY = COMPONENT.nestedClass("Factory");
  public static final ClassName DAGGER_PROCESSING_OPTIONS =
      ClassName.get("dagger", "DaggerProcessingOptions");
  public static final ClassName ELEMENTS_INTO_SET =
      ClassName.get("dagger.multibindings", "ElementsIntoSet");
  public static final ClassName INTO_MAP = ClassName.get("dagger.multibindings", "IntoMap");
  public static final ClassName INTO_SET = ClassName.get("dagger.multibindings", "IntoSet");
  public static final ClassName MAP_KEY = ClassName.get("dagger", "MapKey");
  public static final ClassName MODULE = ClassName.get("dagger", "Module");
  public static final ClassName MULTIBINDS = ClassName.get("dagger.multibindings", "Multibinds");
  public static final ClassName PROVIDES = ClassName.get("dagger", "Provides");
  public static final ClassName REUSABLE = ClassName.get("dagger", "Reusable");
  public static final ClassName SUBCOMPONENT = ClassName.get("dagger", "Subcomponent");
  public static final ClassName SUBCOMPONENT_BUILDER = SUBCOMPONENT.nestedClass("Builder");
  public static final ClassName SUBCOMPONENT_FACTORY = SUBCOMPONENT.nestedClass("Factory");

  // Dagger Internal classnames
  public static final ClassName DELEGATE_FACTORY =
      ClassName.get("dagger.internal", "DelegateFactory");
  public static final ClassName DOUBLE_CHECK = ClassName.get("dagger.internal", "DoubleCheck");

  public static final ClassName FACTORY = ClassName.get("dagger.internal", "Factory");
  public static final ClassName INJECTED_FIELD_SIGNATURE =
      ClassName.get("dagger.internal", "InjectedFieldSignature");
  public static final ClassName INSTANCE_FACTORY =
      ClassName.get("dagger.internal", "InstanceFactory");
  public static final ClassName MAP_FACTORY = ClassName.get("dagger.internal", "MapFactory");
  public static final ClassName MAP_PROVIDER_FACTORY =
      ClassName.get("dagger.internal", "MapProviderFactory");
  public static final ClassName MEMBERS_INJECTOR = ClassName.get("dagger", "MembersInjector");
  public static final ClassName MEMBERS_INJECTORS =
      ClassName.get("dagger.internal", "MembersInjectors");
  public static final ClassName PROVIDER = ClassName.get("javax.inject", "Provider");
  public static final ClassName PROVIDER_OF_LAZY =
      ClassName.get("dagger.internal", "ProviderOfLazy");
  public static final ClassName SCOPE_METADATA = ClassName.get("dagger.internal", "ScopeMetadata");
  public static final ClassName QUALIFIER_METADATA =
      ClassName.get("dagger.internal", "QualifierMetadata");
  public static final ClassName SET_FACTORY = ClassName.get("dagger.internal", "SetFactory");
  public static final ClassName SINGLE_CHECK = ClassName.get("dagger.internal", "SingleCheck");
  public static final ClassName LAZY = ClassName.get("dagger", "Lazy");

  // Dagger Producers classnames
  public static final ClassName ABSTRACT_PRODUCER =
      ClassName.get("dagger.producers.internal", "AbstractProducer");
  public static final ClassName CANCELLATION_LISTENER =
      ClassName.get("dagger.producers.internal", "CancellationListener");
  public static final ClassName CANCELLATION_POLICY =
      ClassName.get("dagger.producers", "CancellationPolicy");
  public static final ClassName DELEGATE_PRODUCER =
      ClassName.get("dagger.producers.internal", "DelegateProducer");
  public static final ClassName DEPENDENCY_METHOD_PRODUCER =
      ClassName.get("dagger.producers.internal", "DependencyMethodProducer");
  public static final ClassName MAP_OF_PRODUCED_PRODUCER =
      ClassName.get("dagger.producers.internal", "MapOfProducedProducer");
  public static final ClassName MAP_OF_PRODUCER_PRODUCER =
      ClassName.get("dagger.producers.internal", "MapOfProducerProducer");
  public static final ClassName MAP_PRODUCER =
      ClassName.get("dagger.producers.internal", "MapProducer");
  public static final ClassName MONITORS =
      ClassName.get("dagger.producers.monitoring.internal", "Monitors");
  public static final ClassName PRODUCED = ClassName.get("dagger.producers", "Produced");
  public static final ClassName PRODUCER = ClassName.get("dagger.producers", "Producer");
  public static final ClassName PRODUCERS = ClassName.get("dagger.producers.internal", "Producers");
  public static final ClassName PRODUCER_MODULE =
      ClassName.get("dagger.producers", "ProducerModule");
  public static final ClassName PRODUCES = ClassName.get("dagger.producers", "Produces");
  public static final ClassName PRODUCTION = ClassName.get("dagger.producers", "Production");
  public static final ClassName PRODUCTION_COMPONENT =
      ClassName.get("dagger.producers", "ProductionComponent");
  public static final ClassName PRODUCTION_COMPONENT_BUILDER =
      PRODUCTION_COMPONENT.nestedClass("Builder");
  public static final ClassName PRODUCTION_COMPONENT_FACTORY =
      PRODUCTION_COMPONENT.nestedClass("Factory");
  public static final ClassName PRODUCTION_EXECTUTOR_MODULE =
      ClassName.get("dagger.producers.internal", "ProductionExecutorModule");
  public static final ClassName PRODUCTION_IMPLEMENTATION =
      ClassName.get("dagger.producers.internal", "ProductionImplementation");
  public static final ClassName PRODUCTION_SUBCOMPONENT =
      ClassName.get("dagger.producers", "ProductionSubcomponent");
  public static final ClassName PRODUCTION_SUBCOMPONENT_BUILDER =
      PRODUCTION_SUBCOMPONENT.nestedClass("Builder");
  public static final ClassName PRODUCTION_SUBCOMPONENT_FACTORY =
      PRODUCTION_SUBCOMPONENT.nestedClass("Factory");
  public static final ClassName PRODUCER_TOKEN =
      ClassName.get("dagger.producers.monitoring", "ProducerToken");
  public static final ClassName PRODUCTION_COMPONENT_MONITOR =
      ClassName.get("dagger.producers.monitoring", "ProductionComponentMonitor");
  public static final ClassName PRODUCTION_COMPONENT_MONITOR_FACTORY =
      ClassName.get("dagger.producers.monitoring", "ProductionComponentMonitor", "Factory");
  public static final ClassName SET_OF_PRODUCED_PRODUCER =
      ClassName.get("dagger.producers.internal", "SetOfProducedProducer");
  public static final ClassName SET_PRODUCER =
      ClassName.get("dagger.producers.internal", "SetProducer");
  public static final ClassName PRODUCTION_SCOPE =
      ClassName.get("dagger.producers", "ProductionScope");

  // Other classnames
  public static final ClassName EXECUTOR = ClassName.get("java.util.concurrent", "Executor");
  public static final ClassName ERROR = ClassName.get("java.lang", "Error");
  public static final ClassName EXCEPTION = ClassName.get("java.lang", "Exception");
  public static final ClassName RUNTIME_EXCEPTION = ClassName.get("java.lang", "RuntimeException");
  public static final ClassName MAP = ClassName.get("java.util", "Map");
  public static final ClassName KOTLIN_METADATA = ClassName.get("kotlin", "Metadata");
  public static final ClassName IMMUTABLE_MAP =
      ClassName.get("com.google.common.collect", "ImmutableMap");
  public static final ClassName SINGLETON = ClassName.get("jakarta.inject", "Singleton");
  public static final ClassName SINGLETON_JAVAX = ClassName.get("javax.inject", "Singleton");
  public static final ClassName SCOPE = ClassName.get("jakarta.inject", "Scope");
  public static final ClassName SCOPE_JAVAX = ClassName.get("javax.inject", "Scope");
  public static final ClassName INJECT = ClassName.get("jakarta.inject", "Inject");
  public static final ClassName INJECT_JAVAX = ClassName.get("javax.inject", "Inject");
  public static final ClassName QUALIFIER = ClassName.get("jakarta.inject", "Qualifier");
  public static final ClassName QUALIFIER_JAVAX = ClassName.get("javax.inject", "Qualifier");
  public static final ClassName COLLECTION = ClassName.get("java.util", "Collection");
  public static final ClassName LIST = ClassName.get("java.util", "List");
  public static final ClassName SET = ClassName.get("java.util", "Set");
  public static final ClassName IMMUTABLE_SET =
      ClassName.get("com.google.common.collect", "ImmutableSet");
  public static final ClassName FUTURES =
      ClassName.get("com.google.common.util.concurrent", "Futures");
  public static final ClassName LISTENABLE_FUTURE =
      ClassName.get("com.google.common.util.concurrent", "ListenableFuture");
  public static final ClassName FLUENT_FUTURE =
      ClassName.get("com.google.common.util.concurrent", "FluentFuture");
  public static final ClassName GUAVA_OPTIONAL =
      ClassName.get("com.google.common.base", "Optional");
  public static final ClassName JDK_OPTIONAL = ClassName.get("java.util", "Optional");
  public static final ClassName OVERRIDE = ClassName.get("java.lang", "Override");
  public static final ClassName JVM_STATIC = ClassName.get("kotlin.jvm", "JvmStatic");
  public static final ClassName CLASS = ClassName.get("java.lang", "Class");
  public static final ClassName KCLASS = ClassName.get("kotlin.reflect", "KClass");

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

  private static final ImmutableSet<ClassName> FUTURE_TYPES =
      ImmutableSet.of(LISTENABLE_FUTURE, FLUENT_FUTURE);

  public static boolean isFutureType(XType type) {
    return isFutureType(type.getTypeName());
  }

  public static boolean isFutureType(TypeName typeName) {
    return FUTURE_TYPES.contains(rawTypeName(typeName));
  }

  /**
   * Returns the {@link TypeName} for the raw type of the given {@link TypeName}. If the argument
   * isn't a parameterized type, it returns the argument unchanged.
   */
  public static TypeName rawTypeName(TypeName typeName) {
    return (typeName instanceof ParameterizedTypeName)
        ? ((ParameterizedTypeName) typeName).rawType
        : typeName;
  }

  private TypeNames() {}
}
