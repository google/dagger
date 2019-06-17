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

package dagger.internal.codegen;

import dagger.BindsInstance;
import dagger.Subcomponent;
import dagger.internal.codegen.ComponentImplementationBuilder.RootComponentImplementationBuilder;
import dagger.internal.codegen.ComponentImplementationBuilder.SubcomponentImplementationBuilder;
import java.util.Optional;

/**
 * A subcomponent that injects all objects that are responsible for creating a single {@link
 * ComponentImplementation} instance. Each child {@link ComponentImplementation} will have its own
 * instance of {@link CurrentImplementationSubcomponent}.
 */
@Subcomponent
@PerComponentImplementation
interface CurrentImplementationSubcomponent {
  RootComponentImplementationBuilder rootComponentBuilder();

  SubcomponentImplementationBuilder subcomponentBuilder();

  @Subcomponent.Builder
  interface Builder {
    @BindsInstance
    Builder componentImplementation(ComponentImplementation componentImplementation);

    @BindsInstance
    Builder bindingGraph(BindingGraph bindingGraph);

    @BindsInstance
    Builder parentBuilder(@ParentComponent Optional<ComponentImplementationBuilder> parentBuilder);

    @BindsInstance
    Builder parentBindingExpressions(
        @ParentComponent Optional<ComponentBindingExpressions> parentBindingExpressions);

    @BindsInstance
    Builder parentRequirementExpressions(
        @ParentComponent Optional<ComponentRequirementExpressions> parentRequirementExpressions);

    CurrentImplementationSubcomponent build();
  }
}
