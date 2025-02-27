/*
 * Copyright (C) 2024 The Dagger Authors.
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

package dagger.internal.codegen.xprocessing

import androidx.room.compiler.codegen.XClassName
import androidx.room.compiler.codegen.box
import androidx.room.compiler.codegen.XTypeName
import androidx.room.compiler.codegen.compat.XConverters.toXPoet
import androidx.room.compiler.codegen.toJavaPoet
import androidx.room.compiler.codegen.toKotlinPoet
import androidx.room.compiler.processing.XType
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName

/** Common names and convenience methods for XPoet {@link XTypeName} usage. */
@OptIn(com.squareup.kotlinpoet.javapoet.KotlinPoetJavaPoetPreview::class)
object XTypeNames {

  // Dagger Core classnames
  @JvmField val ASSISTED = XClassName.get("dagger.assisted", "Assisted")
  @JvmField val ASSISTED_FACTORY = XClassName.get("dagger.assisted", "AssistedFactory")
  @JvmField val ASSISTED_INJECT = XClassName.get("dagger.assisted", "AssistedInject")
  @JvmField val BINDS = XClassName.get("dagger", "Binds")
  @JvmField val BINDS_INSTANCE = XClassName.get("dagger", "BindsInstance")
  @JvmField val BINDS_OPTIONAL_OF = XClassName.get("dagger", "BindsOptionalOf")
  @JvmField val COMPONENT = XClassName.get("dagger", "Component")
  @JvmField val COMPONENT_BUILDER = XClassName.get("dagger", "Component", "Builder")
  @JvmField val COMPONENT_FACTORY = XClassName.get("dagger", "Component", "Factory")
  @JvmField val DAGGER_PROCESSING_OPTIONS = XClassName.get("dagger", "DaggerProcessingOptions")
  @JvmField val ELEMENTS_INTO_SET = XClassName.get("dagger.multibindings", "ElementsIntoSet")
  @JvmField val INTO_MAP = XClassName.get("dagger.multibindings", "IntoMap")
  @JvmField val INTO_SET = XClassName.get("dagger.multibindings", "IntoSet")
  @JvmField val MAP_KEY = XClassName.get("dagger", "MapKey")
  @JvmField val MODULE = XClassName.get("dagger", "Module")
  @JvmField val MULTIBINDS = XClassName.get("dagger.multibindings", "Multibinds")
  @JvmField val PROVIDES = XClassName.get("dagger", "Provides")
  @JvmField val REUSABLE = XClassName.get("dagger", "Reusable")
  @JvmField val SUBCOMPONENT = XClassName.get("dagger", "Subcomponent")
  @JvmField val SUBCOMPONENT_BUILDER = XClassName.get("dagger", "Subcomponent", "Builder")
  @JvmField val SUBCOMPONENT_FACTORY = XClassName.get("dagger", "Subcomponent", "Factory")

  // Dagger Internal classnames
  @JvmField val IDENTIFIER_NAME_STRING = XClassName.get("dagger.internal", "IdentifierNameString")
  @JvmField val KEEP_FIELD_TYPE = XClassName.get("dagger.internal", "KeepFieldType")
  @JvmField val LAZY_CLASS_KEY = XClassName.get("dagger.multibindings", "LazyClassKey")
  @JvmField val LAZY_CLASS_KEY_MAP = XClassName.get("dagger.internal", "LazyClassKeyMap")
  @JvmField
  val LAZY_CLASS_KEY_MAP_FACTORY =
    XClassName.get("dagger.internal", "LazyClassKeyMap", "MapFactory")
  @JvmField
  val LAZY_CLASS_KEY_MAP_PROVIDER_FACTORY =
    XClassName.get("dagger.internal", "LazyClassKeyMap", "MapProviderFactory")
  @JvmField
  val LAZY_MAP_OF_PRODUCED_PRODUCER =
    XClassName.get("dagger.producers.internal", "LazyMapOfProducedProducer")
  @JvmField
  val LAZY_MAP_OF_PRODUCER_PRODUCER =
    XClassName.get("dagger.producers.internal", "LazyMapOfProducerProducer")
  @JvmField val LAZY_MAP_PRODUCER = XClassName.get("dagger.producers.internal", "LazyMapProducer")

