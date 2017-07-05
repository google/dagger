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

/**
 * A {@link RequestFulfillment} for {@link
 * dagger.internal.codegen.ContributionBinding.Kind#SYNTHETIC_OPTIONAL_BINDING}
 */
final class OptionalBindingRequestFulfillment extends SimpleInvocationRequestFulfillment {
  private final ProvisionBinding binding;
  private final HasBindingExpressions hasBindingExpressions;

  OptionalBindingRequestFulfillment(
      BindingKey bindingKey,
      ProvisionBinding binding,
      RequestFulfillment delegate,
      HasBindingExpressions hasBindingExpressions) {
    super(bindingKey, delegate);
    this.binding = binding;
    this.hasBindingExpressions = hasBindingExpressions;
  }

  @Override
  CodeBlock getSimpleInvocation(DependencyRequest request, ClassName requestingClass) {
    OptionalType optionalType = OptionalType.from(binding.key());
    OptionalKind optionalKind = optionalType.kind();
    if (binding.dependencies().isEmpty()) {
      // When compiling with -source 7, javac's type inference isn't strong enough to detect
      // Futures.immediateFuture(Optional.absent()) for keys that aren't Object
      if (request.kind().equals(DependencyRequest.Kind.FUTURE)
          && isTypeAccessibleFrom(binding.key().type(), requestingClass.packageName())) {
        return optionalKind.parameterizedAbsentValueExpression(optionalType);
      }
      return optionalKind.absentValueExpression();
    }
    DependencyRequest dependency = getOnlyElement(binding.dependencies());
    return optionalKind.presentExpression(
        hasBindingExpressions
            .getBindingExpression(dependency.bindingKey())
            .getSnippetForDependencyRequest(dependency, requestingClass));
  }
}
