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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static dagger.internal.codegen.DaggerElements.getAnnotationMirror;
import static dagger.internal.codegen.ErrorMessages.CAN_RELEASE_REFERENCES_ANNOTATIONS_MUST_NOT_HAVE_SOURCE_RETENTION;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import dagger.releasablereferences.CanReleaseReferences;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.SimpleAnnotationValueVisitor7;

/**
 * Validates that {@link CanReleaseReferences} are applied only to valid annotations.
 *
 * <p>They must not annotate annotations that have {@link RetentionPolicy#SOURCE}-level retention.
 */
final class CanReleaseReferencesValidator {

  ValidationReport<TypeElement> validate(TypeElement annotatedElement) {
    ValidationReport.Builder<TypeElement> report = ValidationReport.about(annotatedElement);
    checkNoSourceRetention(annotatedElement, report);
    return report.build();
  }

  private void checkNoSourceRetention(
      TypeElement annotatedElement, ValidationReport.Builder<TypeElement> report) {
    getAnnotationMirror(annotatedElement, Retention.class)
        .ifPresent(
            retention -> {
              if (getRetentionPolicy(retention).equals(SOURCE)) {
                report.addError(
                    CAN_RELEASE_REFERENCES_ANNOTATIONS_MUST_NOT_HAVE_SOURCE_RETENTION,
                    report.getSubject(),
                    retention);
              }
            });
  }

  // TODO(dpb): Move the ability to get an annotation type's retention policy somewhere common.
  private RetentionPolicy getRetentionPolicy(AnnotationMirror retention) {
    return getAnnotationValue(retention, "value")
        .accept(
            new SimpleAnnotationValueVisitor7<RetentionPolicy, Void>() {
              @Override
              public RetentionPolicy visitEnumConstant(VariableElement element, Void p) {
                return RetentionPolicy.valueOf(element.getSimpleName().toString());
              }
            },
            null);
  }
}
