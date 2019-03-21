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

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;
import dagger.spi.BindingGraphPlugin;

/** Binds the set of {@link BindingGraphPlugin}s used to implement Dagger validation. */
@Module
interface BindingGraphValidationModule {

  @Binds
  @IntoSet
  @Validation
  BindingGraphPlugin dependencyCycle(DependencyCycleValidator validation);

  @Binds
  @IntoSet
  @Validation
  BindingGraphPlugin dependsOnProductionExecutor(DependsOnProductionExecutorValidator validation);

  @Binds
  @IntoSet
  @Validation
  BindingGraphPlugin duplicateBindings(DuplicateBindingsValidator validation);

  @Binds
  @IntoSet
  @Validation
  BindingGraphPlugin incompatiblyScopedBindings(IncompatiblyScopedBindingsValidator validation);

  @Binds
  @IntoSet
  @Validation
  BindingGraphPlugin injectBinding(InjectBindingValidator validation);

  @Binds
  @IntoSet
  @Validation
  BindingGraphPlugin mapMultibinding(MapMultibindingValidator validation);

  @Binds
  @IntoSet
  @Validation
  BindingGraphPlugin missingBinding(MissingBindingValidator validation);

  @Binds
  @IntoSet
  @Validation
  BindingGraphPlugin nullableBinding(NullableBindingValidator validation);

  @Binds
  @IntoSet
  @Validation
  BindingGraphPlugin provisionDependencyOnProducerBinding(
      ProvisionDependencyOnProducerBindingValidator validation);

  @Binds
  @IntoSet
  @Validation
  BindingGraphPlugin subcomponentFactoryMethod(SubcomponentFactoryMethodValidator validation);
}
