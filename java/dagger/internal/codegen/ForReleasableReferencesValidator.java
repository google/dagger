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
import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.ErrorMessages.forReleasableReferencesValueCannotReleaseReferences;
import static dagger.internal.codegen.ErrorMessages.forReleasableReferencesValueNotAScope;
import static dagger.internal.codegen.MoreAnnotationMirrors.getTypeValue;
import static dagger.internal.codegen.Scope.isScope;
import static dagger.internal.codegen.Scope.scope;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.releasablereferences.ForReleasableReferences;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** Validates uses of {@link ForReleasableReferences @ForReleasableReferences}. */
final class ForReleasableReferencesValidator implements ProcessingStep {

  private final Messager messager;

  ForReleasableReferencesValidator(Messager messager) {
    this.messager = messager;
  }

  ValidationReport<Element> validateAnnotatedElement(Element annotatedElement) {
    checkArgument(isAnnotationPresent(annotatedElement, ForReleasableReferences.class));
    ValidationReport.Builder<Element> report = ValidationReport.about(annotatedElement);
    AnnotationMirror annotation =
        getAnnotationMirror(annotatedElement, ForReleasableReferences.class).get();
    TypeElement scopeType = MoreTypes.asTypeElement(getTypeValue(annotation, "value"));
    if (!isScope(scopeType)) {
      report.addError(
          forReleasableReferencesValueNotAScope(scopeType), annotatedElement, annotation);
    } else if (!scope(scopeType).canReleaseReferences()) {
      report.addError(
          forReleasableReferencesValueCannotReleaseReferences(scopeType),
          annotatedElement,
          annotation);
    }
    return report.build();
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(ForReleasableReferences.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    elementsByAnnotation
        .get(ForReleasableReferences.class)
        .stream()
        .map(this::validateAnnotatedElement)
        .forEach(report -> report.printMessagesTo(messager));
    return ImmutableSet.of();
  }
}
