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
import dagger.internal.Preconditions;
import dagger.internal.codegen.javapoet.Expression;

/** A binding expression for component provision methods. */
final class ComponentProvisionBindingExpression extends SimpleInvocationBindingExpression {
  private final ProvisionBinding binding;
  private final BindingGraph bindingGraph;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final CompilerOptions compilerOptions;

  ComponentProvisionBindingExpression(
      ResolvedBindings resolvedBindings,
      BindingGraph bindingGraph,
      ComponentRequirementExpressions componentRequirementExpressions,
      CompilerOptions compilerOptions) {
    super(resolvedBindings);
    this.binding = (ProvisionBinding) resolvedBindings.contributionBinding();
    this.bindingGraph = checkNotNull(bindingGraph);
    this.componentRequirementExpressions = checkNotNull(componentRequirementExpressions);
    this.compilerOptions = checkNotNull(compilerOptions);
  }

  @Override
  Expression getDependencyExpression(ClassName requestingClass) {
    CodeBlock invocation =
        CodeBlock.of(
            "$L.$L()",
            componentRequirementExpressions.getExpression(componentRequirement(), requestingClass),
            binding.bindingElement().get().getSimpleName());
    return Expression.create(
        binding.contributedPrimitiveType().orElse(binding.key().type()),
        maybeCheckForNull(binding, compilerOptions, invocation));
  }

  private ComponentRequirement componentRequirement() {
    return bindingGraph
        .componentDescriptor()
        .getDependencyThatDefinesMethod(binding.bindingElement().get());
  }

  static CodeBlock maybeCheckForNull(
      ProvisionBinding binding, CompilerOptions compilerOptions, CodeBlock invocation) {
    return binding.shouldCheckForNull(compilerOptions)
        ? CodeBlock.of(
            "$T.checkNotNull($L, $S)",
            Preconditions.class,
            invocation,
            "Cannot return null from a non-@Nullable component method")
        : invocation;
  }
}
