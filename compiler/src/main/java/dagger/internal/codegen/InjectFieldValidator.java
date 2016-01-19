/*
 * Copyright (C) 2014 Google, Inc.
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
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;

import static dagger.internal.codegen.ErrorMessages.FINAL_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.STATIC_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.provisionMayNotDependOnProducerType;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * A {@linkplain ValidationReport validator} for {@link Inject} fields.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class InjectFieldValidator {
  private Kind privateMemberValidationKind;
  private Kind staticMemberValidationKind;
  
  public InjectFieldValidator(
      Kind privateMemberValidationKind, Kind staticMemberValidationKind) {
    this.privateMemberValidationKind = privateMemberValidationKind;
    this.staticMemberValidationKind = staticMemberValidationKind;
  }

  ValidationReport<VariableElement> validate(VariableElement fieldElement) {
    ValidationReport.Builder<VariableElement> builder = ValidationReport.about(fieldElement);
    Set<Modifier> modifiers = fieldElement.getModifiers();
    if (modifiers.contains(FINAL)) {
      builder.addError(FINAL_INJECT_FIELD, fieldElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(PRIVATE_INJECT_FIELD, privateMemberValidationKind, fieldElement);
    }

    if (modifiers.contains(STATIC)) {
      builder.addItem(STATIC_INJECT_FIELD, staticMemberValidationKind, fieldElement);
    }
    
    ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(fieldElement);
    if (qualifiers.size() > 1) {
      for (AnnotationMirror qualifier : qualifiers) {
        builder.addError(MULTIPLE_QUALIFIERS, fieldElement, qualifier);
      }
    }

    if (FrameworkTypes.isProducerType(fieldElement.asType())) {
      builder.addError(provisionMayNotDependOnProducerType(fieldElement.asType()), fieldElement);
    }

    return builder.build();
  }
}
