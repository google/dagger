/*
 * Copyright (C) 2018 The Dagger Authors.
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

import dagger.BindsInstance;
import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.writing.ComponentImplementation;
import dagger.internal.codegen.writing.ComponentImplementation.ChildComponentImplementationFactory;
import dagger.internal.codegen.writing.ComponentRequestRepresentations;
import dagger.internal.codegen.writing.ComponentRequirementExpressions;
import dagger.internal.codegen.writing.ComponentWrapperImplementation;
import dagger.internal.codegen.writing.GeneratedImplementation;
import dagger.internal.codegen.writing.ParentComponent;
import dagger.internal.codegen.writing.PerComponentImplementation;
import dagger.internal.codegen.writing.TopLevel;
import java.util.Optional;
import javax.inject.Provider;

/**
 * A subcomponent that injects all objects that are responsible for creating a single {@link
 * ComponentImplementation} instance. Each child {@link ComponentImplementation} will have its own
 * instance of {@link CurrentImplementationSubcomponent}.
 */
@Subcomponent(
    modules = CurrentImplementationSubcomponent.ChildComponentImplementationFactoryModule.class)
@PerComponentImplementation
// This only needs to be public because the type is referenced by generated component.
public interface CurrentImplementationSubcomponent {
  ComponentImplementation componentImplementation();

  /** A module to bind the {@link ChildComponentImplementationFactory}. */
  @Module
  interface ChildComponentImplementationFactoryModule {
    @Provides
    static ChildComponentImplementationFactory provideChildComponentImplementationFactory(
        CurrentImplementationSubcomponent.Builder currentImplementationSubcomponentBuilder,
        Provider<ComponentImplementation> componentImplementation,
        Provider<ComponentRequestRepresentations> componentRequestRepresentations,
        Provider<ComponentRequirementExpressions> componentRequirementExpressions) {
      return childGraph ->
          currentImplementationSubcomponentBuilder
              .bindingGraph(childGraph)
              .parentImplementation(Optional.of(componentImplementation.get()))
              .parentRequestRepresentations(Optional.of(componentRequestRepresentations.get()))
              .parentRequirementExpressions(Optional.of(componentRequirementExpressions.get()))
              .build()
              .componentImplementation();
    }

    @Provides
    @TopLevel
    static GeneratedImplementation provideTopLevelImplementation(
        ComponentImplementation componentImplementation,
        ComponentWrapperImplementation componentWrapperImplementation,
        CompilerOptions compilerOptions) {
      return compilerOptions.generatedClassExtendsComponent()
          ? componentImplementation.rootComponentImplementation().getComponentShard()
          : componentWrapperImplementation;
    }
  }

  /** Returns the builder for {@link CurrentImplementationSubcomponent}. */
  @Subcomponent.Builder
  interface Builder {
    @BindsInstance
    Builder bindingGraph(BindingGraph bindingGraph);

    @BindsInstance
    Builder parentImplementation(
        @ParentComponent Optional<ComponentImplementation> parentImplementation);

    @BindsInstance
    Builder parentRequestRepresentations(
        @ParentComponent Optional<ComponentRequestRepresentations> parentRequestRepresentations);

    @BindsInstance
    Builder parentRequirementExpressions(
        @ParentComponent Optional<ComponentRequirementExpressions> parentRequirementExpressions);

    CurrentImplementationSubcomponent build();
  }
}
