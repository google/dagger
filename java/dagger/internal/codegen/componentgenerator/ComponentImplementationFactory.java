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

import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.writing.ComponentImplementation;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

// TODO(bcorso): We don't need a separate class for this anymore. Merge it into ComponentGenerator.
/** Factory for {@link ComponentImplementation}s. */
@Singleton
final class ComponentImplementationFactory {
  private final TopLevelImplementationComponent.Factory topLevelImplementationComponentFactory;

  @Inject
  ComponentImplementationFactory(
      TopLevelImplementationComponent.Factory topLevelImplementationComponentFactory) {
    this.topLevelImplementationComponentFactory = topLevelImplementationComponentFactory;
  }

  /** Returns a top-level (non-nested) component implementation for a binding graph. */
  ComponentImplementation createComponentImplementation(BindingGraph bindingGraph) {
    // TODO(dpb): explore using optional bindings for the "parent" bindings
    return topLevelImplementationComponentFactory
        .create(bindingGraph)
        .currentImplementationSubcomponentBuilder()
        .bindingGraph(bindingGraph)
        .parentImplementation(Optional.empty())
        .parentBindingExpressions(Optional.empty())
        .parentRequirementExpressions(Optional.empty())
        .build()
        .componentImplementationBuilder()
        .build();
  }
}
