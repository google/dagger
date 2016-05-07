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

import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_THROWS_CHECKED;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.TYPEVAR;

final class Validation {

  private Validation() {}

  /** Validates that the given method only throws unchecked exceptions. */
  static void validateUncheckedThrows(
      Elements elements,
      Types types,
      ExecutableElement methodElement,
      Class<? extends Annotation> methodAnnotation,
      ValidationReport.Builder<ExecutableElement> builder) {
    TypeMirror runtimeExceptionType =
        elements.getTypeElement(RuntimeException.class.getCanonicalName()).asType();
    TypeMirror errorType = elements.getTypeElement(Error.class.getCanonicalName()).asType();
    for (TypeMirror thrownType : methodElement.getThrownTypes()) {
      if (!types.isSubtype(thrownType, runtimeExceptionType)
          && !types.isSubtype(thrownType, errorType)) {
        builder.addError(
            String.format(BINDING_METHOD_THROWS_CHECKED, methodAnnotation.getSimpleName()),
            methodElement);
        break;
      }
    }
  }

  /** Validates that the return type of a binding method is an acceptable kind. */
  static void validateReturnType(
      Class<? extends Annotation> methodAnnotation,
      ValidationReport.Builder<? extends Element> reportBuilder,
      TypeMirror returnType) {
    TypeKind kind = returnType.getKind();
    if (!(kind.isPrimitive()
        || kind.equals(DECLARED)
        || kind.equals(ARRAY)
        || kind.equals(TYPEVAR))) {
      reportBuilder.addError(
          String.format(BINDING_METHOD_RETURN_TYPE, methodAnnotation.getSimpleName()),
          reportBuilder.getSubject());
    }
  }

  /** Validates that a Provides, Produces or Bind method doesn't have multiple qualifiers. */
  static void validateMethodQualifiers(
      ValidationReport.Builder<ExecutableElement> builder, ExecutableElement methodElement) {
    ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(methodElement);
    if (qualifiers.size() > 1) {
      for (AnnotationMirror qualifier : qualifiers) {
        builder.addError(BINDING_METHOD_MULTIPLE_QUALIFIERS, methodElement, qualifier);
      }
    }
  }
}
