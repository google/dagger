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
    return componentImplementation
        .baseImplementation()
        .map(baseImplementation -> elements.getTypeElement(baseImplementation.name()))
        .map(typeElement -> typeElement.getAnnotation(GenerationOptions.class))
        .map(defaultOptions::withGenerationOptions)
        .orElse(defaultOptions);
  }
}
