/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal.codegen.base;

import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.extension.DaggerCollectors.toOptional;
import static dagger.internal.codegen.model.RequestKind.LAZY;
import static dagger.internal.codegen.model.RequestKind.PRODUCED;
import static dagger.internal.codegen.model.RequestKind.PRODUCER;
import static dagger.internal.codegen.model.RequestKind.PROVIDER;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.wrapType;
import static dagger.internal.codegen.xprocessing.XTypeNames.javaxProviderOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.lazyOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.listenableFutureOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.producedOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.producerOf;
import static dagger.internal.codegen.xprocessing.XTypeNames.providerTypeNames;
import static dagger.internal.codegen.xprocessing.XTypes.checkTypePresent;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableMap;
import dagger.internal.codegen.model.Binding;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraph.ComponentNode;
import dagger.internal.codegen.model.BindingGraph.DependencyEdge;
import dagger.internal.codegen.model.BindingGraph.Node;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.XTypeNames;

/** Utility methods for {@link RequestKind}s. */
public final class RequestKinds {
  /** Returns the type of a request of this kind for a key with a given type. */
  public static XType requestType(
      RequestKind requestKind, XType type, XProcessingEnv processingEnv) {
    switch (requestKind) {
      case INSTANCE:
        return type;

      case PROVIDER_OF_LAZY:
        return wrapType(
            XTypeNames.JAVAX_PROVIDER, requestType(LAZY, type, processingEnv), processingEnv);

      case FUTURE:
        return wrapType(XTypeNames.LISTENABLE_FUTURE, type, processingEnv);

      default:
        return wrapType(frameworkClassName(requestKind), type, processingEnv);
    }
  }

  /** Returns the type of a request of this kind for a key with a given type. */
  public static XTypeName requestTypeName(RequestKind requestKind, XTypeName keyType) {
    switch (requestKind) {
      case INSTANCE:
        return keyType;

      case PROVIDER:
        return javaxProviderOf(keyType);

      case LAZY:
        return lazyOf(keyType);

      case PROVIDER_OF_LAZY:
        return javaxProviderOf(lazyOf(keyType));

      case PRODUCER:
        return producerOf(keyType);

      case PRODUCED:
        return producedOf(keyType);

      case FUTURE:
        return listenableFutureOf(keyType);

      default:
        throw new AssertionError(requestKind);
    }
  }

  private static final ImmutableMap<RequestKind, XClassName> FRAMEWORK_CLASSES =
      ImmutableMap.of(
          // Default to the javax Provider since that is what is used for the binding graph
          // representation.
          PROVIDER, XTypeNames.JAVAX_PROVIDER,
          LAZY, XTypeNames.LAZY,
          PRODUCER, XTypeNames.PRODUCER,
          PRODUCED, XTypeNames.PRODUCED);

  /** Returns the {@link RequestKind} that matches the wrapping types (if any) of {@code type}. */
  public static RequestKind getRequestKind(XType type) {
    checkTypePresent(type);
    if (!isDeclared(type) || type.getTypeArguments().isEmpty()) {
      // If the type is not a declared type (i.e. class or interface) with type arguments, then we
      // know it can't be a parameterized type of one of the framework classes, so return INSTANCE.
      return RequestKind.INSTANCE;
    }

    // The Jakarta Provider won't be matched on the check via framework classes so look for
    // Provider types here. Similarly, Provider<Lazy<>> will not be correctly matched either so
    // explicitly look for it here as well.
    if (isTypeOf(type, providerTypeNames())) {
      return isTypeOf(unwrapType(type), XTypeNames.LAZY)
          ? RequestKind.PROVIDER_OF_LAZY
          : RequestKind.PROVIDER;
    }

    return FRAMEWORK_CLASSES.keySet().stream()
        .filter(kind -> isTypeOf(type, FRAMEWORK_CLASSES.get(kind)))
        .collect(toOptional())
        .orElse(RequestKind.INSTANCE);
  }

  /**
   * Unwraps the framework class(es) of {@code requestKind} from {@code type}. If {@code
   * requestKind} is {@link RequestKind#INSTANCE}, this acts as an identity function.
   *
   * @throws TypeNotPresentException if {@code type} is an {@link javax.lang.model.type.ErrorType},
   *     which may mean that the type will be generated in a later round of processing
   * @throws IllegalArgumentException if {@code type} is not wrapped with {@code requestKind}'s
   *     framework class(es).
   */
  public static XType extractKeyType(XType type) {
    return extractKeyType(getRequestKind(type), type);
  }

  private static XType extractKeyType(RequestKind requestKind, XType type) {
    switch (requestKind) {
      case INSTANCE:
        return type;
      case PROVIDER_OF_LAZY:
        return extractKeyType(LAZY, extractKeyType(PROVIDER, type));
      default:
        return unwrapType(type);
    }
  }

  /**
   * A dagger- or {@code javax.inject}-defined class for {@code requestKind} that that can wrap
   * another type but share the same {@link dagger.internal.codegen.model.Key}.
   *
   * <p>For example, {@code Provider<String>} and {@code Lazy<String>} can both be requested if a
   * key exists for {@code String}; they all share the same key.
   *
   * <p>This concept is not well defined and should probably be removed and inlined into the cases
   * that need it. For example, {@link RequestKind#PROVIDER_OF_LAZY} has <em>2</em> wrapping
   * classes, and {@link RequestKind#FUTURE} is wrapped with a {@link ListenableFuture}, but for
   * historical/implementation reasons has not had an associated framework class.
   */
  public static XClassName frameworkClassName(RequestKind requestKind) {
    checkArgument(
        FRAMEWORK_CLASSES.containsKey(requestKind), "no framework class for %s", requestKind);
    return FRAMEWORK_CLASSES.get(requestKind);
  }

  /**
   * Returns {@code true} if requests for {@code requestKind} can be satisfied by a production
   * binding.
   */
  public static boolean canBeSatisfiedByProductionBinding(
      RequestKind requestKind, boolean isEntryPoint) {
    switch (requestKind) {
      case PROVIDER:
      case LAZY:
      case PROVIDER_OF_LAZY:
      case MEMBERS_INJECTION:
        return false;
      case PRODUCED: // TODO(b/337087142) Requires implementation for entry point.
      case INSTANCE:
        return !isEntryPoint;
      case PRODUCER:
      case FUTURE:
        return true;
    }
    throw new AssertionError();
  }

  public static boolean dependencyCanBeProduction(DependencyEdge edge, BindingGraph graph) {
    Node source = graph.network().incidentNodes(edge).source();
    boolean isEntryPoint = source instanceof ComponentNode;
    boolean isValidRequest =
        canBeSatisfiedByProductionBinding(edge.dependencyRequest().kind(), isEntryPoint);
    if (isEntryPoint) {
      return isValidRequest;
    }
    if (source instanceof Binding) {
      return isValidRequest && ((Binding) source).isProduction();
    }
    throw new IllegalArgumentException(
        "expected a dagger.internal.codegen.model.Binding or ComponentNode: " + source);
  }

  private RequestKinds() {}
}
