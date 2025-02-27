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
import dagger.Subcomponent;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.writing.ComponentImplementation;
import dagger.internal.codegen.writing.PerGeneratedFile;
import dagger.internal.codegen.writing.TopLevel;

/**
 * A shared subcomponent for a top-level {@link ComponentImplementation} and any nested child
 * implementations.
 */
@PerGeneratedFile
@Subcomponent
// This only needs to be public because the type is referenced by generated component.
public interface TopLevelImplementationComponent {
  CurrentImplementationSubcomponent.Builder currentImplementationSubcomponentBuilder();

  /** Returns the builder for {@link TopLevelImplementationComponent}. */
  @Subcomponent.Factory
  interface Factory {
    TopLevelImplementationComponent create(@BindsInstance @TopLevel BindingGraph bindingGraph);
  }
}
