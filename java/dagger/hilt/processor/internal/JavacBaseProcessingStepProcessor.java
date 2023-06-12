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

package dagger.hilt.processor.internal;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingEnvConfig;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.XRoundEnv;
import androidx.room.compiler.processing.javac.JavacBasicAnnotationProcessor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.lang.model.SourceVersion;

/** A JavacBasicAnnotationProcessor that contains a single BaseProcessingStep. */
public abstract class JavacBaseProcessingStepProcessor extends JavacBasicAnnotationProcessor {
  private static final XProcessingEnvConfig PROCESSING_ENV_CONFIG =
      new XProcessingEnvConfig.Builder().disableAnnotatedElementValidation(true).build();

  private BaseProcessingStep processingStep;

  public JavacBaseProcessingStepProcessor() {
    super(PROCESSING_ENV_CONFIG);
  }

  @Override
  public void initialize(XProcessingEnv env) {
    HiltCompilerOptions.checkWrongAndDeprecatedOptions(env);
    processingStep = processingStep();
  }

  @Override
  public final SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public final ImmutableSet<String> getSupportedOptions() {
    // This is declared here rather than in the actual processors because KAPT will issue a
    // warning if any used option is not unsupported. This can happen when there is a module
    // which uses Hilt but lacks any @AndroidEntryPoint annotations.
    // See: https://github.com/google/dagger/issues/2040
    return ImmutableSet.<String>builder()
        .addAll(HiltCompilerOptions.getProcessorOptions())
        .addAll(additionalProcessingOptions())
        .build();
  }

  @Override
  public final ImmutableList<XProcessingStep> processingSteps() {
    return ImmutableList.of(processingStep);
  }

  @Override
  public void preRound(XProcessingEnv env, XRoundEnv round) {
    processingStep.preRoundProcess(env, round);
  }

  protected abstract BaseProcessingStep processingStep();

  @Override
  public void postRound(XProcessingEnv env, XRoundEnv round) {
    processingStep.postRoundProcess(env, round);
  }

  /** Returns additional processing options that should only be applied for a single processor. */
  protected Set<String> additionalProcessingOptions() {
    return ImmutableSet.of();
  }
}
