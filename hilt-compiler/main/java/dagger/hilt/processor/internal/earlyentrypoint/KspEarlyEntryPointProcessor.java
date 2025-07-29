/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.processor.internal.earlyentrypoint;

import com.google.auto.service.AutoService;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import dagger.hilt.processor.internal.KspBaseProcessingStepProcessor;

/** Validates {@link dagger.hilt.android.EarlyEntryPoint} usages. */
public final class KspEarlyEntryPointProcessor extends KspBaseProcessingStepProcessor {

  public KspEarlyEntryPointProcessor(SymbolProcessorEnvironment symbolProcessorEnvironment) {
    super(symbolProcessorEnvironment);
  }

  @Override
  public EarlyEntryPointProcessingStep processingStep() {
    return new EarlyEntryPointProcessingStep(getXProcessingEnv());
  }

  /** Provides the {@link KspEarlyEntryPointProcessor}. */
  @AutoService(SymbolProcessorProvider.class)
  public static final class Provider implements SymbolProcessorProvider {
    @Override
    public SymbolProcessor create(SymbolProcessorEnvironment symbolProcessorEnvironment) {
      return new KspEarlyEntryPointProcessor(symbolProcessorEnvironment);
    }
  }
}
