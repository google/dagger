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

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.GeneratedComponentModel.FieldSpecKind.FRAMEWORK_FIELD;
import static javax.lang.model.element.Modifier.PRIVATE;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.producers.Producer;
import dagger.producers.internal.Producers;
import javax.lang.model.type.TypeMirror;

/** Binding expression for producer node instances. */
final class ProducerNodeInstanceBindingExpression extends FrameworkInstanceBindingExpression {

  static final String MAY_INTERRUPT_IF_RUNNING = "mayInterruptIfRunning";

  /** Model for the component defining this binding. */
  private final GeneratedComponentModel generatedComponentModel;

  private final TypeMirror type;
  private boolean addedCancellationStatement = false;

  ProducerNodeInstanceBindingExpression(
      ResolvedBindings resolvedBindings,
      FrameworkInstanceSupplier frameworkInstanceSupplier,
      DaggerTypes types,
      DaggerElements elements,
      GeneratedComponentModel generatedComponentModel) {
    super(resolvedBindings, frameworkInstanceSupplier, types, elements);
    this.generatedComponentModel = checkNotNull(generatedComponentModel);
    this.type = types.wrapType(resolvedBindings.key().type(), Producer.class);
  }

  @Override
  protected FrameworkType frameworkType() {
    return FrameworkType.PRODUCER_NODE;
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    Expression result = super.getDependencyExpression(requestingClass);
    if (!addedCancellationStatement) {
      CodeBlock cancel =
          CodeBlock.of(
              "$T.cancel($L, $L);", Producers.class, result.codeBlock(), MAY_INTERRUPT_IF_RUNNING);
      generatedComponentModel.addCancellation(cancel);
      addedCancellationStatement = true;
    }
    return result;
  }

  @Override
  Expression getDependencyExpressionForComponentMethod(
      ComponentMethodDescriptor componentMethod, GeneratedComponentModel component) {
    if (component.componentDescriptor().kind().isProducer()) {
      return Expression.create(type, "$N", createField(componentMethod, component));
    } else {
      // If the component isn't a production component, it won't implement CancellationListener and
      // as such we can't create an entry point. But this binding must also just be a Producer from
      // Provider anyway in that case, so there shouldn't be an issue.
      // TODO(b/116855531): Is it really intended that a non-production component can have Producer
      // entry points?
      return super.getDependencyExpressionForComponentMethod(componentMethod, component);
    }
  }

  private FieldSpec createField(
      ComponentMethodDescriptor componentMethod, GeneratedComponentModel component) {
    // TODO(cgdecker): Use a FrameworkFieldInitializer for this?
    // Though I don't think we need the once-only behavior of that, since I think
    // getComponentMethodImplementation will only be called once anyway
    String methodName = componentMethod.methodElement().getSimpleName().toString();
    FieldSpec field =
        FieldSpec.builder(
                TypeName.get(type),
                component.getUniqueFieldName(methodName + "EntryPoint"),
                PRIVATE)
            .build();
    component.addField(FRAMEWORK_FIELD, field);

    CodeBlock fieldInitialization =
        CodeBlock.of(
            "this.$N = $T.entryPointViewOf($L, this);",
            field,
            Producers.class,
            getDependencyExpression(component.name()).codeBlock());
    component.addInitialization(fieldInitialization);

    return field;
  }
}
