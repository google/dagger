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
import dagger.internal.codegen.base.SourceFileGenerator;
import dagger.internal.codegen.binding.BindingGraph;
import dagger.internal.codegen.binding.MembersInjectionBinding;
import dagger.internal.codegen.binding.ProductionBinding;
import dagger.internal.codegen.binding.ProvisionBinding;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.writing.FactoryGenerator;
import dagger.internal.codegen.writing.HjarSourceFileGenerator;
import dagger.internal.codegen.writing.MembersInjectorGenerator;
import dagger.internal.codegen.writing.ModuleGenerator;
import dagger.internal.codegen.writing.ModuleProxies.ModuleConstructorProxyGenerator;
import dagger.internal.codegen.writing.ProducerFactoryGenerator;
import javax.lang.model.element.TypeElement;

@Module
abstract class SourceFileGeneratorsModule {

  @Provides
  static SourceFileGenerator<ProvisionBinding> provisionBindingGenerator(
      FactoryGenerator generator, CompilerOptions compilerOptions) {
    return hjarWrapper(generator, compilerOptions);
  }

  @Provides
  static SourceFileGenerator<ProductionBinding> productionBindingGenerator(
      ProducerFactoryGenerator generator, CompilerOptions compilerOptions) {
    return hjarWrapper(generator, compilerOptions);
  }

  @Provides
  static SourceFileGenerator<MembersInjectionBinding> membersInjectionBindingGenerator(
      MembersInjectorGenerator generator, CompilerOptions compilerOptions) {
    return hjarWrapper(generator, compilerOptions);
  }

  @Provides
  static SourceFileGenerator<BindingGraph> bindingGraphGenerator(
      ComponentGenerator generator, CompilerOptions compilerOptions) {
    return hjarWrapper(generator, compilerOptions);
  }

  @Provides
  @ModuleGenerator
  static SourceFileGenerator<TypeElement> moduleProxyGenerator(
      ModuleConstructorProxyGenerator generator, CompilerOptions compilerOptions) {
    return hjarWrapper(generator, compilerOptions);
  }

  private static <T> SourceFileGenerator<T> hjarWrapper(
      SourceFileGenerator<T> generator, CompilerOptions compilerOptions) {
    return compilerOptions.headerCompilation()
        ? HjarSourceFileGenerator.wrap(generator)
        : generator;
  }
}
