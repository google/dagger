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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;

/** A factory of code expressions used to access a single binding in a component. */
abstract class BindingExpression {
  private final ResolvedBindings resolvedBindings;

  BindingExpression(ResolvedBindings resolvedBindings) {
    this.resolvedBindings = checkNotNull(resolvedBindings);
  }

  /** The binding this instance uses to fulfill requests. */
  final ResolvedBindings resolvedBindings() {
    return resolvedBindings;
  }

  /**
   * Returns an expression that evaluates to the value of a request for a given kind of dependency
   * on this binding.
   *
   * @param requestingClass the class that will contain the expression
   */
  abstract Expression getDependencyExpression(
      DependencyRequest.Kind requestKind, ClassName requestingClass);

  /** Returns an expression for the implementation of a component method with the given request. */
  CodeBlock getComponentMethodImplementation(
      ComponentMethodDescriptor componentMethod, ClassName requestingClass) {
    DependencyRequest request = componentMethod.dependencyRequest().get();
    checkArgument(request.bindingKey().equals(resolvedBindings().bindingKey()));
    // By default, just delegate to #getDependencyExpression().
    CodeBlock expression = getDependencyExpression(request.kind(), requestingClass).codeBlock();
    return CodeBlock.of("return $L;", expression);
  }
}
