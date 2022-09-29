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

package dagger.internal.codegen;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.ksp.KspBasicAnnotationProcessor;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;

/**
 * The KSP processor responsible for generating the classes that drive the Dagger implementation.
 */
public final class KspComponentProcessor extends KspBasicAnnotationProcessor {
  private final DelegateComponentProcessor delegate = new DelegateComponentProcessor();

  private KspComponentProcessor(SymbolProcessorEnvironment symbolProcessorEnvironment) {
    super(symbolProcessorEnvironment, DelegateComponentProcessor.PROCESSING_ENV_CONFIG);
  }

  @Override
  public void initialize(XProcessingEnv env) {
    // TODO(bcorso): Support external usage of dagger.spi.model.BindingGraphPlugin.
    delegate.initialize(
        env,
        // LegacyExternalPlugins are only supported with Javac.
        /* legacyExternalPlugins= */ ImmutableSet.of());
  }

  @Override
  public Iterable<XProcessingStep> processingSteps() {
    return delegate.processingSteps();
  }

  @Override
  public void postRound(XProcessingEnv env, XRoundEnv roundEnv) {
    delegate.postRound(env, roundEnv);
  }

  /** Provides the {@link KspComponentProcessor}. */
  public static final class Provider implements SymbolProcessorProvider {
    @Override
    public SymbolProcessor create(SymbolProcessorEnvironment symbolProcessorEnvironment) {
      return new KspComponentProcessor(symbolProcessorEnvironment);
    }
  }
}
