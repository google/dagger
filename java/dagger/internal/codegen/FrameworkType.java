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
import static dagger.internal.codegen.DependencyRequest.Kind.INSTANCE;

import com.google.common.util.concurrent.Futures;
import com.squareup.javapoet.CodeBlock;
import dagger.MembersInjector;
import dagger.internal.DoubleCheck;
import dagger.internal.ProviderOfLazy;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.internal.Producers;
import javax.inject.Provider;

/** One of the core types initialized as fields in a generated component. */
enum FrameworkType {
  /** A {@link Provider}. */
  PROVIDER {
    @Override
    CodeBlock to(DependencyRequest.Kind requestKind, CodeBlock from) {
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
  },

  /** A {@link Producer}. */
  PRODUCER {
    @Override
    CodeBlock to(DependencyRequest.Kind requestKind, CodeBlock from) {
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
  },

  /** A {@link MembersInjector}. */
  MEMBERS_INJECTOR {
    @Override
    CodeBlock to(DependencyRequest.Kind requestKind, CodeBlock from) {
      switch (requestKind) {
        case MEMBERS_INJECTOR:
          return from;

        default:
          throw new IllegalArgumentException(
              String.format("Cannot request a %s from a %s", requestKind, this));
      }
    }
  },
  ;

  /**
   * Returns an expression that evaluates to a requested object given an expression that evaluates
   * to an instance of this framework type.
   *
   * @param requestKind the kind of {@link DependencyRequest} that the returned expression can
   *     satisfy
   * @param from an expression that evaluates to an instance of this framework type
   * @throws IllegalArgumentException if a valid expression cannot be generated for {@code
   *     requestKind}
   */
  abstract CodeBlock to(DependencyRequest.Kind requestKind, CodeBlock from);

  @Override
  public String toString() {
    return UPPER_UNDERSCORE.to(UPPER_CAMEL, super.toString());
  }
}