  @JvmField val DELEGATE_FACTORY = XClassName.get("dagger.internal", "DelegateFactory")
  @JvmField val DOUBLE_CHECK = XClassName.get("dagger.internal", "DoubleCheck")

  @JvmField val FACTORY = XClassName.get("dagger.internal", "Factory")
  @JvmField
  val INJECTED_FIELD_SIGNATURE = XClassName.get("dagger.internal", "InjectedFieldSignature")
  @JvmField val INSTANCE_FACTORY = XClassName.get("dagger.internal", "InstanceFactory")
  @JvmField val MAP_BUILDER = XClassName.get("dagger.internal", "MapBuilder")
  @JvmField val MAP_FACTORY = XClassName.get("dagger.internal", "MapFactory")
  @JvmField val MAP_PROVIDER_FACTORY = XClassName.get("dagger.internal", "MapProviderFactory")
  @JvmField val MEMBERS_INJECTOR = XClassName.get("dagger", "MembersInjector")
  @JvmField val MEMBERS_INJECTORS = XClassName.get("dagger.internal", "MembersInjectors")
  @JvmField val PROVIDER = XClassName.get("javax.inject", "Provider")
  @JvmField val JAKARTA_PROVIDER = XClassName.get("jakarta.inject", "Provider")
  @JvmField val DAGGER_PROVIDER = XClassName.get("dagger.internal", "Provider")
  @JvmField val DAGGER_PROVIDERS = XClassName.get("dagger.internal", "Providers")
  @JvmField val PROVIDER_OF_LAZY = XClassName.get("dagger.internal", "ProviderOfLazy")
  @JvmField val SCOPE_METADATA = XClassName.get("dagger.internal", "ScopeMetadata")
  @JvmField val QUALIFIER_METADATA = XClassName.get("dagger.internal", "QualifierMetadata")
  @JvmField val SET_FACTORY = XClassName.get("dagger.internal", "SetFactory")
  @JvmField val SINGLE_CHECK = XClassName.get("dagger.internal", "SingleCheck")
  @JvmField val LAZY = XClassName.get("dagger", "Lazy")

  // Dagger Producers classnames
  @JvmField val ABSTRACT_PRODUCER = XClassName.get("dagger.producers.internal", "AbstractProducer")
  @JvmField
  val ABSTRACT_PRODUCES_METHOD_PRODUCER =
    XClassName.get("dagger.producers.internal", "AbstractProducesMethodProducer")
  @JvmField
  val CANCELLATION_LISTENER = XClassName.get("dagger.producers.internal", "CancellationListener")
  @JvmField val CANCELLATION_POLICY = XClassName.get("dagger.producers", "CancellationPolicy")
  @JvmField val DELEGATE_PRODUCER = XClassName.get("dagger.producers.internal", "DelegateProducer")
  @JvmField
  val DEPENDENCY_METHOD_PRODUCER =
    XClassName.get("dagger.producers.internal", "DependencyMethodProducer")
  @JvmField
  val MAP_OF_PRODUCED_PRODUCER =
    XClassName.get("dagger.producers.internal", "MapOfProducedProducer")
  @JvmField
  val MAP_OF_PRODUCER_PRODUCER =
    XClassName.get("dagger.producers.internal", "MapOfProducerProducer")
  @JvmField val MAP_PRODUCER = XClassName.get("dagger.producers.internal", "MapProducer")
  @JvmField val MONITORS = XClassName.get("dagger.producers.monitoring.internal", "Monitors")
  @JvmField val PRODUCED = XClassName.get("dagger.producers", "Produced")
  @JvmField val PRODUCER = XClassName.get("dagger.producers", "Producer")
  @JvmField val PRODUCERS = XClassName.get("dagger.producers.internal", "Producers")
  @JvmField val PRODUCER_MODULE = XClassName.get("dagger.producers", "ProducerModule")
  @JvmField val PRODUCES = XClassName.get("dagger.producers", "Produces")
  @JvmField val PRODUCTION = XClassName.get("dagger.producers", "Production")
  @JvmField val PRODUCTION_COMPONENT = XClassName.get("dagger.producers", "ProductionComponent")
  @JvmField
  val PRODUCTION_COMPONENT_BUILDER =
    XClassName.get("dagger.producers", "ProductionComponent", "Builder")
  @JvmField
  val PRODUCTION_COMPONENT_FACTORY =
    XClassName.get("dagger.producers", "ProductionComponent", "Factory")
  @JvmField
  val PRODUCTION_EXECTUTOR_MODULE =
    XClassName.get("dagger.producers.internal", "ProductionExecutorModule")
  @JvmField
  val PRODUCTION_IMPLEMENTATION =
    XClassName.get("dagger.producers.internal", "ProductionImplementation")
  @JvmField
  val PRODUCTION_SUBCOMPONENT = XClassName.get("dagger.producers", "ProductionSubcomponent")
  @JvmField
  val PRODUCTION_SUBCOMPONENT_BUILDER =
    XClassName.get("dagger.producers", "ProductionSubcomponent", "Builder")
  @JvmField
  val PRODUCTION_SUBCOMPONENT_FACTORY =
    XClassName.get("dagger.producers", "ProductionSubcomponent", "Factory")
  @JvmField val PRODUCER_TOKEN = XClassName.get("dagger.producers.monitoring", "ProducerToken")
  @JvmField
  val PRODUCTION_COMPONENT_MONITOR =
    XClassName.get("dagger.producers.monitoring", "ProductionComponentMonitor")
  @JvmField
  val PRODUCTION_COMPONENT_MONITOR_FACTORY =
    XClassName.get("dagger.producers.monitoring", "ProductionComponentMonitor", "Factory")
  @JvmField
  val SET_OF_PRODUCED_PRODUCER =
    XClassName.get("dagger.producers.internal", "SetOfProducedProducer")
  @JvmField val SET_PRODUCER = XClassName.get("dagger.producers.internal", "SetProducer")
  @JvmField val PRODUCTION_SCOPE = XClassName.get("dagger.producers", "ProductionScope")

