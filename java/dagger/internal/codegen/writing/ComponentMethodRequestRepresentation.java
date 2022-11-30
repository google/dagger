/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.internal.codegen.writing;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.room.compiler.processing.XProcessingEnv;
import com.squareup.javapoet.CodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.javapoet.ExpressionType;

/**
 * A binding expression that implements and uses a component method.
 *
 * <p>Dependents of this binding expression will just call the component method.
 */
final class ComponentMethodRequestRepresentation extends MethodRequestRepresentation {
  private final RequestRepresentation wrappedRequestRepresentation;
  private final ComponentImplementation componentImplementation;
  private final ComponentMethodDescriptor componentMethod;

  @AssistedInject
  ComponentMethodRequestRepresentation(
      @Assisted RequestRepresentation wrappedRequestRepresentation,
      @Assisted ComponentMethodDescriptor componentMethod,
      ComponentImplementation componentImplementation,
      XProcessingEnv processingEnv) {
    super(componentImplementation.getComponentShard(), processingEnv);
    this.wrappedRequestRepresentation = checkNotNull(wrappedRequestRepresentation);
    this.componentMethod = checkNotNull(componentMethod);
    this.componentImplementation = componentImplementation;
  }

  @Override
  protected Expression getDependencyExpressionForComponentMethod(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    // There could be several methods on the component for the same request key and kind.
    // Only one should use the BindingMethodImplementation; the others can delegate that one.
    // Separately, the method might be defined on a supertype that is also a supertype of some
    // parent component. In that case, the same ComponentMethodDescriptor will be used to add a CMBE
    // for the parent and the child. Only the parent's should use the BindingMethodImplementation;
    // the child's can delegate to the parent. So use methodImplementation.body() only if
    // componentName equals the component for this instance.
    return componentMethod.equals(this.componentMethod) && component.equals(componentImplementation)
        ? wrappedRequestRepresentation.getDependencyExpressionForComponentMethod(
            componentMethod, componentImplementation)
        : super.getDependencyExpressionForComponentMethod(componentMethod, component);
  }

  @Override
  protected CodeBlock methodCall() {
    return CodeBlock.of("$N()", componentMethod.methodElement().getJvmName());
  }

  @Override
  protected ExpressionType returnType() {
    return ExpressionType.create(componentMethod.methodElement().getReturnType());
  }

  @AssistedFactory
  static interface Factory {
    ComponentMethodRequestRepresentation create(
        RequestRepresentation wrappedRequestRepresentation,
        ComponentMethodDescriptor componentMethod);
  }
}
