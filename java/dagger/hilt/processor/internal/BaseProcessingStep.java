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

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableMap;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XProcessingStep;
import androidx.room.compiler.processing.XRoundEnv;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Implements default configurations for ProcessingSteps, and provides structure for exception
 * handling.
 *
 * <p>In each round it will do the following:
 *
 * <ol>
 *   <li>#preProcess()
 *   <li>foreach element:
 *       <ul>
 *         <li>#processEach()
 *       </ul>
 *   <li>#postProcess()
 * </ol>
 *
 * <p>#processEach() allows each element to be processed, even if exceptions are thrown. Due to the
 * non-deterministic ordering of the processed elements, this is needed to ensure a consistent set
 * of exceptions are thrown with each build.
 */
public abstract class BaseProcessingStep implements XProcessingStep {
  private final ProcessorErrorHandler errorHandler;

  private final XProcessingEnv processingEnv;

  public BaseProcessingStep(XProcessingEnv env) {
    errorHandler = new ProcessorErrorHandler(env);
    processingEnv = env;
  }

  protected final XProcessingEnv processingEnv() {
    return processingEnv;
  }

  @Override
  public final ImmutableSet<String> annotations() {
    return annotationClassNames().stream().map(ClassName::canonicalName).collect(toImmutableSet());
  }

  protected abstract Set<ClassName> annotationClassNames();

  protected abstract void processEach(ClassName annotation, XElement element) throws Exception;

  protected void preProcess(XProcessingEnv env, XRoundEnv round) {}

  protected void postProcess(XProcessingEnv env, XRoundEnv round) throws Exception {}

  public final void preRoundProcess(XProcessingEnv env, XRoundEnv round) {
    preProcess(env, round);
  }

  public final void postRoundProcess(XProcessingEnv env, XRoundEnv round) {
    if (errorHandler.isEmpty()) {
      try {
        postProcess(env, round);
      } catch (Exception e) {
        errorHandler.recordError(e);
      }
    }
    if (!delayErrors() || round.isProcessingOver()) {
      errorHandler.checkErrors();
    }
  }

  @Override
  public final ImmutableSet<XElement> process(
      XProcessingEnv env,
      Map<String, ? extends Set<? extends XElement>> elementsByAnnotation,
      boolean isLastRound) {
    ImmutableMap<String, ClassName> annotationClassNamesByName =
        annotationClassNames().stream()
            .collect(toImmutableMap(ClassName::canonicalName, Function.identity()));
    ImmutableSet.Builder<XElement> elementsToReprocessBuilder = ImmutableSet.builder();
    for (String annotationName : annotations()) {
      Set<? extends XElement> elements = elementsByAnnotation.get(annotationName);
      if (elements != null) {
        for (XElement element : elements) {
          try {
            processEach(annotationClassNamesByName.get(annotationName), element);
          } catch (Exception e) {
            if (e instanceof ErrorTypeException && !isLastRound) {
              // Allow an extra round to reprocess to try to resolve this type.
              elementsToReprocessBuilder.add(element);
            } else {
              errorHandler.recordError(e);
            }
          }
        }
      }
    }
    return elementsToReprocessBuilder.build();
  }

  /**
   * Returns true if you want to delay errors to the last round. Useful if the processor generates
   * code for symbols used a lot in the user code. Delaying allows as much code to compile as
   * possible for correctly configured types and reduces error spam.
   */
  protected boolean delayErrors() {
    return false;
  }
}
