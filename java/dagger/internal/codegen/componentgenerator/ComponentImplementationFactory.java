/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.internal.codegen.componentgenerator;

import static dagger.internal.codegen.componentgenerator.ComponentGenerator.componentName;

import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.KeyFactory;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.writing.ComponentImplementation;
import dagger.internal.codegen.writing.SubcomponentNames;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Factory for {@link ComponentImplementation}s. */
@Singleton
final class ComponentImplementationFactory {
  private final KeyFactory keyFactory;
  private final CompilerOptions compilerOptions;
  private final DaggerElements elements;
  private final TopLevelImplementationComponent.Builder topLevelImplementationComponentBuilder;

  @Inject
  ComponentImplementationFactory(
      KeyFactory keyFactory,
      CompilerOptions compilerOptions,
      DaggerElements elements,
      TopLevelImplementationComponent.Builder topLevelImplementationComponentBuilder) {
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
    this.elements = elements;
    this.topLevelImplementationComponentBuilder = topLevelImplementationComponentBuilder;
  }

  /**
   * Returns a top-level (non-nested) component implementation for a binding graph.
   */
  ComponentImplementation createComponentImplementation(BindingGraph bindingGraph) {
    ComponentImplementation componentImplementation =
        ComponentImplementation.topLevelComponentImplementation(
            bindingGraph,
            componentName(bindingGraph.componentTypeElement()),
            new SubcomponentNames(bindingGraph, keyFactory),
            compilerOptions,
            elements);

    // TODO(dpb): explore using optional bindings for the "parent" bindings
    return topLevelImplementationComponentBuilder
        .topLevelComponent(componentImplementation)
        .build()
        .currentImplementationSubcomponentBuilder()
        .componentImplementation(componentImplementation)
        .bindingGraph(bindingGraph)
        .parentBuilder(Optional.empty())
        .parentBindingExpressions(Optional.empty())
        .parentRequirementExpressions(Optional.empty())
        .build()
        .componentImplementationBuilder()
        .build();
  }
}
