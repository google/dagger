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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkNotNull;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.javapoet.Expression;
import dagger.internal.codegen.langmodel.DaggerTypes;
import javax.lang.model.type.TypeMirror;

/**
 * A binding expression that implements and uses a component method.
 *
 * <p>Dependents of this binding expression will just call the component method.
 */
final class ComponentMethodBindingExpression extends MethodBindingExpression {
  private final ComponentImplementation componentImplementation;
  private final ComponentMethodDescriptor componentMethod;

  ComponentMethodBindingExpression(
      BindingRequest request,
      ContributionBinding binding,
      MethodImplementationStrategy methodImplementationStrategy,
      BindingExpression wrappedBindingExpression,
      ComponentImplementation componentImplementation,
      ComponentMethodDescriptor componentMethod,
      DaggerTypes types) {
    super(
        request,
        binding,
        methodImplementationStrategy,
        wrappedBindingExpression,
        componentImplementation,
        types);
    this.componentImplementation = checkNotNull(componentImplementation);
    this.componentMethod = checkNotNull(componentMethod);
  }

  @Override
  protected CodeBlock getComponentMethodImplementation(
      ComponentMethodDescriptor componentMethod, ComponentImplementation component) {
    // There could be several methods on the component for the same request key and kind.
    // Only one should use the BindingMethodImplementation; the others can delegate that one. So
    // use methodImplementation.body() only if componentMethod equals the method for this instance.

    // Separately, the method might be defined on a supertype that is also a supertype of some
    // parent component. In that case, the same ComponentMethodDescriptor will be used to add a CMBE
    // for the parent and the child. Only the parent's should use the BindingMethodImplementation;
    // the child's can delegate to the parent. So use methodImplementation.body() only if
    // componentName equals the component for this instance.
    return componentMethod.equals(this.componentMethod) && component.equals(componentImplementation)
        ? methodBodyForComponentMethod(componentMethod)
        : super.getComponentMethodImplementation(componentMethod, component);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    // If a component method returns a primitive, update the expression's type which might be boxed.
    Expression expression = super.getDependencyExpression(requestingClass);
    TypeMirror methodReturnType = componentMethod.methodElement().getReturnType();
    return methodReturnType.getKind().isPrimitive()
        ? Expression.create(methodReturnType, expression.codeBlock())
        : expression;
  }

  @Override
  protected void addMethod() {}

  @Override
  protected String methodName() {
    return componentMethod.methodElement().getSimpleName().toString();
  }
}
