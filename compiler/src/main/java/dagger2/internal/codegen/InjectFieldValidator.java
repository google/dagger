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
package dagger2.internal.codegen;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

import static dagger2.internal.codegen.ErrorMessages.FINAL_INJECT_FIELD;
import static dagger2.internal.codegen.ErrorMessages.MULTIPLE_QUALIFIERS;
import static dagger2.internal.codegen.ErrorMessages.PRIVATE_INJECT_FIELD;
import static dagger2.internal.codegen.InjectionAnnotations.getQualifiers;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;

/**
 * A {@link Validator} for {@link Inject} fields.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class InjectFieldValidator implements Validator<VariableElement> {
  @Override
  public ValidationReport<VariableElement> validate(VariableElement fieldElement) {
    ValidationReport.Builder<VariableElement> builder =
        ValidationReport.Builder.about(fieldElement);
    Set<Modifier> modifiers = fieldElement.getModifiers();
    if (modifiers.contains(FINAL)) {
      builder.addItem(FINAL_INJECT_FIELD, fieldElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(PRIVATE_INJECT_FIELD, fieldElement);
    }

    ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(fieldElement);
    if (qualifiers.size() > 1) {
      for (AnnotationMirror qualifier : qualifiers) {
        builder.addItem(MULTIPLE_QUALIFIERS, fieldElement, qualifier);
      }
    }

    return builder.build();
  }
}
