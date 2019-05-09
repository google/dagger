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

import dagger.Module;
import dagger.Provides;
import dagger.internal.GenerationOptions;
import dagger.internal.codegen.langmodel.DaggerElements;
import java.util.Optional;

/** Adds bindings for serializing and rereading {@link GenerationOptions}. */
@Module
interface GenerationOptionsModule {
  @Provides
  @PerComponentImplementation
  @GenerationCompilerOptions
  static CompilerOptions generationOptions(
      CompilerOptions defaultOptions,
      ComponentImplementation componentImplementation,
      DaggerElements elements) {
    // Avoid looking up types that don't exist. Performance improves for large components.
    if (!defaultOptions.aheadOfTimeSubcomponents()) {
      return defaultOptions;
    }
    // Inspect the base implementation for the @GenerationOptions annotation. Even if
    // componentImplementation is the base implementation, inspect it for the case where we are
    // recomputing the ComponentImplementation from a previous compilation.
    // TODO(b/117833324): consider adding a method that returns baseImplementation.orElse(this).
    // The current state of the world is a little confusing and maybe not intuitive: the base
    // implementation has no base implementation, but it _is_ a base implementation.
    return Optional.of(componentImplementation.baseImplementation().orElse(componentImplementation))
        .map(baseImplementation -> elements.getTypeElement(baseImplementation.name()))
        // If this returns null, the type has not been generated yet and Optional will switch to an
        // empty state. This means that we're currently generating componentImplementation, or that
        // the base implementation is being generated in this round, and thus the options passed to
        // this compilation are applicable
        .map(typeElement -> typeElement.getAnnotation(GenerationOptions.class))
        .map(defaultOptions::withGenerationOptions)
        .orElse(defaultOptions);
  }
}
