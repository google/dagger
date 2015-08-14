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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

import static dagger2.internal.codegen.ErrorMessages.ABSTRACT_INJECT_METHOD;
import static dagger2.internal.codegen.ErrorMessages.GENERIC_INJECT_METHOD;
import static dagger2.internal.codegen.ErrorMessages.MULTIPLE_QUALIFIERS;
import static dagger2.internal.codegen.ErrorMessages.PRIVATE_INJECT_METHOD;
import static dagger2.internal.codegen.InjectionAnnotations.getQualifiers;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;

/**
 * A {@link Validator} for {@link Inject} methods.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class InjectMethodValidator implements Validator<ExecutableElement> {
  @Override
  public ValidationReport<ExecutableElement> validate(ExecutableElement methodElement) {
    ValidationReport.Builder<ExecutableElement> builder =
        ValidationReport.Builder.about(methodElement);
    Set<Modifier> modifiers = methodElement.getModifiers();
    if (modifiers.contains(ABSTRACT)) {
      builder.addItem(ABSTRACT_INJECT_METHOD, methodElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(PRIVATE_INJECT_METHOD, methodElement);
    }

    if (!methodElement.getTypeParameters().isEmpty()) {
      builder.addItem(GENERIC_INJECT_METHOD, methodElement);
    }

    for (VariableElement parameter : methodElement.getParameters()) {
      ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(parameter);
      if (qualifiers.size() > 1) {
        for (AnnotationMirror qualifier : qualifiers) {
          builder.addItem(MULTIPLE_QUALIFIERS, methodElement, qualifier);
        }
      }
    }

    return builder.build();
  }
}
