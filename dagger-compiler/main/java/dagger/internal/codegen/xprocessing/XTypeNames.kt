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

import androidx.room3.compiler.codegen.XClassName
import androidx.room3.compiler.codegen.XTypeName
import androidx.room3.compiler.codegen.box
import androidx.room3.compiler.codegen.compat.XConverters.toJavaPoet
import androidx.room3.compiler.codegen.compat.XConverters.toKotlinPoet
import androidx.room3.compiler.codegen.compat.XConverters.toXPoet
import androidx.room3.compiler.processing.XType
import com.squareup.javapoet.ArrayTypeName as JArrayTypeName
import com.squareup.javapoet.ClassName as JClassName
import com.squareup.javapoet.ParameterizedTypeName as JParameterizedTypeName
import com.squareup.javapoet.TypeName as JTypeName
import com.squareup.javapoet.TypeVariableName as JTypeVariableName
import com.squareup.javapoet.WildcardTypeName as JWildcardTypeName
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName as KClassName
import com.squareup.kotlinpoet.ParameterizedTypeName as KParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName as KTypeName
import com.squareup.kotlinpoet.TypeVariableName as KTypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName as KWildcardTypeName

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
  @JvmField val DAGGER_GENERATED = XClassName.get("dagger.internal", "DaggerGenerated")
  @JvmField val DAGGER_CASTS = XClassName.get("dagger.internal", "DaggerCasts")
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
  @JvmField val DAGGER_PRECONDITIONS = XClassName.get("dagger.internal", "Preconditions")

  // TODO(b/404613325): Figure out what to do for calls like java.util.Collections.<T>emptyList()
  // where in Kotlin it becomes a top-level function like kotlin.collections.emptyList<T>().
  @JvmField val JAVA_UTIL_COLLECTIONS = XClassName.get("java.util", "Collections")
  @JvmField val JAVA_UTIL_SET = XClassName.get("java.util", "Set")
  @JvmField val JAVA_UTIL_MAP = XClassName.get("java.util", "Map")
  @JvmField val FACTORY = XClassName.get("dagger.internal", "Factory")
  @JvmField
  val INJECTED_FIELD_SIGNATURE = XClassName.get("dagger.internal", "InjectedFieldSignature")
  @JvmField val INSTANCE_FACTORY = XClassName.get("dagger.internal", "InstanceFactory")
  @JvmField val MAP_BUILDER = XClassName.get("dagger.internal", "MapBuilder")
  @JvmField val MAP_FACTORY = XClassName.get("dagger.internal", "MapFactory")
  @JvmField val MAP_PROVIDER_FACTORY = XClassName.get("dagger.internal", "MapProviderFactory")
  @JvmField val MEMBERS_INJECTOR = XClassName.get("dagger", "MembersInjector")
  @JvmField val MEMBERS_INJECTORS = XClassName.get("dagger.internal", "MembersInjectors")
  @JvmField val DAGGER_PROVIDER = XClassName.get("dagger.internal", "Provider")
  @JvmField val DAGGER_PROVIDERS = XClassName.get("dagger.internal", "Providers")
  @JvmField val PROVIDER_OF_LAZY = XClassName.get("dagger.internal", "ProviderOfLazy")
  @JvmField val SCOPE_METADATA = XClassName.get("dagger.internal", "ScopeMetadata")
  @JvmField val QUALIFIER_METADATA = XClassName.get("dagger.internal", "QualifierMetadata")
  @JvmField val SET_BUILDER = XClassName.get("dagger.internal", "SetBuilder")
  @JvmField val SET_FACTORY = XClassName.get("dagger.internal", "SetFactory")
  @JvmField val SINGLE_CHECK = XClassName.get("dagger.internal", "SingleCheck")
  @JvmField val WEAK = XClassName.get("dagger.internal", "Weak")
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
  @JvmField val ASSERTION_ERROR = XClassName.get("java.lang", "AssertionError")
  @JvmField val ERROR = XClassName.get("java.lang", "Error")
  @JvmField val EXCEPTION = XClassName.get("java.lang", "Exception")
  @JvmField val RUNTIME_EXCEPTION = XClassName.get("java.lang", "RuntimeException")
  @JvmField
  val UNSUPPORTED_OPERATION_EXCEPTION = XClassName.get("java.lang", "UnsupportedOperationException")
  @JvmField
  val DEPRECATED =
    toXPoet(JClassName.get("java.lang", "Deprecated"), KClassName("kotlin", "Deprecated"))
  @JvmField
  val CAN_IGNORE_RETURN_VALUE =
    XClassName.get("com.google.errorprone.annotations", "CanIgnoreReturnValue")

  @JvmField val KOTLIN_METADATA = XClassName.get("kotlin", "Metadata")
  @JvmField val IMMUTABLE_MAP = XClassName.get("com.google.common.collect", "ImmutableMap")
  @JvmField val IMMUTABLE_SET = XClassName.get("com.google.common.collect", "ImmutableSet")
  @JvmField
  val IMMUTABLE_SET_BUILDER = XClassName.get("com.google.common.collect", "ImmutableSet.Builder")
  @JvmField
  val MORE_EXECUTORS = XClassName.get("com.google.common.util.concurrent", "MoreExecutors")
  @JvmField val FUTURES = XClassName.get("com.google.common.util.concurrent", "Futures")
  @JvmField
  val LISTENABLE_FUTURE = XClassName.get("com.google.common.util.concurrent", "ListenableFuture")
  @JvmField val FLUENT_FUTURE = XClassName.get("com.google.common.util.concurrent", "FluentFuture")
  @JvmField val GUAVA_OPTIONAL = XClassName.get("com.google.common.base", "Optional")
  @JvmField val GUAVA_FUNCTION = XClassName.get("com.google.common.base", "Function")
  @JvmField val JDK_OPTIONAL = XClassName.get("java.util", "Optional")
  @JvmField val OVERRIDE = XClassName.get("java.lang", "Override")
  @JvmField val JVM_STATIC = XClassName.get("kotlin.jvm", "JvmStatic")
  @JvmField val CLASS = XClassName.get("java.lang", "Class")
  @JvmField val KCLASS = XClassName.get("kotlin.reflect", "KClass")
  @JvmField val UNIT_VOID_CLASS = XTypeName.UNIT_VOID.box()

  private val JAKARTA_SINGLETON = XClassName.get("jakarta.inject", "Singleton")
  private val JAKARTA_SCOPE = XClassName.get("jakarta.inject", "Scope")
  private val JAKARTA_INJECT = XClassName.get("jakarta.inject", "Inject")
  private val JAKARTA_QUALIFIER = XClassName.get("jakarta.inject", "Qualifier")
  private val JAKARTA_PROVIDER = XClassName.get("jakarta.inject", "Provider")
  private val JAVAX_SINGLETON = XClassName.get("javax.inject", "Singleton")
  private val JAVAX_SCOPE = XClassName.get("javax.inject", "Scope")
  private val JAVAX_INJECT = XClassName.get("javax.inject", "Inject")
  private val JAVAX_QUALIFIER = XClassName.get("javax.inject", "Qualifier")
  // This is public since a number of places still require explicit usages of javax.inject.Provider.
  // However, prefer to use the providerTypeNames() set below when possible.
  @JvmField val JAVAX_PROVIDER = XClassName.get("javax.inject", "Provider")

  @JvmStatic fun singletonTypeNames() = setOf(JAVAX_SINGLETON, JAKARTA_SINGLETON)

  @JvmStatic fun scopeTypeNames() = setOf(JAVAX_SCOPE, JAKARTA_SCOPE)

  @JvmStatic fun injectTypeNames() = setOf(JAVAX_INJECT, JAKARTA_INJECT)

  @JvmStatic fun qualifierTypeNames() = setOf(JAVAX_QUALIFIER, JAKARTA_QUALIFIER)

  @JvmStatic fun providerTypeNames() = setOf(JAVAX_PROVIDER, JAKARTA_PROVIDER)

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
  fun javaxProviderOf(typeName: XTypeName): XTypeName {
    return JAVAX_PROVIDER.parametrizedBy(typeName)
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

  /** Returns `true` if the raw type is equal to the given `className`. */
  @JvmStatic fun XTypeName.isTypeOf(className: XClassName) = this.rawTypeName == className

  /** Returns `true` if the raw type is equal to any of the given `classNames`. */
  @JvmStatic
  fun XTypeName.isTypeOf(classNames: Collection<XClassName>) = classNames.any { isTypeOf(it) }

  /**
   * Returns the [XTypeName] as an [XClassName].
   *
   * This should only be used if you know the JavaPoet and KotlinPoet representations are class
   * names. Otherwise, a [ClassCastException] is thrown.
   */
  @JvmStatic
  fun XTypeName.asClassName(): XClassName {
    // TODO(b/427261839): Normally, we would just cast to XClassName, but that currently doesn't
    // work in XProcessing due to b/427261839. Instead, we cast the JavaPoet and KotlinPoet
    // representations separately and create a new XClassName from the result.
    return toXPoet(toJavaPoet() as JClassName, toKotlinPoet() as KClassName)
  }

  /**
   * Returns the {@link JTypeName} for the raw type of the given {@link JTypeName}. If the argument
   * isn't a parameterized type, it returns the argument unchanged.
   */
  // TODO(bcorso): Take in an XTypeName once we can check for XParameterizedTypeName in XPoet.
  @JvmStatic
  fun rawJavaTypeName(typeName: JTypeName): JTypeName {
    return if (typeName is JParameterizedTypeName) {
      typeName.rawType
    } else {
      typeName
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

  /** The default [KTypeName] returned by xprocessing APIs when the backend is not KSP. */
  internal val UNAVAILABLE_KTYPE_NAME = KClassName("androidx.room3.compiler.codegen", "Unavailable")

  @JvmStatic fun XTypeName.unwrap(): XTypeName = getParameterizedTypeArgument(0)

  // TODO(b/448510944): We shouldn't need to reach into the JavaPoet/KotlinPoet internals once this
  // bug is fixed.
  @JvmStatic
  fun XTypeName.getParameterizedTypeArgument(index: Int): XTypeName {
    return toXPoet(
      jTypeName =
        when (val jTypeName = toJavaPoet()) {
          is JParameterizedTypeName -> jTypeName.typeArguments[index]
          else -> error("Expected a parameterized type name but got $jTypeName")
        },
      kTypeName =
        when (val kTypeName = toKotlinPoet()) {
          UNAVAILABLE_KTYPE_NAME -> kTypeName
          is KParameterizedTypeName -> kTypeName.typeArguments[index]
          else -> error("Expected a parameterized type name but got $kTypeName")
        },
    )
  }

  @JvmStatic
  fun XTypeName.boundsHasSelfReference(): Boolean =
    // The result should be the same for both JavaPoet and KotlinPoet, so we only need to check one.
    toJavaPoet().boundsHasSelfReference(mutableMapOf())

  private fun JTypeName.boundsHasSelfReference(
    typeParametersMap: MutableMap<JTypeName, Boolean>
  ): Boolean {
    if (isPrimitive) {
      return false
    }
    return when (this) {
      is JClassName -> false
      is JParameterizedTypeName ->
        typeArguments.any { it.boundsHasSelfReference(typeParametersMap) }
      is JWildcardTypeName ->
        if (!lowerBounds.isEmpty()) {
          lowerBounds.first().boundsHasSelfReference(typeParametersMap)
        } else {
          upperBounds.first().boundsHasSelfReference(typeParametersMap)
        }
      is JArrayTypeName -> componentType.boundsHasSelfReference(typeParametersMap)
      is JTypeVariableName -> {
        if (bounds.isEmpty()) {
          return false
        }
        if (!typeParametersMap.contains(this)) {
          // Initialize the map to true so that we can catch cycles before a real value is assigned.
          typeParametersMap.put(this, true)
          typeParametersMap.put(this, bounds.any { it.boundsHasSelfReference(typeParametersMap) })
        }
        typeParametersMap.get(this)!!
      }
      else -> error("Unexpected type name: $this")
    }
  }

  @JvmStatic
  fun XTypeName.replaceTypeVariablesWithBounds() =
    toXPoet(
      toJavaPoet().replaceTypeVariablesWithBounds(mutableMapOf()),
      toKotlinPoet().replaceTypeVariablesWithBounds(mutableMapOf()),
    )

  private val JCYCLIC_BOUNDS_SENTINEL = JClassName.get("", "JCYCLIC_BOUNDS_SENTINEL")

  private fun JTypeName.replaceTypeVariablesWithBounds(
    typeParametersMap: MutableMap<JTypeName, JTypeName?>
  ): JTypeName {
    if (isPrimitive) {
      return this
    }
    return when (this) {
      is JClassName -> this
      is JParameterizedTypeName ->
        JParameterizedTypeName.get(
          rawType,
          *typeArguments.map { it.replaceTypeVariablesWithBounds(typeParametersMap) }.toTypedArray(),
        )
      is JWildcardTypeName ->
        if (!lowerBounds.isEmpty()) {
          JWildcardTypeName.supertypeOf(
            lowerBounds.first().replaceTypeVariablesWithBounds(typeParametersMap)
          )
        } else {
          JWildcardTypeName.subtypeOf(
            upperBounds.first().replaceTypeVariablesWithBounds(typeParametersMap)
          )
        }
      is JArrayTypeName ->
        JArrayTypeName.of(componentType.replaceTypeVariablesWithBounds(typeParametersMap))
      is JTypeVariableName -> {
        if (bounds.isEmpty()) {
          return JTypeName.OBJECT
        }
        if (!typeParametersMap.contains(this)) {
          // Initialize the map to a sentinel to catch cycles before a real value is assigned.
          typeParametersMap.put(this, JCYCLIC_BOUNDS_SENTINEL)
          check(bounds.size == 1) {
            "Cannot replace type variables with intersection bounds: $this"
          }
          val bound = bounds.first().replaceTypeVariablesWithBounds(typeParametersMap)
          typeParametersMap.put(this, bound)
        }
        check(typeParametersMap.get(this) != JCYCLIC_BOUNDS_SENTINEL) {
          "Cannot replace type variables with cyclic bounds: $this"
        }
        return typeParametersMap.get(this)!!
      }
      else -> error("Unexpected type name: $this")
    }
  }

  private val KCYCLIC_BOUNDS_SENTINEL = KClassName("", "KCYCLIC_BOUNDS_SENTINEL")

  private fun KTypeName.replaceTypeVariablesWithBounds(
    typeParametersMap: MutableMap<KTypeName, KTypeName>
  ): KTypeName {
    return when (this) {
      is KClassName -> this
      is KParameterizedTypeName ->
        rawType.parameterizedBy(
          *typeArguments.map { it.replaceTypeVariablesWithBounds(typeParametersMap) }.toTypedArray()
        )
      is KWildcardTypeName ->
        if (!inTypes.isEmpty()) {
          KWildcardTypeName.consumerOf(
            inTypes.first().replaceTypeVariablesWithBounds(typeParametersMap)
          )
        } else {
          KWildcardTypeName.producerOf(
            outTypes.first().replaceTypeVariablesWithBounds(typeParametersMap)
          )
        }
      is KTypeVariableName -> {
        if (bounds.isEmpty()) {
          return ANY.copy(nullable = true)
        }
        if (!typeParametersMap.contains(this)) {
          // Initialize the map to a sentinel to catch cycles before a real value is assigned.
          typeParametersMap.put(this, KCYCLIC_BOUNDS_SENTINEL)
          check(bounds.size == 1) {
            "Cannot replace type variables with intersection bounds: $this"
          }
          val bound = bounds.first().replaceTypeVariablesWithBounds(typeParametersMap)
          typeParametersMap.put(this, bound)
        }
        check(typeParametersMap.get(this) != KCYCLIC_BOUNDS_SENTINEL) {
          "Cannot replace type variables with cyclic bounds: $this"
        }
        return typeParametersMap.get(this)!!
      }
      else -> error("Unexpected type name: $this")
    }
  }

  // TODO(bcorso): Remove this workaround once @JvmStatic is added to XTypeName#getTypeVariableName
  @JvmStatic fun getTypeVariableName(name: String) = XTypeName.getTypeVariableName(name)
}
