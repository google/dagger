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
import dagger.internal.codegen.SourceFileGeneratorsModule.ComponentModule;
import dagger.internal.codegen.SourceFileGeneratorsModule.MembersInjectionModule;
import dagger.internal.codegen.SourceFileGeneratorsModule.ProductionModule;
import dagger.internal.codegen.SourceFileGeneratorsModule.ProvisionModule;
import javax.lang.model.element.TypeElement;

@Module(
    includes = {
      ProvisionModule.class,
      ProductionModule.class,
      MembersInjectionModule.class,
      ComponentModule.class
    })
interface SourceFileGeneratorsModule {
  @Module
  abstract class GeneratorModule<T, G extends SourceFileGenerator<T>> {
    @Provides
    SourceFileGenerator<T> generator(G generator, CompilerOptions compilerOptions) {
      return compilerOptions.headerCompilation()
          ? HjarSourceFileGenerator.wrap(generator)
          : generator;
    }
  }

  @Module
  class ProvisionModule extends GeneratorModule<ProvisionBinding, FactoryGenerator> {}

  @Module
  class ProductionModule extends GeneratorModule<ProductionBinding, ProducerFactoryGenerator> {}

  @Module
  class MembersInjectionModule
      extends GeneratorModule<MembersInjectionBinding, MembersInjectorGenerator> {}

  @Module
  class ComponentModule extends GeneratorModule<BindingGraph, ComponentGenerator> {}

  // the abstract module is not available because we're using a qualifier
  @Provides
  @ModuleGenerator
  static SourceFileGenerator<TypeElement> generator(
      ModuleConstructorProxyGenerator generator, CompilerOptions compilerOptions) {
    return compilerOptions.headerCompilation()
        ? HjarSourceFileGenerator.wrap(generator)
        : generator;
  }
}
