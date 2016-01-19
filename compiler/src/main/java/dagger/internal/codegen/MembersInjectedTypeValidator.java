/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/** Validates the {@code @Inject} fields and methods in a class and its superclasses. */
final class MembersInjectedTypeValidator {
  private final InjectFieldValidator fieldValidator;
  private final InjectMethodValidator methodValidator;

  MembersInjectedTypeValidator(
      InjectFieldValidator fieldValidator, InjectMethodValidator methodValidator) {
    this.fieldValidator = fieldValidator;
    this.methodValidator = methodValidator;
  }

  ValidationReport<TypeElement> validate(TypeElement typeElement) {
    // TODO(beder): This element might not be currently compiled, so this error message could be
    // left in limbo. Find an appropriate way to display the error message in that case.
    ValidationReport.Builder<TypeElement> builder = ValidationReport.about(typeElement);
    for (VariableElement element : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, Inject.class)) {
        ValidationReport<VariableElement> report = fieldValidator.validate(element);
        if (!report.isClean()) {
          builder.addSubreport(report);
        }
      }
    }
    for (ExecutableElement element : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, Inject.class)) {
        ValidationReport<ExecutableElement> report = methodValidator.validate(element);
        if (!report.isClean()) {
          builder.addSubreport(report);
        }
      }
    }
    TypeMirror superclass = typeElement.getSuperclass();
    if (!superclass.getKind().equals(TypeKind.NONE)) {
      ValidationReport<TypeElement> report = validate(MoreTypes.asTypeElement(superclass));
      if (!report.isClean()) {
        builder.addSubreport(report);
      }
    }
    return builder.build();
  }
}
