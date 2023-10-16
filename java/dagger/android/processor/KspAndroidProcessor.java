/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.android.processor;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.ksp.KspBasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;

/** Ksp Processor for verifying usage of {@code dagger.android} code. */
final class KspAndroidProcessor extends KspBasicAnnotationProcessor {
  private final DelegateAndroidProcessor delegate = new DelegateAndroidProcessor();

  private KspAndroidProcessor(SymbolProcessorEnvironment symbolProcessorEnvironment) {
    super(symbolProcessorEnvironment, DelegateAndroidProcessor.PROCESSING_ENV_CONFIG);
  }

  @Override
  public void initialize(XProcessingEnv env) {
    delegate.initialize(env);
  }

  @Override
  public Iterable<XProcessingStep> processingSteps() {
    return delegate.processingSteps();
  }

  /** Provides the {@link KspAndroidProcessor}. */
  @AutoService(SymbolProcessorProvider.class)
  public static final class Provider implements SymbolProcessorProvider {
    @Override
    public SymbolProcessor create(SymbolProcessorEnvironment symbolProcessorEnvironment) {
      return new KspAndroidProcessor(symbolProcessorEnvironment);
    }
  }
}