  // Other classnames
  @JvmField val EXECUTOR = XClassName.get("java.util.concurrent", "Executor")
  @JvmField val ERROR = XClassName.get("java.lang", "Error")
  @JvmField val EXCEPTION = XClassName.get("java.lang", "Exception")
  @JvmField val RUNTIME_EXCEPTION = XClassName.get("java.lang", "RuntimeException")

  @JvmField val KOTLIN_METADATA = XClassName.get("kotlin", "Metadata")
  @JvmField val IMMUTABLE_MAP = XClassName.get("com.google.common.collect", "ImmutableMap")
  @JvmField val SINGLETON = XClassName.get("jakarta.inject", "Singleton")
  @JvmField val SINGLETON_JAVAX = XClassName.get("javax.inject", "Singleton")
  @JvmField val SCOPE = XClassName.get("jakarta.inject", "Scope")
  @JvmField val SCOPE_JAVAX = XClassName.get("javax.inject", "Scope")
  @JvmField val INJECT = XClassName.get("jakarta.inject", "Inject")
  @JvmField val INJECT_JAVAX = XClassName.get("javax.inject", "Inject")
  @JvmField val QUALIFIER = XClassName.get("jakarta.inject", "Qualifier")
  @JvmField val QUALIFIER_JAVAX = XClassName.get("javax.inject", "Qualifier")
  @JvmField val IMMUTABLE_SET = XClassName.get("com.google.common.collect", "ImmutableSet")
  @JvmField val FUTURES = XClassName.get("com.google.common.util.concurrent", "Futures")
  @JvmField
  val LISTENABLE_FUTURE = XClassName.get("com.google.common.util.concurrent", "ListenableFuture")
  @JvmField val FLUENT_FUTURE = XClassName.get("com.google.common.util.concurrent", "FluentFuture")
  @JvmField val GUAVA_OPTIONAL = XClassName.get("com.google.common.base", "Optional")
  @JvmField val GUAVA_FUNCTION = XClassName.get("com.google.common.base", "Function")
  @JvmField val JDK_OPTIONAL = XClassName.get("java.util", "Optional")
  @JvmField val OVERRIDE = XClassName.get("java.lang", "Override")
  @JvmField val JVM_STATIC = XClassName.get("kotlin.jvm", "JvmField")
  @JvmField val CLASS = XClassName.get("java.lang", "Class")
  @JvmField val KCLASS = XClassName.get("kotlin.reflect", "KClass")
  @JvmField val UNIT_VOID_CLASS = XTypeName.UNIT_VOID.box()

