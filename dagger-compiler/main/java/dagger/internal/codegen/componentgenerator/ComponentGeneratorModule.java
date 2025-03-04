/*
 * Copyright (C) 2020 The Dagger Authors.
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

import androidx.room.compiler.processing.XProcessingEnv;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.base.SourceFileHjarGenerator;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.ComponentDescriptor;

/** Provides bindings needed to generated the component. */
@Module(subcomponents = TopLevelImplementationComponent.class)
public interface ComponentGeneratorModule {

  @Binds
  abstract SourceFileGenerator<BindingGraph> componentGenerator(ComponentGenerator generator);

  // The SourceFileHjarGenerator wrapper first generates the entire TypeSpec before stripping out
  // things that aren't needed for the hjar. However, this can be really expensive for the component
  // because it is usually the most expensive file to generate, and most of its content is not
  // needed in the hjar. Thus, we provide a completely separate processing step,
  // ComponentHjarProcessingStep and ComponentHjarGenerator, for when generating hjars for
  // components, which can avoid generating the parts of the component that would have been stripped
  // out by the HjarSourceFileGenerator anyway. Note that we still wrap ComponentHjarGenerator in
  // SourceFileHjarGenerator because it adds in constructor and method bodies that are needed for
  // Javac to compile correctly, e.g. super(...) calls in the constructor and return statements in
  // methods.
  @Provides
  static SourceFileGenerator<ComponentDescriptor> componentHjarGenerator(
      XProcessingEnv processingEnv,
      ComponentHjarGenerator hjarGenerator) {
    // Note: technically the ComponentHjarGenerator is already in hjar form, but the
    // SourceFileHjarGenerator wrapper adds in proper method bodies, e.g. constructors that require
    // super() calls or methods that require return statements.
    return SourceFileHjarGenerator.wrap(hjarGenerator, processingEnv);
  }
}
