/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.processor.internal.testroot;

import static net.ltgt.gradle.incap.IncrementalAnnotationProcessorType.ISOLATING;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.android.processor.internal.AndroidClassNames;
import dagger.hilt.processor.internal.BaseProcessor;
import dagger.hilt.processor.internal.ProcessorErrors;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor;

/** Processor for Hilt tests. */
@IncrementalAnnotationProcessor(ISOLATING)
@AutoService(Processor.class)
public final class TestRootProcessor extends BaseProcessor {

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(
        );
  }

  @Override
  public void processEach(TypeElement annotation, Element element) throws Exception {
    ProcessorErrors.checkState(element.getKind() == ElementKind.CLASS, element,
        "Only classes can be annotated with a test root.");

    TypeElement testElement = (TypeElement) element;

    ClassName baseApplication = AndroidClassNames.MULTI_DEX_APPLICATION;

    new InternalTestRootGenerator(
            getProcessingEnv(),
            testType(annotation),
            testElement,
            baseApplication)
        .generate();
  }

  private static ClassName testType(TypeElement annotation) {
    ClassName annotationName = ClassName.get(annotation);
    throw new AssertionError("Unknown annotation: " + annotationName);
  }
}
