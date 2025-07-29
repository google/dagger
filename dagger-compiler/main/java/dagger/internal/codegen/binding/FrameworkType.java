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

package dagger.internal.codegen.binding;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XProcessingEnv;
import dagger.internal.codegen.base.RequestKinds;
import dagger.internal.codegen.model.RequestKind;
import dagger.internal.codegen.xprocessing.XExpression;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Optional;

/** One of the core types initialized as fields in a generated component. */
public enum FrameworkType {
  /** A {@link javax.inject.Provider}. */
  PROVIDER {
    @Override
    public XCodeBlock to(
        RequestKind requestKind,
        XCodeBlock from) {
      switch (requestKind) {
        case INSTANCE:
          return XCodeBlock.of("%L.get()", from);

        case LAZY:
          return XCodeBlock.of(
              "%T.lazy(%L)",
              XTypeNames.DOUBLE_CHECK,
              from);

        case PROVIDER:
          return from;

        case PROVIDER_OF_LAZY:
          return XCodeBlock.of("%T.create(%L)", XTypeNames.PROVIDER_OF_LAZY, from);

        case PRODUCER:
          return XCodeBlock.of("%T.producerFromProvider(%L)", XTypeNames.PRODUCERS, from);

        case FUTURE:
          return XCodeBlock.of(
              "%T.immediateFuture(%L)",
              XTypeNames.FUTURES,
              to(
                  RequestKind.INSTANCE,
                  from));

        case PRODUCED:
          return XCodeBlock.of(
              "%T.successful(%L)",
              XTypeNames.PRODUCED,
              to(
                  RequestKind.INSTANCE,
                  from));

        default:
          throw new IllegalArgumentException(
              String.format("Cannot request a %s from a %s", requestKind, this));
      }
    }

    @Override
    public XExpression to(
        RequestKind requestKind,
        XExpression from,
        XProcessingEnv processingEnv) {
      XCodeBlock codeBlock =
          to(
              requestKind,
              from.codeBlock());
      switch (requestKind) {
        case INSTANCE:
          return XExpression.create(from.type().unwrapType(), codeBlock);

        case PROVIDER:
          return from;

        case PROVIDER_OF_LAZY:
          return XExpression.create(
              from.type().rewrapType(XTypeNames.LAZY).wrapType(XTypeNames.DAGGER_PROVIDER),
              codeBlock);

        case FUTURE:
          return XExpression.create(
              from.type().rewrapType(XTypeNames.LISTENABLE_FUTURE), codeBlock);

        default:
          return XExpression.create(
              from.type().rewrapType(RequestKinds.frameworkClassName(requestKind)), codeBlock);
      }
    }
  },

  /** A {@link dagger.producers.Producer}. */
  PRODUCER_NODE {
    @Override
    public XCodeBlock to(
        RequestKind requestKind,
        XCodeBlock from) {
      switch (requestKind) {
        case FUTURE:
          return XCodeBlock.of("%L.get()", from);

        case PRODUCER:
          return from;

        default:
          throw new IllegalArgumentException(
              String.format("Cannot request a %s from a %s", requestKind, this));
      }
    }

    @Override
    public XExpression to(
        RequestKind requestKind,
        XExpression from,
        XProcessingEnv processingEnv) {
      switch (requestKind) {
        case FUTURE:
          return XExpression.create(
              from.type().rewrapType(XTypeNames.LISTENABLE_FUTURE),
              to(
                  requestKind,
                  from.codeBlock()));

        case PRODUCER:
          return from;

        default:
          throw new IllegalArgumentException(
              String.format("Cannot request a %s from a %s", requestKind, this));
      }
    }
  };

  /** Returns the framework type appropriate for fields for a given binding type. */
  public static FrameworkType forBindingType(BindingType bindingType) {
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
  public static Optional<FrameworkType> forRequestKind(RequestKind requestKind) {
    switch (requestKind) {
      case PROVIDER:
        return Optional.of(FrameworkType.PROVIDER);
      case PRODUCER:
        return Optional.of(FrameworkType.PRODUCER_NODE);
      default:
        return Optional.empty();
    }
  }

  /** The class of fields of this type. */
  public XClassName frameworkClassName() {
    switch (this) {
      case PROVIDER:
        return XTypeNames.DAGGER_PROVIDER;
      case PRODUCER_NODE:
        // TODO(cgdecker): Replace this with new class for representing internal producer nodes.
        // Currently the new class is CancellableProducer, but it may be changed to ProducerNode and
        // made to not implement Producer.
        return XTypeNames.PRODUCER;
    }
    throw new AssertionError("Unknown value: " + this.name());
  }

  /** Returns the {@link #frameworkClassName()} parameterized with a type. */
  public XTypeName frameworkClassOf(XTypeName valueType) {
    return frameworkClassName().parametrizedBy(valueType);
  }

  /** The request kind that an instance of this framework type can satisfy directly, if any. */
  public RequestKind requestKind() {
    switch (this) {
      case PROVIDER:
        return RequestKind.PROVIDER;
      case PRODUCER_NODE:
        return RequestKind.PRODUCER;
    }
    throw new AssertionError("Unknown value: " + this.name());
  }

  /**
   * Returns a {@link XCodeBlock} that evaluates to a requested object given an expression that
   * evaluates to an instance of this framework type.
   *
   * @param requestKind the kind of {@link DependencyRequest} that the returned expression can
   *     satisfy
   * @param from a {@link XCodeBlock} that evaluates to an instance of this framework type
   * @throws IllegalArgumentException if a valid expression cannot be generated for {@code
   *     requestKind}
   */
  public abstract XCodeBlock to(
      RequestKind requestKind,
      XCodeBlock from);

  /**
   * Returns an {@link XExpression} that evaluates to a requested object given an expression that
   * evaluates to an instance of this framework type.
   *
   * @param requestKind the kind of {@link DependencyRequest} that the returned expression can
   *     satisfy
   * @param from an expression that evaluates to an instance of this framework type
   * @throws IllegalArgumentException if a valid expression cannot be generated for {@code
   *     requestKind}
   */
  public abstract XExpression to(
      RequestKind requestKind,
      XExpression from,
      XProcessingEnv processingEnv);

  @Override
  public String toString() {
    return UPPER_UNDERSCORE.to(UPPER_CAMEL, super.toString());
  }
}
