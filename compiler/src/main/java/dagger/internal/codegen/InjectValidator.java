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

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static dagger.internal.codegen.ErrorMessages.ABSTRACT_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.FINAL_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.GENERIC_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_INNER_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_INTO_PRIVATE_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_ON_PRIVATE_CONSTRUCTOR;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_INJECT_CONSTRUCTORS;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_SCOPES;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.provisionMayNotDependOnProducerType;
import static dagger.internal.codegen.ErrorMessages.QUALIFIER_ON_INJECT_CONSTRUCTOR;
import static dagger.internal.codegen.ErrorMessages.SCOPE_ON_INJECT_CONSTRUCTOR;
import static dagger.internal.codegen.ErrorMessages.STATIC_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.STATIC_INJECT_METHOD;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static dagger.internal.codegen.InjectionAnnotations.getScopes;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * A {@linkplain ValidationReport validator} for {@link Inject}-annotated elements and the types
 * that contain them.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class InjectValidator {
  private CompilerOptions compilerOptions;

  InjectValidator(CompilerOptions compilerOptions) {
    this.compilerOptions = compilerOptions;
  }

  ValidationReport<TypeElement> validateConstructor(ExecutableElement constructorElement) {
    ValidationReport.Builder<TypeElement> builder =
        ValidationReport.about(MoreElements.asType(constructorElement.getEnclosingElement()));
    if (constructorElement.getModifiers().contains(PRIVATE)) {
      builder.addError(INJECT_ON_PRIVATE_CONSTRUCTOR, constructorElement);
    }

    for (AnnotationMirror qualifier : getQualifiers(constructorElement)) {
      builder.addError(QUALIFIER_ON_INJECT_CONSTRUCTOR, constructorElement, qualifier);
    }

    for (AnnotationMirror scope : getScopes(constructorElement)) {
      builder.addError(SCOPE_ON_INJECT_CONSTRUCTOR, constructorElement, scope);
    }

    for (VariableElement parameter : constructorElement.getParameters()) {
      ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(parameter);
      if (qualifiers.size() > 1) {
        for (AnnotationMirror qualifier : qualifiers) {
          builder.addError(MULTIPLE_QUALIFIERS, constructorElement, qualifier);
        }
      }
      if (FrameworkTypes.isProducerType(parameter.asType())) {
        builder.addError(provisionMayNotDependOnProducerType(parameter.asType()), parameter);
      }
    }

    TypeElement enclosingElement =
        MoreElements.asType(constructorElement.getEnclosingElement());
    Set<Modifier> typeModifiers = enclosingElement.getModifiers();

    if (typeModifiers.contains(PRIVATE)) {
      builder.addError(INJECT_INTO_PRIVATE_CLASS, constructorElement);
    }

    if (typeModifiers.contains(ABSTRACT)) {
      builder.addError(INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS, constructorElement);
    }

    if (enclosingElement.getNestingKind().isNested()
        && !typeModifiers.contains(STATIC)) {
      builder.addError(INJECT_CONSTRUCTOR_ON_INNER_CLASS, constructorElement);
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
      builder.addError(MULTIPLE_INJECT_CONSTRUCTORS, constructorElement);
    }

    ImmutableSet<? extends AnnotationMirror> scopes = getScopes(enclosingElement);
    if (scopes.size() > 1) {
      for (AnnotationMirror scope : scopes) {
        builder.addError(MULTIPLE_SCOPES, enclosingElement, scope);
      }
    }

    return builder.build();
  }

 ValidationReport<VariableElement> validateField(VariableElement fieldElement) {
    ValidationReport.Builder<VariableElement> builder = ValidationReport.about(fieldElement);
    Set<Modifier> modifiers = fieldElement.getModifiers();
    if (modifiers.contains(FINAL)) {
      builder.addError(FINAL_INJECT_FIELD, fieldElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(
          PRIVATE_INJECT_FIELD, compilerOptions.privateMemberValidationKind(), fieldElement);
    }

    if (modifiers.contains(STATIC)) {
      builder.addItem(
          STATIC_INJECT_FIELD, compilerOptions.staticMemberValidationKind(), fieldElement);
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

  ValidationReport<ExecutableElement> validateMethod(ExecutableElement methodElement) {
    ValidationReport.Builder<ExecutableElement> builder = ValidationReport.about(methodElement);
    Set<Modifier> modifiers = methodElement.getModifiers();
    if (modifiers.contains(ABSTRACT)) {
      builder.addError(ABSTRACT_INJECT_METHOD, methodElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(
          PRIVATE_INJECT_METHOD, compilerOptions.privateMemberValidationKind(), methodElement);
    }

    if (modifiers.contains(STATIC)) {
      builder.addItem(
          STATIC_INJECT_METHOD, compilerOptions.staticMemberValidationKind(), methodElement);
    }

    if (!methodElement.getTypeParameters().isEmpty()) {
      builder.addError(GENERIC_INJECT_METHOD, methodElement);
    }

    for (VariableElement parameter : methodElement.getParameters()) {
      ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(parameter);
      if (qualifiers.size() > 1) {
        for (AnnotationMirror qualifier : qualifiers) {
          builder.addError(MULTIPLE_QUALIFIERS, methodElement, qualifier);
        }
      }
      if (FrameworkTypes.isProducerType(parameter.asType())) {
        builder.addError(provisionMayNotDependOnProducerType(parameter.asType()), parameter);
      }
    }

    return builder.build();
  }

  ValidationReport<TypeElement> validateType(TypeElement typeElement) {
    // TODO(beder): This element might not be currently compiled, so this error message could be
    // left in limbo. Find an appropriate way to display the error message in that case.
    ValidationReport.Builder<TypeElement> builder = ValidationReport.about(typeElement);
    for (VariableElement element : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, Inject.class)) {
        ValidationReport<VariableElement> report = validateField(element);
        if (!report.isClean()) {
          builder.addSubreport(report);
        }
      }
    }
    for (ExecutableElement element : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, Inject.class)) {
        ValidationReport<ExecutableElement> report = validateMethod(element);
        if (!report.isClean()) {
          builder.addSubreport(report);
        }
      }
    }
    TypeMirror superclass = typeElement.getSuperclass();
    if (!superclass.getKind().equals(TypeKind.NONE)) {
      ValidationReport<TypeElement> report = validateType(MoreTypes.asTypeElement(superclass));
      if (!report.isClean()) {
        builder.addSubreport(report);
      }
    }
    return builder.build();
  }
}
