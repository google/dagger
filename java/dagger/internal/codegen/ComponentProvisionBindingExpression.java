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
import static dagger.internal.codegen.ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD;

import com.google.auto.common.MoreElements;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.internal.Preconditions;
import javax.lang.model.element.TypeElement;

/** A binding expression for component provision methods. */
final class ComponentProvisionBindingExpression extends SimpleInvocationBindingExpression {
  private final ProvisionBinding binding;
  private final BindingGraph bindingGraph;
  private final ComponentRequirementFields componentRequirementFields;
  private final CompilerOptions compilerOptions;

  ComponentProvisionBindingExpression(
      BindingExpression providerBindingExpression,
      ProvisionBinding binding,
      BindingGraph bindingGraph,
      ComponentRequirementFields componentRequirementFields,
      CompilerOptions compilerOptions) {
    super(providerBindingExpression);
    this.binding = checkNotNull(binding);
    this.bindingGraph = checkNotNull(bindingGraph);
    this.componentRequirementFields = checkNotNull(componentRequirementFields);
    this.compilerOptions = checkNotNull(compilerOptions);
  }

  @Override
  CodeBlock getInstanceDependencyExpression(
      DependencyRequest.Kind requestKind, ClassName requestingClass) {
    CodeBlock invocation =
        CodeBlock.of(
            "$L.$L()",
            componentRequirementFields.getExpression(componentRequirement(), requestingClass),
            binding.bindingElement().get().getSimpleName());
    return maybeCheckForNull(binding, compilerOptions, invocation);
  }

  private ComponentRequirement componentRequirement() {
    TypeElement componentDependency =
        bindingGraph
            .componentDescriptor()
            .dependencyMethodIndex()
            .get(MoreElements.asExecutable(binding.bindingElement().get()));
    return ComponentRequirement.forDependency(componentDependency.asType());
  }

  static CodeBlock maybeCheckForNull(
      ProvisionBinding binding, CompilerOptions compilerOptions, CodeBlock invocation) {
    return binding.shouldCheckForNull(compilerOptions)
        ? CodeBlock.of(
            "$T.checkNotNull($L, $S)",
            Preconditions.class,
            invocation,
            CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD)
        : invocation;
  }
}
