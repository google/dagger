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

package dagger.internal.codegen.writing;

import static dagger.internal.codegen.xprocessing.XElements.asMethod;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XCodeBlock;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ComponentDependencyProvisionBinding;
import dagger.internal.codegen.binding.ComponentRequirement;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.xprocessing.XExpression;
import dagger.internal.codegen.xprocessing.XTypeNames;

/** A binding expression for component provision methods. */
final class ComponentProvisionRequestRepresentation extends RequestRepresentation {
  private final ComponentDependencyProvisionBinding binding;
  private final BindingGraph bindingGraph;
  private final ComponentRequirementExpressions componentRequirementExpressions;
  private final CompilerOptions compilerOptions;

  @AssistedInject
  ComponentProvisionRequestRepresentation(
      @Assisted ComponentDependencyProvisionBinding binding,
      BindingGraph bindingGraph,
      ComponentImplementation componentImplementation,
      ComponentRequirementExpressions componentRequirementExpressions,
      CompilerOptions compilerOptions) {
    this.binding = binding;
    this.bindingGraph = bindingGraph;
    this.componentRequirementExpressions = componentRequirementExpressions;
    this.compilerOptions = compilerOptions;
  }

  @Override
  XExpression getDependencyExpression(XClassName requestingClass) {
    XCodeBlock componentDependency = getComponentRequirementExpression(requestingClass);
    XCodeBlock invocation =
        XCodeBlock.of(
            "%L.%L()", componentDependency, asMethod(binding.bindingElement().get()).getJvmName());
    return XExpression.create(
        binding.contributedPrimitiveType().orElse(binding.key().type().xprocessing()),
        maybeCheckForNull(binding, compilerOptions, invocation));
  }

  private XCodeBlock getComponentRequirementExpression(XClassName requestingClass) {
    return componentRequirementExpressions.getExpression(componentRequirement(), requestingClass);
  }

  private ComponentRequirement componentRequirement() {
    return bindingGraph
        .componentDescriptor()
        .getDependencyThatDefinesMethod(binding.bindingElement().get());
  }

  static XCodeBlock maybeCheckForNull(
      ComponentDependencyProvisionBinding binding,
      CompilerOptions compilerOptions,
      XCodeBlock invocation) {
    return binding.shouldCheckForNull(compilerOptions)
        ? XCodeBlock.of(
            "%T.checkNotNullFromComponent(%L)", XTypeNames.DAGGER_PRECONDITIONS, invocation)
        : invocation;
  }

  @AssistedFactory
  static interface Factory {
    ComponentProvisionRequestRepresentation create(ComponentDependencyProvisionBinding binding);
  }
}
