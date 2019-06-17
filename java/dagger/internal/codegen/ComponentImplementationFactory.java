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

package dagger.internal.codegen;

import static dagger.internal.codegen.ComponentGenerator.componentName;
import static dagger.internal.codegen.Util.reentrantComputeIfAbsent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.TypeElement;

/** Factory for {@link ComponentImplementation}s. */
@Singleton
final class ComponentImplementationFactory implements ClearableCache {
  private final Map<TypeElement, ComponentImplementation> topLevelComponentCache = new HashMap<>();
  private final KeyFactory keyFactory;
  private final CompilerOptions compilerOptions;
  private final TopLevelImplementationComponent.Builder topLevelImplementationComponentBuilder;

  @Inject
  ComponentImplementationFactory(
      KeyFactory keyFactory,
      CompilerOptions compilerOptions,
      TopLevelImplementationComponent.Builder topLevelImplementationComponentBuilder) {
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
    this.topLevelImplementationComponentBuilder = topLevelImplementationComponentBuilder;
  }

  /**
   * Returns a top-level (non-nested) component implementation for a binding graph.
   */
  ComponentImplementation createComponentImplementation(BindingGraph bindingGraph) {
    return reentrantComputeIfAbsent(
        topLevelComponentCache,
        bindingGraph.componentTypeElement(),
        component -> createComponentImplementationUncached(bindingGraph));
  }

  private ComponentImplementation createComponentImplementationUncached(BindingGraph bindingGraph) {
    ComponentImplementation componentImplementation =
        ComponentImplementation.topLevelComponentImplementation(
            bindingGraph,
            componentName(bindingGraph.componentTypeElement()),
            new SubcomponentNames(bindingGraph, keyFactory),
            compilerOptions);

    // TODO(dpb): explore using optional bindings for the "parent" bindings
    CurrentImplementationSubcomponent currentImplementationSubcomponent =
        topLevelImplementationComponentBuilder
            .topLevelComponent(componentImplementation)
            .build()
            .currentImplementationSubcomponentBuilder()
            .componentImplementation(componentImplementation)
            .bindingGraph(bindingGraph)
            .parentBuilder(Optional.empty())
            .parentBindingExpressions(Optional.empty())
            .parentRequirementExpressions(Optional.empty())
            .build();

    return currentImplementationSubcomponent.rootComponentBuilder().build();
  }

  @Override
  public void clearCache() {
    topLevelComponentCache.clear();
  }
}
