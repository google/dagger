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
import static dagger.internal.codegen.RequestKinds.requestType;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.model.RequestKind;
import javax.lang.model.type.TypeMirror;

/** Defines a method body and return type for a given {@link BindingExpression}. */
class BindingMethodImplementation {
  private final ContributionBinding binding;
  private final RequestKind requestKind;
  private final BindingExpression bindingExpression;
  private final ClassName componentName;
  private final DaggerTypes types;

  BindingMethodImplementation(
      ResolvedBindings resolvedBindings,
      RequestKind requestKind,
      BindingExpression bindingExpression,
      ClassName componentName,
      DaggerTypes types) {
    this.binding = resolvedBindings.contributionBinding();
    this.requestKind = checkNotNull(requestKind);
    this.bindingExpression = checkNotNull(bindingExpression);
    this.componentName = checkNotNull(componentName);
    this.types = checkNotNull(types);
  }

  /**
   * Returns the method body, which contains zero or more statements (including semicolons).
   *
   * <p>If the implementation has a non-void return type, the body will also include the {@code
   * return} statement.
   */
  CodeBlock body() {
    return CodeBlock.of("return $L;", simpleBindingExpression());
  }

  /** Returns the code for the binding expression. */
  protected final CodeBlock simpleBindingExpression() {
    return bindingExpression.getDependencyExpression(componentName).codeBlock();
  }

  /** Returns the return type for the dependency request. */
  final TypeMirror returnType() {
    if (requestKind.equals(RequestKind.INSTANCE)
        && binding.contributedPrimitiveType().isPresent()) {
      return binding.contributedPrimitiveType().get();
    }
    return types.accessibleType(
        requestType(requestKind, binding.contributedType(), types), componentName);
  }
}
