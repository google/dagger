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

package dagger.internal.codegen.bindinggraphvalidation;

import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.model.BindingGraphPlugin;
import dagger.internal.codegen.validation.Validation;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugin;

/** Binds the set of {@link BindingGraphPlugin}s used to implement Dagger validation. */
@Module
public interface BindingGraphValidationModule {

  @Provides
  @Validation
  static ImmutableSet<ValidationBindingGraphPlugin> providePlugins(
      CompositeBindingGraphPlugin.Factory factory,
      CompilerOptions compilerOptions,
      DependencyCycleValidator dependencyCycleValidator,
      DependsOnProductionExecutorValidator dependsOnProductionExecutorValidator,
      DuplicateBindingsValidator duplicateBindingsValidator,
      IncompatiblyScopedBindingsValidator incompatiblyScopedBindingsValidator,
      InjectBindingValidator injectBindingValidator,
      MapMultibindingValidator mapMultibindingValidator,
      MissingBindingValidator missingBindingValidator,
      NullableBindingValidator nullableBindingValidator,
      ProvisionDependencyOnProducerBindingValidator provisionDependencyOnProducerBindingValidator,
      InvalidProductionBindingScopeValidator invalidProductionBindingScopeValidator,
      SetMultibindingValidator setMultibindingValidator,
    SubcomponentFactoryMethodValidator subcomponentFactoryMethodValidator) {
    ImmutableSet.Builder<ValidationBindingGraphPlugin> builder =
        ImmutableSet.<ValidationBindingGraphPlugin>builder()
            .add(dependencyCycleValidator)
            .add(dependsOnProductionExecutorValidator)
            .add(duplicateBindingsValidator)
            .add(incompatiblyScopedBindingsValidator)
            .add(injectBindingValidator)
            .add(mapMultibindingValidator)
            .add(missingBindingValidator)
            .add(nullableBindingValidator)
            .add(provisionDependencyOnProducerBindingValidator)
            .add(invalidProductionBindingScopeValidator)
            .add(setMultibindingValidator)
            .add(subcomponentFactoryMethodValidator);

    if (compilerOptions.experimentalDaggerErrorMessages()) {
      return ImmutableSet.of(factory.create(builder.build()));
    } else {
      return builder.build();
    }
  }
}
