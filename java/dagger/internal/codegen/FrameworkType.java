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

package dagger.internal.codegen;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static dagger.model.RequestKind.INSTANCE;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import dagger.Lazy;
import dagger.internal.DoubleCheck;
import dagger.internal.ProviderOfLazy;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.internal.Producers;
import java.util.Optional;
import javax.inject.Provider;
import javax.lang.model.type.TypeMirror;

/** One of the core types initialized as fields in a generated component. */
enum FrameworkType {
  /** A {@link Provider}. */
  PROVIDER {
    @Override
    Class<?> frameworkClass() {
      return Provider.class;
    }

    @Override
    Optional<RequestKind> requestKind() {
      return Optional.of(RequestKind.PROVIDER);
    }

    @Override
    CodeBlock to(RequestKind requestKind, CodeBlock from) {
      switch (requestKind) {
        case INSTANCE:
          return CodeBlock.of("$L.get()", from);

        case LAZY:
          return CodeBlock.of("$T.lazy($L)", DoubleCheck.class, from);

        case PROVIDER:
          return from;

        case PROVIDER_OF_LAZY:
          return CodeBlock.of("$T.create($L)", ProviderOfLazy.class, from);

        case PRODUCER:
          return CodeBlock.of("$T.producerFromProvider($L)", Producers.class, from);

        case FUTURE:
          return CodeBlock.of("$T.immediateFuture($L)", Futures.class, to(INSTANCE, from));

        case PRODUCED:
          return CodeBlock.of("$T.successful($L)", Produced.class, to(INSTANCE, from));

        default:
          throw new IllegalArgumentException(
              String.format("Cannot request a %s from a %s", requestKind, this));
      }
    }

    @Override
    Expression to(RequestKind requestKind, Expression from, DaggerTypes types) {
      CodeBlock codeBlock = to(requestKind, from.codeBlock());
      switch (requestKind) {
        case INSTANCE:
          return Expression.create(types.unwrapTypeOrObject(from.type()), codeBlock);

        case PROVIDER:
          return from;

        case PROVIDER_OF_LAZY:
          TypeMirror lazyType = types.rewrapType(from.type(), Lazy.class);
          return Expression.create(types.wrapType(lazyType, Provider.class), codeBlock);

        case FUTURE:
          return Expression.create(
              types.rewrapType(from.type(), ListenableFuture.class), codeBlock);

        default:
          return Expression.create(
              types.rewrapType(from.type(), RequestKinds.frameworkClass(requestKind)), codeBlock);
      }
    }
  },

  /** A {@link Producer}. */
  PRODUCER_NODE {
    @Override
    Class<?> frameworkClass() {
      // TODO(cgdecker): Replace this with new class for representing internal producer nodes.
      // Currently the new class is CancellableProducer, but it may be changed to ProducerNode and
      // made to not implement Producer.
      return Producer.class;
    }

    @Override
    Optional<RequestKind> requestKind() {
      return Optional.empty();
    }

    @Override
    CodeBlock to(RequestKind requestKind, CodeBlock from) {
      switch (requestKind) {
        case FUTURE:
          return CodeBlock.of("$L.get()", from);

        case PRODUCER:
          return from;

        default:
          throw new IllegalArgumentException(
              String.format("Cannot request a %s from a %s", requestKind, this));
      }
    }

    @Override
    Expression to(RequestKind requestKind, Expression from, DaggerTypes types) {
      switch (requestKind) {
        case FUTURE:
          return Expression.create(
              types.rewrapType(from.type(), ListenableFuture.class),
              to(requestKind, from.codeBlock()));

        case PRODUCER:
          return Expression.create(from.type(), to(requestKind, from.codeBlock()));

        default:
          throw new IllegalArgumentException(
              String.format("Cannot request a %s from a %s", requestKind, this));
      }
    }
  },
  ;

  /** Returns the framework type appropriate for fields for a given binding type. */
  static FrameworkType forBindingType(BindingType bindingType) {
    switch (bindingType) {
      case PROVISION:
        return PROVIDER;
      case PRODUCTION:
        return PRODUCER_NODE;
      case MEMBERS_INJECTION:
    }
    throw new AssertionError(bindingType);
  }

  /** Returns the framework type that exactly matches the given request kind, if one exists. */
  static Optional<FrameworkType> forRequestKind(RequestKind requestKind) {
    switch (requestKind) {
      case PROVIDER:
        return Optional.of(FrameworkType.PROVIDER);
      default:
        return Optional.empty();
    }
  }

  /** The class of fields of this type. */
  abstract Class<?> frameworkClass();

  /** Returns the {@link #frameworkClass()} parameterized with a type. */
  ParameterizedTypeName frameworkClassOf(TypeName valueType) {
    return ParameterizedTypeName.get(ClassName.get(frameworkClass()), valueType);
  }

  /** The request kind that an instance of this framework type can satisfy directly, if any. */
  abstract Optional<RequestKind> requestKind();

  /**
   * Returns a {@link CodeBlock} that evaluates to a requested object given an expression that
   * evaluates to an instance of this framework type.
   *
   * @param requestKind the kind of {@link DependencyRequest} that the returned expression can
   *     satisfy
   * @param from a {@link CodeBlock} that evaluates to an instance of this framework type
   * @throws IllegalArgumentException if a valid expression cannot be generated for {@code
   *     requestKind}
   */
  abstract CodeBlock to(RequestKind requestKind, CodeBlock from);

  /**
   * Returns an {@link Expression} that evaluates to a requested object given an expression that
   * evaluates to an instance of this framework type.
   *
   * @param requestKind the kind of {@link DependencyRequest} that the returned expression can
   *     satisfy
   * @param from an expression that evaluates to an instance of this framework type
   * @throws IllegalArgumentException if a valid expression cannot be generated for {@code
   *     requestKind}
   */
  abstract Expression to(RequestKind requestKind, Expression from, DaggerTypes types);

  @Override
  public String toString() {
    return UPPER_UNDERSCORE.to(UPPER_CAMEL, super.toString());
  }
}
