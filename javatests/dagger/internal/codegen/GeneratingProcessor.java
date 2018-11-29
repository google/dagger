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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/** A simple {@link Processor} that generates one source file. */
final class GeneratingProcessor extends AbstractProcessor {
  private final String generatedClassName;
  private final String generatedSource;
  private boolean processed;

  GeneratingProcessor(String generatedClassName, String... source) {
    this.generatedClassName = generatedClassName;
    this.generatedSource = Joiner.on("\n").join(source);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of("*");
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (!processed) {
      processed = true;
      try (Writer writer =
          processingEnv.getFiler().createSourceFile(generatedClassName).openWriter()) {
        writer.append(generatedSource);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return false;
  }
}
