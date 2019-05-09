/*
 * Copyright (C) 2018 The Dagger Authors.
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

import static dagger.internal.codegen.ComponentImplementation.FieldSpecKind.FRAMEWORK_FIELD;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.RequestKind;
import dagger.producers.Producer;
import dagger.producers.internal.CancellationListener;
import dagger.producers.internal.Producers;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/**
 * A factory of {@linkplain Producers#entryPointViewOf(Producer, CancellationListener) entry point
 * views} of {@link Producer}s.
 */
final class ProducerEntryPointView {
  private final DaggerTypes types;

  ProducerEntryPointView(DaggerTypes types) {
    this.types = types;
  }

  /**
   * Returns an expression for an {@linkplain Producers#entryPointViewOf(Producer,
   * CancellationListener) entry point view} of a producer if the component method returns a {@link
   * Producer} or {@link com.google.common.util.concurrent.ListenableFuture}.
   *
   * <p>This is intended to be a replacement implementation for {@link
   * BindingExpression#getDependencyExpressionForComponentMethod(ComponentMethodDescriptor,
   * ComponentImplementation)}, and in cases where {@link Optional#empty()} is returned, callers
   * should call {@code super.getDependencyExpressionForComponentMethod()}.
   */
  Optional<Expression> getProducerEntryPointField(
      BindingExpression producerExpression,
      ComponentMethodDescriptor componentMethod,
      ComponentImplementation component) {
    if (component.componentDescriptor().isProduction()
        && (componentMethod.dependencyRequest().get().kind().equals(RequestKind.FUTURE)
            || componentMethod.dependencyRequest().get().kind().equals(RequestKind.PRODUCER))) {
      return Optional.of(
          Expression.create(
              fieldType(componentMethod),
              "$N",
              createField(producerExpression, componentMethod, component)));
    } else {
      // If the component isn't a production component, it won't implement CancellationListener and
      // as such we can't create an entry point. But this binding must also just be a Producer from
      // Provider anyway in that case, so there shouldn't be an issue.
      // TODO(b/116855531): Is it really intended that a non-production component can have Producer
      // entry points?
      return Optional.empty();
    }
  }

  private FieldSpec createField(
      BindingExpression producerExpression,
      ComponentMethodDescriptor componentMethod,
      ComponentImplementation component) {
    // TODO(cgdecker): Use a FrameworkFieldInitializer for this?
    // Though I don't think we need the once-only behavior of that, since I think
    // getComponentMethodImplementation will only be called once anyway
    String methodName = componentMethod.methodElement().getSimpleName().toString();
    FieldSpec field =
        FieldSpec.builder(
                TypeName.get(fieldType(componentMethod)),
                component.getUniqueFieldName(methodName + "EntryPoint"),
                PRIVATE)
            .build();
    component.addField(FRAMEWORK_FIELD, field);

    CodeBlock fieldInitialization =
        CodeBlock.of(
            "this.$N = $T.entryPointViewOf($L, this);",
            field,
            Producers.class,
            producerExpression.getDependencyExpression(component.name()).codeBlock());
    component.addInitialization(fieldInitialization);

    return field;
  }

  // TODO(cgdecker): Can we use producerExpression.getDependencyExpression().type() instead of
  // needing to (re)compute this?
  private TypeMirror fieldType(ComponentMethodDescriptor componentMethod) {
    return types.wrapType(componentMethod.dependencyRequest().get().key().type(), Producer.class);
  }
}
