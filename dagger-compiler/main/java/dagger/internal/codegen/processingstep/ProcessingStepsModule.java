/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.internal.codegen.processingstep;

import androidx.room.compiler.processing.XProcessingStep;
import com.google.common.collect.ImmutableList;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.multibindings.IntoSet;

/** A module that provides the list of processing steps in the expected order. */
@Module
public interface ProcessingStepsModule {
  @Provides
  static ImmutableList<XProcessingStep> processingSteps(
      MapKeyProcessingStep mapKeyProcessingStep,
      InjectProcessingStep injectProcessingStep,
      AssistedInjectProcessingStep assistedInjectProcessingStep,
      AssistedFactoryProcessingStep assistedFactoryProcessingStep,
      AssistedProcessingStep assistedProcessingStep,
      MonitoringModuleProcessingStep monitoringModuleProcessingStep,
      MultibindingAnnotationsProcessingStep multibindingAnnotationsProcessingStep,
      BindsInstanceProcessingStep bindsInstanceProcessingStep,
      ModuleProcessingStep moduleProcessingStep,
      LazyClassKeyProcessingStep lazyClassKeyProcessingStep,
      ComponentProcessingStep componentProcessingStep,
      ComponentHjarProcessingStep componentHjarProcessingStep,
      BindingMethodProcessingStep bindingMethodProcessingStep,
      CompilerOptions compilerOptions) {
    return ImmutableList.of(
        mapKeyProcessingStep,
        injectProcessingStep,
        assistedInjectProcessingStep,
        assistedFactoryProcessingStep,
        assistedProcessingStep,
        monitoringModuleProcessingStep,
        multibindingAnnotationsProcessingStep,
        bindsInstanceProcessingStep,
        moduleProcessingStep,
        lazyClassKeyProcessingStep,
        compilerOptions.headerCompilation() ? componentHjarProcessingStep : componentProcessingStep,
        bindingMethodProcessingStep);
  }

  @Binds
  @IntoSet
  ClearableCache superficialValidator(SuperficialValidator cache);
}
