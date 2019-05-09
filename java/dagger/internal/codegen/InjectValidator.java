/*
 * Copyright (C) 2014 The Dagger Authors.
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
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static dagger.internal.codegen.InjectionAnnotations.injectedConstructors;
import static dagger.internal.codegen.Scopes.scopesOf;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.DECLARED;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.langmodel.Accessibility;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.model.Scope;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;

/**
 * A {@linkplain ValidationReport validator} for {@link Inject}-annotated elements and the types
 * that contain them.
 */
final class InjectValidator {
  private final DaggerTypes types;
  private final DaggerElements elements;
  private final CompilerOptions compilerOptions;
  private final DependencyRequestValidator dependencyRequestValidator;
  private final Optional<Diagnostic.Kind> privateAndStaticInjectionDiagnosticKind;

  @Inject
  InjectValidator(
      DaggerTypes types,
      DaggerElements elements,
      DependencyRequestValidator dependencyRequestValidator,
      CompilerOptions compilerOptions) {
    this(types, elements, compilerOptions, dependencyRequestValidator, Optional.empty());
  }

  private InjectValidator(
      DaggerTypes types,
      DaggerElements elements,
      CompilerOptions compilerOptions,
      DependencyRequestValidator dependencyRequestValidator,
      Optional<Kind> privateAndStaticInjectionDiagnosticKind) {
    this.types = types;
    this.elements = elements;
    this.compilerOptions = compilerOptions;
    this.dependencyRequestValidator = dependencyRequestValidator;
    this.privateAndStaticInjectionDiagnosticKind = privateAndStaticInjectionDiagnosticKind;
  }

  /**
   * Returns a new validator that performs the same validation as this one, but is strict about
   * rejecting optionally-specified JSR 330 behavior that Dagger doesn't support (unless {@code
   * -Adagger.ignorePrivateAndStaticInjectionForComponent=enabled} was set in the javac options).
   */
  InjectValidator whenGeneratingCode() {
    return compilerOptions.ignorePrivateAndStaticInjectionForComponent()
        ? this
        : new InjectValidator(
            types,
            elements,
            compilerOptions,
            dependencyRequestValidator,
            Optional.of(Diagnostic.Kind.ERROR));
  }

  ValidationReport<TypeElement> validateConstructor(ExecutableElement constructorElement) {
    ValidationReport.Builder<TypeElement> builder =
        ValidationReport.about(MoreElements.asType(constructorElement.getEnclosingElement()));
    if (constructorElement.getModifiers().contains(PRIVATE)) {
      builder.addError(
          "Dagger does not support injection into private constructors", constructorElement);
    }

    for (AnnotationMirror qualifier : getQualifiers(constructorElement)) {
      builder.addError(
          "@Qualifier annotations are not allowed on @Inject constructors",
          constructorElement,
          qualifier);
    }

    for (Scope scope : scopesOf(constructorElement)) {
      builder.addError(
          "@Scope annotations are not allowed on @Inject constructors; annotate the class instead",
          constructorElement,
          scope.scopeAnnotation());
    }

    for (VariableElement parameter : constructorElement.getParameters()) {
      validateDependencyRequest(builder, parameter);
    }

    if (throwsCheckedExceptions(constructorElement)) {
      builder.addItem(
          "Dagger does not support checked exceptions on @Inject constructors",
          privateMemberDiagnosticKind(),
          constructorElement);
    }

    checkInjectIntoPrivateClass(constructorElement, builder);

    TypeElement enclosingElement =
        MoreElements.asType(constructorElement.getEnclosingElement());

    Set<Modifier> typeModifiers = enclosingElement.getModifiers();
    if (typeModifiers.contains(ABSTRACT)) {
      builder.addError(
          "@Inject is nonsense on the constructor of an abstract class", constructorElement);
    }

    if (enclosingElement.getNestingKind().isNested()
        && !typeModifiers.contains(STATIC)) {
      builder.addError(
          "@Inject constructors are invalid on inner classes. "
              + "Did you mean to make the class static?",
          constructorElement);
    }

    // This is computationally expensive, but probably preferable to a giant index
    ImmutableSet<ExecutableElement> injectConstructors = injectedConstructors(enclosingElement);

    if (injectConstructors.size() > 1) {
      builder.addError("Types may only contain one @Inject constructor", constructorElement);
    }

    ImmutableSet<Scope> scopes = scopesOf(enclosingElement);
    if (scopes.size() > 1) {
      for (Scope scope : scopes) {
        builder.addError(
            "A single binding may not declare more than one @Scope",
            enclosingElement,
            scope.scopeAnnotation());
      }
    }

    return builder.build();
  }

