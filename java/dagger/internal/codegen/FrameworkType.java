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
import static dagger.internal.codegen.RequestKinds.frameworkClass;
import static dagger.model.RequestKind.INSTANCE;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.javapoet.CodeBlock;
import dagger.Lazy;
import dagger.MembersInjector;
import dagger.internal.DoubleCheck;
import dagger.internal.ProviderOfLazy;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.internal.Producers;
import javax.inject.Provider;
import javax.lang.model.type.TypeMirror;

/** One of the core types initialized as fields in a generated component. */
enum FrameworkType {
  /** A {@link Provider}. */
  PROVIDER {
    @Override
    RequestKind requestKind() {
      return RequestKind.PROVIDER;
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
              types.rewrapType(from.type(), frameworkClass(requestKind)), codeBlock);
      }
    }
  },

  /** A {@link Producer}. */
  PRODUCER {
    @Override
    RequestKind requestKind() {
      return RequestKind.PRODUCER;
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
          return from;

        default:
          throw new IllegalArgumentException(
              String.format("Cannot request a %s from a %s", requestKind, this));
      }
    }
  },

  // TODO(ronshapiro): Remove this once MembersInjectionBinding no longer extends Binding
  /** A {@link MembersInjector}. */
  MEMBERS_INJECTOR {
    @Override
    RequestKind requestKind() {
      return RequestKind.MEMBERS_INJECTION;
    }

    @Override
    CodeBlock to(RequestKind requestKind, CodeBlock from) {
      throw new UnsupportedOperationException(requestKind.toString());
    }

    @Override
    Expression to(RequestKind requestKind, Expression from, DaggerTypes types) {
      throw new UnsupportedOperationException(requestKind.toString());
    }
  },
  ;

  /** Returns the {@link RequestKind} matching this framework type. */
  abstract RequestKind requestKind();

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
