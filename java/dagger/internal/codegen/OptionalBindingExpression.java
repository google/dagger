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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.codegen.OptionalType.OptionalKind;

/** A binding expression for optional bindings. */
final class OptionalBindingExpression extends SimpleInvocationBindingExpression {
  private final ProvisionBinding binding;
  private final ComponentBindingExpressions componentBindingExpressions;

  OptionalBindingExpression(
      ProvisionBinding binding,
      BindingExpression delegate,
      ComponentBindingExpressions componentBindingExpressions) {
    super(delegate);
    this.binding = binding;
    this.componentBindingExpressions = componentBindingExpressions;
  }

  @Override
  CodeBlock getInstanceDependencyExpression(
      DependencyRequest.Kind requestKind, ClassName requestingClass) {
    OptionalType optionalType = OptionalType.from(binding.key());
    OptionalKind optionalKind = optionalType.kind();
    if (binding.dependencies().isEmpty()) {
      // When compiling with -source 7, javac's type inference isn't strong enough to detect
      // Futures.immediateFuture(Optional.absent()) for keys that aren't Object
      if (requestKind.equals(DependencyRequest.Kind.FUTURE)
          && isTypeAccessibleFrom(binding.key().type(), requestingClass.packageName())) {
        return optionalKind.parameterizedAbsentValueExpression(optionalType);
      }
      return optionalKind.absentValueExpression();
    }
    DependencyRequest dependency = getOnlyElement(binding.dependencies());

    CodeBlock dependencyExpression =
        componentBindingExpressions.getDependencyExpression(dependency, requestingClass);

    // If the dependency type is inaccessible, then we have to use Optional.<Object>of(...), or else
    // we will get "incompatible types: inference variable has incompatible bounds.
    return isTypeAccessibleFrom(dependency.key().type(), requestingClass.packageName())
        ? optionalKind.presentExpression(dependencyExpression)
        : optionalKind.presentObjectExpression(dependencyExpression);
  }
}
