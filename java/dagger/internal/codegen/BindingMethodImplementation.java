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

import com.google.common.base.Supplier;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.model.RequestKind;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/** Defines a method body and return type for a given {@link BindingExpression}. */
class BindingMethodImplementation {
  private final ComponentImplementation component;
  private final ContributionBinding binding;
  private final BindingRequest request;
  private final BindingExpression bindingExpression;
  private final DaggerTypes types;

  BindingMethodImplementation(
      ComponentImplementation component,
      ContributionBinding binding,
      BindingRequest request,
      BindingExpression bindingExpression,
      DaggerTypes types) {
    this.component = component;
    this.binding = binding;
    this.request = request;
    this.bindingExpression = checkNotNull(bindingExpression);
    this.types = types;
  }

  /** The method's body. */
  final CodeBlock body() {
    return implementation(bindingExpression.getDependencyExpression(component.name())::codeBlock);
  }

  /** The method's body if this method is a component method. */
  final CodeBlock bodyForComponentMethod(ComponentMethodDescriptor componentMethod) {
    return implementation(
        bindingExpression.getDependencyExpressionForComponentMethod(componentMethod, component)
            ::codeBlock);
  }

  /**
   * Returns the method body, which contains zero or more statements (including semicolons).
   *
   * <p>If the implementation has a non-void return type, the body will also include the {@code
   * return} statement.
   *
   * @param simpleBindingExpression the expression to retrieve an instance of this binding without
   *     the wrapping method.
   */
  CodeBlock implementation(Supplier<CodeBlock> simpleBindingExpression) {
    return CodeBlock.of("return $L;", simpleBindingExpression.get());
  }

  /** Returns the return type for the dependency request. */
  final TypeMirror returnType() {
    if (request.isRequestKind(RequestKind.INSTANCE)
        && binding.contributedPrimitiveType().isPresent()) {
      return binding.contributedPrimitiveType().get();
    }

    if (matchingComponentMethod().isPresent()) {
      // Component methods are part of the user-defined API, and thus we must use the user-defined
      // type.
      return matchingComponentMethod().get().resolvedReturnType(types);
    }

    // If the component is abstract, this method may be overridden by another implementation in a
    // different package for which requestedType is inaccessible. In order to make that method
    // overridable, we use the publicly accessible type. If the type is final, we don't need to 
    // worry about this, and instead just need to check accessibility of the file we're about to
    // write
    TypeMirror requestedType = request.requestedType(binding.contributedType(), types);
    return component.isAbstract()
        ? types.publiclyAccessibleType(requestedType)
        : types.accessibleType(requestedType, component.name());
  }

  private Optional<ComponentMethodDescriptor> matchingComponentMethod() {
    return component.componentDescriptor().firstMatchingComponentMethod(request);
  }
}