  @JvmStatic
  fun abstractProducerOf(typeName: XTypeName): XTypeName {
    return ABSTRACT_PRODUCER.parametrizedBy(typeName)
  }

  @JvmStatic
  fun factoryOf(factoryType: XTypeName): XTypeName {
    return FACTORY.parametrizedBy(factoryType)
  }

  @JvmStatic
  fun lazyOf(typeName: XTypeName): XTypeName {
    return LAZY.parametrizedBy(typeName)
  }

  @JvmStatic
  fun listOf(typeName: XTypeName): XTypeName {
    return XTypeName.LIST.parametrizedBy(typeName)
  }

  @JvmStatic
  fun listenableFutureOf(typeName: XTypeName): XTypeName {
    return LISTENABLE_FUTURE.parametrizedBy(typeName)
  }

  @JvmStatic
  fun membersInjectorOf(membersInjectorType: XTypeName): XTypeName {
    return MEMBERS_INJECTOR.parametrizedBy(membersInjectorType)
  }

  @JvmStatic
  fun producedOf(typeName: XTypeName): XTypeName {
    return PRODUCED.parametrizedBy(typeName)
  }

  @JvmStatic
  fun producerOf(typeName: XTypeName): XTypeName {
    return PRODUCER.parametrizedBy(typeName)
  }

  @JvmStatic
  fun dependencyMethodProducerOf(typeName: XTypeName): XTypeName {
    return DEPENDENCY_METHOD_PRODUCER.parametrizedBy(typeName)
  }

  @JvmStatic
  fun providerOf(typeName: XTypeName): XTypeName {
    return PROVIDER.parametrizedBy(typeName)
  }

  @JvmStatic
  fun daggerProviderOf(typeName: XTypeName): XTypeName {
    return DAGGER_PROVIDER.parametrizedBy(typeName)
  }

  @JvmStatic
  fun setOf(elementType: XTypeName): XTypeName {
    return XTypeName.SET.parametrizedBy(elementType)
  }

  private val FUTURE_TYPES = setOf(LISTENABLE_FUTURE, FLUENT_FUTURE)

  @JvmStatic
  fun isFutureType(type: XType): Boolean {
    return isFutureType(type.asTypeName())
  }

  @JvmStatic
  fun isFutureType(typeName: XTypeName): Boolean {
    return FUTURE_TYPES.contains(typeName.rawTypeName)
  }

  /**
   * Returns the {@link TypeName} for the raw type of the given {@link TypeName}. If the argument
   * isn't a parameterized type, it returns the argument unchanged.
   */
  // TODO(bcorso): Take in an XTypeName once we can check for XParameterizedTypeName in XPoet.
  @JvmStatic
  fun rawJavaTypeName(typeName: TypeName): TypeName {
    return if (typeName is ParameterizedTypeName) {
      typeName.rawType
    } else {
      typeName
    }
  }

  @JvmStatic
  fun XTypeName.withTypeNullability(nullability: Nullability): XTypeName {
    return if (nullability.typeUseNullableAnnotations().isEmpty()) {
      this
    } else {
      toXPoet(
          this.toJavaPoet().withTypeNullability(nullability),
          // TODO(bcorso): Check if we need to add nullability to the Kotlin type.
          this.toKotlinPoet()
      )
    }
  }

  @JvmStatic
  fun TypeName.withTypeNullability(nullability: Nullability): TypeName {
    return if (nullability.typeUseNullableAnnotations().isEmpty()) {
      this
    } else {
      this.annotated(
          nullability.typeUseNullableAnnotations()
              .map { AnnotationSpec.builder(it.toJavaPoet()).build() }
      )
    }
  }

  @JvmStatic
  fun enclosingClassName(className: XClassName): XClassName? {
    return if (className.simpleNames.size > 1) {
      XClassName.get(className.packageName, *className.simpleNames.dropLast(1).toTypedArray())
    } else {
      null
    }
  }

  // TODO(bcorso): Remove this workaround once @JvmStatic is added to XTypeName#getTypeVariableName
  @JvmStatic
  fun getTypeVariableName(name: String) = XTypeName.getTypeVariableName(name)
}
