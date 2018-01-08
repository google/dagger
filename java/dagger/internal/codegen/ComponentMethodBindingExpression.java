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
import com.squareup.javapoet.MethodSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.model.RequestKind;

/**
 * A binding expression that implements and uses a component method.
 *
 * <p>Dependents of this binding expression will just call the component method.
 */
final class ComponentMethodBindingExpression extends BindingExpression {
  private final BindingMethodImplementation methodImplementation;
  private final ClassName componentName;
  private final ComponentMethodDescriptor componentMethod;
  private final ComponentBindingExpressions componentBindingExpressions;

  ComponentMethodBindingExpression(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      BindingMethodImplementation methodImplementation,
      ClassName componentName,
      ComponentMethodDescriptor componentMethod,
      ComponentBindingExpressions componentBindingExpressions) {
    super(resolvedBindings, requestKind);
    this.methodImplementation = checkNotNull(methodImplementation);
    this.componentName = checkNotNull(componentName);
    this.componentMethod = checkNotNull(componentMethod);
    this.componentBindingExpressions = checkNotNull(componentBindingExpressions);
  }

  @Override
  protected CodeBlock doGetComponentMethodImplementation(
      ComponentMethodDescriptor componentMethod, ClassName requestingClass) {
    // There could be multiple component methods with the same request key and kind. We designate
    // the component method passed into the constructor to contain the implementation code.
    return this.componentMethod.equals(componentMethod)
        ? methodImplementation.body(requestingClass)
        : super.doGetComponentMethodImplementation(componentMethod, requestingClass);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    MethodSpec implementedMethod = componentBindingExpressions.getComponentMethod(componentMethod);
    return Expression.create(
        methodImplementation.returnType(),
        componentName.equals(requestingClass)
            ? CodeBlock.of("$N()", implementedMethod)
            : CodeBlock.of("$T.this.$N()", componentName, implementedMethod));
  }
}
