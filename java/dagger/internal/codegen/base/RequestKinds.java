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
import static dagger.internal.codegen.javapoet.TypeNames.lazyOf;
import static dagger.internal.codegen.javapoet.TypeNames.listenableFutureOf;
import static dagger.internal.codegen.javapoet.TypeNames.producedOf;
import static dagger.internal.codegen.javapoet.TypeNames.producerOf;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static dagger.internal.codegen.xprocessing.XProcessingEnvs.wrapType;
import static dagger.internal.codegen.xprocessing.XTypes.checkTypePresent;
import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeOf;
import static dagger.internal.codegen.xprocessing.XTypes.unwrapType;
import static dagger.spi.model.RequestKind.LAZY;
import static dagger.spi.model.RequestKind.PRODUCED;
import static dagger.spi.model.RequestKind.PRODUCER;
import static dagger.spi.model.RequestKind.PROVIDER;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.RequestKind;

/** Utility methods for {@link RequestKind}s. */
public final class RequestKinds {
  /** Returns the type of a request of this kind for a key with a given type. */
  public static XType requestType(
      RequestKind requestKind, XType type, XProcessingEnv processingEnv) {
    switch (requestKind) {
      case INSTANCE:
        return type;

      case PROVIDER_OF_LAZY:
        return wrapType(TypeNames.PROVIDER, requestType(LAZY, type, processingEnv), processingEnv);

      case FUTURE:
        return wrapType(TypeNames.LISTENABLE_FUTURE, type, processingEnv);

      default:
        return wrapType(frameworkClassName(requestKind), type, processingEnv);
    }
  }

  /** Returns the type of a request of this kind for a key with a given type. */
  public static TypeName requestTypeName(RequestKind requestKind, TypeName keyType) {
    switch (requestKind) {
      case INSTANCE:
        return keyType;

      case PROVIDER:
        return providerOf(keyType);

      case LAZY:
        return lazyOf(keyType);

      case PROVIDER_OF_LAZY:
        return providerOf(lazyOf(keyType));

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

  private static final ImmutableMap<RequestKind, ClassName> FRAMEWORK_CLASSES =
      ImmutableMap.of(
          PROVIDER, TypeNames.PROVIDER,
          LAZY, TypeNames.LAZY,
          PRODUCER, TypeNames.PRODUCER,
          PRODUCED, TypeNames.PRODUCED);

  /** Returns the {@link RequestKind} that matches the wrapping types (if any) of {@code type}. */
  public static RequestKind getRequestKind(XType type) {
    checkTypePresent(type);
    if (!isDeclared(type) || type.getTypeArguments().isEmpty()) {
      // If the type is not a declared type (i.e. class or interface) with type arguments, then we
      // know it can't be a parameterized type of one of the framework classes, so return INSTANCE.
      return RequestKind.INSTANCE;
    }

    if (isTypeOf(type, TypeNames.PROVIDER) && isTypeOf(unwrapType(type), TypeNames.LAZY)) {
      return RequestKind.PROVIDER_OF_LAZY;
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
   * another type but share the same {@link dagger.spi.model.Key}.
   *
   * <p>For example, {@code Provider<String>} and {@code Lazy<String>} can both be requested if a
   * key exists for {@code String}; they all share the same key.
   *
   * <p>This concept is not well defined and should probably be removed and inlined into the cases
   * that need it. For example, {@link RequestKind#PROVIDER_OF_LAZY} has <em>2</em> wrapping
   * classes, and {@link RequestKind#FUTURE} is wrapped with a {@link ListenableFuture}, but for
   * historical/implementation reasons has not had an associated framework class.
   */
  public static ClassName frameworkClassName(RequestKind requestKind) {
    checkArgument(
        FRAMEWORK_CLASSES.containsKey(requestKind), "no framework class for %s", requestKind);
    return FRAMEWORK_CLASSES.get(requestKind);
  }

  /**
   * Returns {@code true} if requests for {@code requestKind} can be satisfied by a production
   * binding.
   */
  public static boolean canBeSatisfiedByProductionBinding(RequestKind requestKind) {
    switch (requestKind) {
      case INSTANCE:
      case PROVIDER:
      case LAZY:
      case PROVIDER_OF_LAZY:
      case MEMBERS_INJECTION:
        return false;
      case PRODUCER:
      case PRODUCED:
      case FUTURE:
        return true;
    }
    throw new AssertionError();
  }

  private RequestKinds() {}
}
