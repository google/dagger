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

package dagger.hilt.android.testing.compile;

import androidx.room.compiler.processing.XProcessingEnv;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.processing.SymbolProcessorProvider;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.JavacBaseProcessingStepProcessor;
import dagger.hilt.processor.internal.KspBaseProcessingStepProcessor;
import java.util.function.Function;

/**
 * A Javac and KSP processor to be used with the {@link HiltCompilerTests.hiltCompiler} to allow
 * running custom processing steps during compilation tests.
 */
final class HiltCompilerProcessors {
  /** A JavacBasicAnnotationProcessor that contains a single BaseProcessingStep. */
  static final class JavacProcessor extends JavacBaseProcessingStepProcessor {
    private final Function<XProcessingEnv, BaseProcessingStep> processingStep;

    JavacProcessor(Function<XProcessingEnv, BaseProcessingStep> processingStep) {
      this.processingStep = processingStep;
    }

    @Override
    public BaseProcessingStep processingStep() {
      return processingStep.apply(getXProcessingEnv());
    }
  }

  /** A KSP processor that runs the given processing steps. */
  static final class KspProcessor extends KspBaseProcessingStepProcessor {
    private final Function<XProcessingEnv, BaseProcessingStep> processingStep;

    private KspProcessor(
        SymbolProcessorEnvironment symbolProcessorEnvironment,
        Function<XProcessingEnv, BaseProcessingStep> processingStep) {
      super(symbolProcessorEnvironment);
      this.processingStep = processingStep;
    }

    @Override
    public BaseProcessingStep processingStep() {
      return processingStep.apply(getXProcessingEnv());
    }

    /** Provides the {@link KspComponentProcessor}. */
    static final class Provider implements SymbolProcessorProvider {
      private final Function<XProcessingEnv, BaseProcessingStep> processingStep;

      Provider(Function<XProcessingEnv, BaseProcessingStep> processingStep) {
        this.processingStep = processingStep;
      }

      @Override
      public SymbolProcessor create(SymbolProcessorEnvironment symbolProcessorEnvironment) {
        return new KspProcessor(symbolProcessorEnvironment, processingStep);
      }
    }
  }

  private HiltCompilerProcessors() {}
}
