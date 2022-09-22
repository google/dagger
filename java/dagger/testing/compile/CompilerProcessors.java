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

package dagger.testing.compile;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.javac.JavacBasicAnnotationProcessor;
import androidx.room.compiler.processing.ksp.KspBasicAnnotationProcessor;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import javax.lang.model.SourceVersion;

/**
 * A Javac and KSP processor to be used with the {@link CompilerTests.DaggerCompiler} to allow
 * running custom processing steps during compilation tests.
 */
final class CompilerProcessors {
  /** A Javac processor that runs the given processing steps. */
  static final class JavacProcessor extends JavacBasicAnnotationProcessor {
    private final ImmutableCollection<XProcessingStep> processingSteps;

    JavacProcessor(ImmutableCollection<XProcessingStep> processingSteps) {
      super(options -> CompilerTests.PROCESSING_ENV_CONFIG);
      this.processingSteps = processingSteps;
    }

    @Override
    public void initialize(XProcessingEnv env) {}

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public ImmutableSet<String> getSupportedOptions() {
      return ImmutableSet.of();
    }

    @Override
    public ImmutableCollection<XProcessingStep> processingSteps() {
      return processingSteps;
    }

    @Override
    public void postRound(XProcessingEnv env, XRoundEnv roundEnv) {}
  }

  /** A KSP processor that runs the given processing steps. */
  static final class KspProcessor extends KspBasicAnnotationProcessor {
    private final ImmutableCollection<XProcessingStep> processingSteps;

    private KspProcessor(
        SymbolProcessorEnvironment symbolProcessorEnvironment,
        ImmutableCollection<XProcessingStep> processingSteps) {
      super(symbolProcessorEnvironment, CompilerTests.PROCESSING_ENV_CONFIG);
      this.processingSteps = processingSteps;
    }

    @Override
    public void initialize(XProcessingEnv env) {}

    @Override
    public ImmutableCollection<XProcessingStep> processingSteps() {
      return processingSteps;
    }

    @Override
    public void postRound(XProcessingEnv env, XRoundEnv roundEnv) {}

    /** Provides the {@link KspComponentProcessor}. */
    static final class Provider implements SymbolProcessorProvider {
      private final ImmutableCollection<XProcessingStep> processingSteps;

      Provider(ImmutableCollection<XProcessingStep> processingSteps) {
        this.processingSteps = processingSteps;
      }

      @Override
      public SymbolProcessor create(SymbolProcessorEnvironment symbolProcessorEnvironment) {
        return new KspProcessor(symbolProcessorEnvironment, processingSteps);
      }
    }
  }

  private CompilerProcessors() {}
}
