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

package dagger.internal.codegen;

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.auto.common.MoreTypes.isType;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.javapoet.TypeNames.lazyOf;
import static dagger.internal.codegen.javapoet.TypeNames.listenableFutureOf;
import static dagger.internal.codegen.javapoet.TypeNames.producedOf;
import static dagger.internal.codegen.javapoet.TypeNames.producerOf;
import static dagger.internal.codegen.javapoet.TypeNames.providerOf;
import static dagger.internal.codegen.langmodel.DaggerTypes.checkTypePresent;
import static dagger.model.RequestKind.INSTANCE;
import static dagger.model.RequestKind.LAZY;
import static dagger.model.RequestKind.PRODUCED;
import static dagger.model.RequestKind.PRODUCER;
import static dagger.model.RequestKind.PROVIDER;
import static dagger.model.RequestKind.PROVIDER_OF_LAZY;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.javapoet.TypeName;
import dagger.Lazy;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.RequestKind;
import dagger.producers.Produced;
import dagger.producers.Producer;
import javax.inject.Provider;
import javax.lang.model.type.TypeMirror;

/** Utility methods for {@link RequestKind}s. */
final class RequestKinds {
  /** Returns the type of a request of this kind for a key with a given type. */
  static TypeMirror requestType(RequestKind requestKind, TypeMirror type, DaggerTypes types) {
    switch (requestKind) {
      case INSTANCE:
        return type;

      case PROVIDER_OF_LAZY:
        return types.wrapType(requestType(LAZY, type, types), Provider.class);

      case FUTURE:
        return types.wrapType(type, ListenableFuture.class);

      default:
        return types.wrapType(type, frameworkClass(requestKind));
    }
  }

  /** Returns the type of a request of this kind for a key with a given type. */
  static TypeName requestTypeName(RequestKind requestKind, TypeName keyType) {
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

  private static final ImmutableMap<RequestKind, Class<?>> FRAMEWORK_CLASSES =
      ImmutableMap.of(
          PROVIDER, Provider.class,
          LAZY, Lazy.class,
          PRODUCER, Producer.class,
          PRODUCED, Produced.class);

  /** Returns the {@link RequestKind} that matches the wrapping types (if any) of {@code type}. */
  static RequestKind getRequestKind(TypeMirror type) {
    checkTypePresent(type);
    for (RequestKind kind : FRAMEWORK_CLASSES.keySet()) {
      if (matchesKind(kind, type)) {
        if (kind.equals(PROVIDER) && matchesKind(LAZY, extractKeyType(kind, type))) {
          return PROVIDER_OF_LAZY;
        }
        return kind;
      }
    }
    return INSTANCE;
  }

  /**
   * Returns {@code true} if {@code type} is a parameterized type of {@code kind}'s {@link
   * #frameworkClass(RequestKind) framework class}.
   */
  private static boolean matchesKind(RequestKind kind, TypeMirror type) {
    return isType(type)
        && isTypeOf(frameworkClass(kind), type)
        && !asDeclared(type).getTypeArguments().isEmpty();
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
  static TypeMirror extractKeyType(RequestKind requestKind, TypeMirror type) {
    checkTypePresent(type);
    switch (requestKind) {
      case INSTANCE:
        return type;
      case PROVIDER_OF_LAZY:
        return extractKeyType(LAZY, extractKeyType(PROVIDER, type));
      default:
        checkArgument(isType(type) && isTypeOf(frameworkClass(requestKind), type));
        return getOnlyElement(MoreTypes.asDeclared(type).getTypeArguments());
    }
  }

  /**
   * A dagger- or {@code javax.inject}-defined class for {@code requestKind} that that can wrap
   * another type but share the same {@link dagger.model.Key}.
   *
   * <p>For example, {@code Provider<String>} and {@code Lazy<String>} can both be requested if a
   * key exists for {@code String}; they all share the same key.
   *
   * <p>This concept is not well defined and should probably be removed and inlined into the cases
   * that need it. For example, {@link RequestKind#PROVIDER_OF_LAZY} has <em>2</em> wrapping
   * classes, and {@link RequestKind#FUTURE} is wrapped with a {@link ListenableFuture}, but for
   * historical/implementation reasons has not had an associated framework class.
   */
  static Class<?> frameworkClass(RequestKind requestKind) {
    Class<?> result = FRAMEWORK_CLASSES.get(requestKind);
    checkArgument(result != null, "no framework class for %s", requestKind);
    return result;
  }

  /**
   * Returns {@code true} if requests for {@code requestKind} can be satisfied by a production
   * binding.
   */
  static boolean canBeSatisfiedByProductionBinding(RequestKind requestKind) {
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

  /**
   * Returns true if {@code requestKind} is always derived from a {@link RequestKind#PROVIDER}
   * instance.
   */
  static boolean isDerivedFromProvider(RequestKind requestKind) {
    switch (requestKind) {
      case LAZY:
      case PROVIDER_OF_LAZY:
        return true;
      default:
        return false;
    }
  }

  private RequestKinds() {}
}
