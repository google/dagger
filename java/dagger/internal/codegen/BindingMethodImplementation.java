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

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.model.Key;
import dagger.model.RequestKind;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/** Defines a method body and return type for a given {@link BindingExpression}. */
class BindingMethodImplementation {
  private final BindingExpression bindingExpression;
  private final ClassName componentName;
  private final ResolvedBindings resolvedBindings;
  private final RequestKind requestKind;
  private final DaggerTypes types;
  private final Elements elements;

  BindingMethodImplementation(
      BindingExpression bindingExpression,
      ClassName componentName,
      DaggerTypes types,
      Elements elements) {
    this.bindingExpression = checkNotNull(bindingExpression);
    this.componentName = checkNotNull(componentName);
    this.types = checkNotNull(types);
    this.elements = checkNotNull(elements);
    this.resolvedBindings = bindingExpression.resolvedBindings();
    this.requestKind = bindingExpression.requestKind();
  }

  /**
   * Returns the method body, which contains zero or more statements (including semicolons).
   *
   * <p>If the implementation has a non-void return type, the body will also include the {@code
   * return} statement.
   */
  CodeBlock body() {
    return CodeBlock.of(
        "return $L;", bindingExpression.getDependencyExpression(componentName).codeBlock());
  }

  /** Returns the return type for the dependency request. */
  final TypeMirror returnType() {
    ContributionBinding binding = resolvedBindings.contributionBinding();
    if (requestKind.equals(RequestKind.INSTANCE)
        && binding.contributedPrimitiveType().isPresent()) {
      return binding.contributedPrimitiveType().get();
    }
    return accessibleType(requestType(requestKind, binding.contributedType(), types));
  }

  /** Returns the {@linkplain Key} for this expression. */
  protected final Key key() {
    return resolvedBindings.key();
  }

  /** Returns the {#linkplain RequestKind request kind} handled by this expression. */
  protected final RequestKind requestKind() {
    return requestKind;
  }

  /** The binding this instance uses to fulfill requests. */
  protected final ResolvedBindings resolvedBindings() {
    return resolvedBindings;
  }

  // TODO(user): Move this to Accessibility.java or DaggerTypes.java?
  /** Returns a {@link TypeMirror} for the binding that is accessible to the component. */
  protected final TypeMirror accessibleType(TypeMirror type) {
    if (Accessibility.isTypeAccessibleFrom(type, componentName.packageName())) {
      return type;
    } else if (type.getKind().equals(TypeKind.DECLARED)
        && Accessibility.isRawTypeAccessible(type, componentName.packageName())) {
      return types.getDeclaredType(MoreTypes.asTypeElement(type));
    } else {
      return elements.getTypeElement(Object.class.getName()).asType();
    }
  }
}
