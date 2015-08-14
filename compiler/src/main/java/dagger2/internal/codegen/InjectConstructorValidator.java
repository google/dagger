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

import com.google.auto.common.MoreElements;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static dagger2.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS;
import static dagger2.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_INNER_CLASS;
import static dagger2.internal.codegen.ErrorMessages.INJECT_INTO_PRIVATE_CLASS;
import static dagger2.internal.codegen.ErrorMessages.INJECT_ON_PRIVATE_CONSTRUCTOR;
import static dagger2.internal.codegen.ErrorMessages.MULTIPLE_INJECT_CONSTRUCTORS;
import static dagger2.internal.codegen.ErrorMessages.MULTIPLE_QUALIFIERS;
import static dagger2.internal.codegen.ErrorMessages.MULTIPLE_SCOPES;
import static dagger2.internal.codegen.ErrorMessages.QUALIFIER_ON_INJECT_CONSTRUCTOR;
import static dagger2.internal.codegen.InjectionAnnotations.getQualifiers;
import static dagger2.internal.codegen.InjectionAnnotations.getScopes;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * A {@link Validator} for {@link Inject} constructors.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class InjectConstructorValidator implements Validator<ExecutableElement> {
  @Override
  public ValidationReport<ExecutableElement> validate(ExecutableElement constructorElement) {
    ValidationReport.Builder<ExecutableElement> builder =
        ValidationReport.Builder.about(constructorElement);
    if (constructorElement.getModifiers().contains(PRIVATE)) {
      builder.addItem(INJECT_ON_PRIVATE_CONSTRUCTOR, constructorElement);
    }

    for (AnnotationMirror qualifier : getQualifiers(constructorElement)) {
      builder.addItem(QUALIFIER_ON_INJECT_CONSTRUCTOR, constructorElement, qualifier);
    }

    for (VariableElement parameter : constructorElement.getParameters()) {
      ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(parameter);
      if (qualifiers.size() > 1) {
        for (AnnotationMirror qualifier : qualifiers) {
          builder.addItem(MULTIPLE_QUALIFIERS, constructorElement, qualifier);
        }
      }
    }

    TypeElement enclosingElement =
        MoreElements.asType(constructorElement.getEnclosingElement());
    Set<Modifier> typeModifiers = enclosingElement.getModifiers();

    if (typeModifiers.contains(PRIVATE)) {
      builder.addItem(INJECT_INTO_PRIVATE_CLASS, constructorElement);
    }

    if (typeModifiers.contains(ABSTRACT)) {
      builder.addItem(INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS, constructorElement);
    }

    if (enclosingElement.getNestingKind().isNested()
        && !typeModifiers.contains(STATIC)) {
      builder.addItem(INJECT_CONSTRUCTOR_ON_INNER_CLASS, constructorElement);
    }

    // This is computationally expensive, but probably preferable to a giant index
    FluentIterable<ExecutableElement> injectConstructors = FluentIterable.from(
        ElementFilter.constructorsIn(enclosingElement.getEnclosedElements()))
            .filter(new Predicate<ExecutableElement>() {
              @Override public boolean apply(ExecutableElement input) {
                return isAnnotationPresent(input, Inject.class);
              }
            });

    if (injectConstructors.size() > 1) {
      builder.addItem(MULTIPLE_INJECT_CONSTRUCTORS, constructorElement);
    }

    ImmutableSet<? extends AnnotationMirror> scopes = getScopes(enclosingElement);
    if (scopes.size() > 1) {
      for (AnnotationMirror scope : scopes) {
        builder.addItem(MULTIPLE_SCOPES, enclosingElement, scope);
      }
    }

    return builder.build();
  }
}