  private ValidationReport<VariableElement> validateField(VariableElement fieldElement) {
    ValidationReport.Builder<VariableElement> builder = ValidationReport.about(fieldElement);
    Set<Modifier> modifiers = fieldElement.getModifiers();
    if (modifiers.contains(FINAL)) {
      builder.addError("@Inject fields may not be final", fieldElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(
          "Dagger does not support injection into private fields",
          privateMemberDiagnosticKind(),
          fieldElement);
    }

    if (modifiers.contains(STATIC)) {
      builder.addItem(
          "Dagger does not support injection into static fields",
          staticMemberDiagnosticKind(),
          fieldElement);
    }

    validateDependencyRequest(builder, fieldElement);

    return builder.build();
  }

  private ValidationReport<ExecutableElement> validateMethod(ExecutableElement methodElement) {
    ValidationReport.Builder<ExecutableElement> builder = ValidationReport.about(methodElement);
    Set<Modifier> modifiers = methodElement.getModifiers();
    if (modifiers.contains(ABSTRACT)) {
      builder.addError("Methods with @Inject may not be abstract", methodElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(
          "Dagger does not support injection into private methods",
          privateMemberDiagnosticKind(),
          methodElement);
    }

    if (modifiers.contains(STATIC)) {
      builder.addItem(
          "Dagger does not support injection into static methods",
          staticMemberDiagnosticKind(),
          methodElement);
    }

    if (!methodElement.getTypeParameters().isEmpty()) {
      builder.addError("Methods with @Inject may not declare type parameters", methodElement);
    }

    for (VariableElement parameter : methodElement.getParameters()) {
      validateDependencyRequest(builder, parameter);
    }

    return builder.build();
  }

  private void validateDependencyRequest(
      ValidationReport.Builder<?> builder, VariableElement parameter) {
    dependencyRequestValidator.validateDependencyRequest(builder, parameter, parameter.asType());
    dependencyRequestValidator.checkNotProducer(builder, parameter);
  }

  ValidationReport<TypeElement> validateMembersInjectionType(TypeElement typeElement) {
    // TODO(beder): This element might not be currently compiled, so this error message could be
    // left in limbo. Find an appropriate way to display the error message in that case.
    ValidationReport.Builder<TypeElement> builder = ValidationReport.about(typeElement);
    boolean hasInjectedMembers = false;
    for (VariableElement element : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, Inject.class)) {
        hasInjectedMembers = true;
        ValidationReport<VariableElement> report = validateField(element);
        if (!report.isClean()) {
          builder.addSubreport(report);
        }
      }
    }
    for (ExecutableElement element : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, Inject.class)) {
        hasInjectedMembers = true;
        ValidationReport<ExecutableElement> report = validateMethod(element);
        if (!report.isClean()) {
          builder.addSubreport(report);
        }
      }
    }

    if (hasInjectedMembers) {
      checkInjectIntoPrivateClass(typeElement, builder);
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

  ValidationReport<TypeElement> validateType(TypeElement typeElement) {
    ValidationReport.Builder<TypeElement> builder = ValidationReport.about(typeElement);
    ValidationReport<TypeElement> membersInjectionReport =
        validateMembersInjectionType(typeElement);
    if (!membersInjectionReport.isClean()) {
      builder.addSubreport(membersInjectionReport);
    }
    for (ExecutableElement element :
        ElementFilter.constructorsIn(typeElement.getEnclosedElements())) {
      if (isAnnotationPresent(element, Inject.class)) {
        ValidationReport<TypeElement> report = validateConstructor(element);
        if (!report.isClean()) {
          builder.addSubreport(report);
        }
      }
    }
    return builder.build();
  }

  boolean isValidType(TypeMirror type) {
    if (!type.getKind().equals(DECLARED)) {
      return true;
    }
    return validateType(MoreTypes.asTypeElement(type)).isClean();
  }

  /** Returns true if the given method element declares a checked exception. */
  private boolean throwsCheckedExceptions(ExecutableElement methodElement) {
    TypeMirror runtimeExceptionType = elements.getTypeElement(RuntimeException.class).asType();
    TypeMirror errorType = elements.getTypeElement(Error.class).asType();
    for (TypeMirror thrownType : methodElement.getThrownTypes()) {
      if (!types.isSubtype(thrownType, runtimeExceptionType)
          && !types.isSubtype(thrownType, errorType)) {
        return true;
      }
    }
    return false;
  }

  private void checkInjectIntoPrivateClass(
      Element element, ValidationReport.Builder<TypeElement> builder) {
    if (!Accessibility.isElementAccessibleFromOwnPackage(
        DaggerElements.closestEnclosingTypeElement(element))) {
      builder.addItem(
          "Dagger does not support injection into private classes",
          privateMemberDiagnosticKind(),
          element);
    }
  }

  private Diagnostic.Kind privateMemberDiagnosticKind() {
    return privateAndStaticInjectionDiagnosticKind.orElse(
        compilerOptions.privateMemberValidationKind());
  }

  private Diagnostic.Kind staticMemberDiagnosticKind() {
    return privateAndStaticInjectionDiagnosticKind.orElse(
        compilerOptions.staticMemberValidationKind());
  }
}
