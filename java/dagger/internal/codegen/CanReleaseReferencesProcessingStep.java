/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static javax.lang.model.util.ElementFilter.typesIn;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.releasablereferences.CanReleaseReferences;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.inject.Scope;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Processes annotations annotated with {@link CanReleaseReferences}. For each one that is not also
 * a {@link Scope}, generates a class that can create instances at runtime.
 */
final class CanReleaseReferencesProcessingStep implements ProcessingStep {

  private final Messager messager;
  private final CanReleaseReferencesValidator canReleaseReferencesValidator;
  private final AnnotationCreatorGenerator annotationCreatorGenerator;

  @Inject
  CanReleaseReferencesProcessingStep(
      Messager messager,
      CanReleaseReferencesValidator canReleaseReferencesValidator,
      AnnotationCreatorGenerator annotationCreatorGenerator) {
    this.messager = messager;
    this.canReleaseReferencesValidator = canReleaseReferencesValidator;
    this.annotationCreatorGenerator = annotationCreatorGenerator;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(CanReleaseReferences.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (TypeElement annotatedElement :
        typesIn(elementsByAnnotation.get(CanReleaseReferences.class))) {
      ValidationReport<TypeElement> report =
          canReleaseReferencesValidator.validate(annotatedElement);
      report.printMessagesTo(messager);
      if (report.isClean() && !isAnnotationPresent(annotatedElement, Scope.class)) {
        annotationCreatorGenerator.generate(annotatedElement, messager);
      }
    }
    return ImmutableSet.of();
  }
}
