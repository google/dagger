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

package dagger.internal.codegen.validation;

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedInjectedConstructors;
import static dagger.internal.codegen.binding.InjectionAnnotations.injectedConstructors;
import static dagger.internal.codegen.binding.SourceFiles.factoryNameForElement;
import static dagger.internal.codegen.binding.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getAnyAnnotation;
import static dagger.internal.codegen.xprocessing.XMethodElements.hasTypeParameters;
import static dagger.internal.codegen.xprocessing.XTypes.isSubtype;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeName;
import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XFieldElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.XVariableElement;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.binding.MethodSignatureFormatter;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.langmodel.Accessibility;
import dagger.internal.codegen.model.Scope;
import dagger.internal.codegen.xprocessing.XAnnotations;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.tools.Diagnostic;

/**
 * A {@linkplain ValidationReport validator} for {@link Inject}-annotated elements and the types
 * that contain them.
 */
@Singleton
public final class InjectValidator implements ClearableCache {

  private final XProcessingEnv processingEnv;
  private final DependencyRequestValidator dependencyRequestValidator;
  private final InjectionAnnotations injectionAnnotations;
  private final DaggerSuperficialValidation superficialValidation;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final InternalValidator validator;
  private final InternalValidator validatorWhenGeneratingCode;

  @Inject
  InjectValidator(
      XProcessingEnv processingEnv,
      DependencyRequestValidator dependencyRequestValidator,
      CompilerOptions compilerOptions,
      InjectionAnnotations injectionAnnotations,
      DaggerSuperficialValidation superficialValidation,
      MethodSignatureFormatter methodSignatureFormatter) {
    this.processingEnv = processingEnv;
    this.dependencyRequestValidator = dependencyRequestValidator;
    this.injectionAnnotations = injectionAnnotations;
    this.superficialValidation = superficialValidation;
    this.methodSignatureFormatter = methodSignatureFormatter;

    // When validating types that require a generated factory class we need to error on private and
    // static inject members even if the compiler options are set to not error.
    this.validatorWhenGeneratingCode =
        new InternalValidator(Diagnostic.Kind.ERROR, Diagnostic.Kind.ERROR);

    // When validating types that might not require a generated factory we can take the user flags
    // for private and static inject members into account, but try to reuse the existing one if the
    // diagnostic kinds are the same.
    this.validator =
        (compilerOptions.privateMemberValidationKind() == Diagnostic.Kind.ERROR
                && compilerOptions.staticMemberValidationKind() == Diagnostic.Kind.ERROR)
            ? validatorWhenGeneratingCode
            : new InternalValidator(
                compilerOptions.privateMemberValidationKind(),
                compilerOptions.staticMemberValidationKind());
  }

  @Override
  public void clearCache() {
    validator.clearCache();
    validatorWhenGeneratingCode.clearCache();
  }

  public ValidationReport validate(XTypeElement typeElement) {
    return validator.validate(typeElement);
  }

  public ValidationReport validateForMembersInjection(XTypeElement typeElement) {
    return validator.validateForMembersInjection(typeElement);
  }

  /**
   * Validates {@code typeElement} that requires a factory to be generated.
   *
   * <p>In this case, the validator will have stricter validation for private and static injection
   * since the generated factory doesn't support those types.
   */
  public ValidationReport validateWhenGeneratingCode(XTypeElement typeElement) {
    if (typeElement.getPackageName().startsWith("org.atinject.tck")) {
      // The Technology Compatibility Kit (TCK) package is a special package for testing the JSR330
      // spec, which includes optional features like supporting static/private inject members. Even
      // though Dagger doesn't support this, we allow it for this one case for the test coverage
      // purposes. Use the normal validator which takes the user's compiler flags into account.
      return validator.validate(typeElement);
    }
    return validatorWhenGeneratingCode.validate(typeElement);
  }

  private final class InternalValidator {
    private final Diagnostic.Kind privateMemberDiagnosticKind;
    private final Diagnostic.Kind staticMemberDiagnosticKind;
    private final Map<XTypeElement, ValidationReport> provisionReports = new HashMap<>();
    private final Map<XTypeElement, ValidationReport> membersInjectionReports = new HashMap<>();

    InternalValidator(
        Diagnostic.Kind privateMemberDiagnosticKind, Diagnostic.Kind staticMemberDiagnosticKind) {
      this.privateMemberDiagnosticKind = privateMemberDiagnosticKind;
      this.staticMemberDiagnosticKind = staticMemberDiagnosticKind;
    }

    void clearCache() {
      provisionReports.clear();
      membersInjectionReports.clear();
    }

    ValidationReport validate(XTypeElement typeElement) {
      return reentrantComputeIfAbsent(provisionReports, typeElement, this::validateUncached);
    }

    private ValidationReport validateUncached(XTypeElement typeElement) {
      ValidationReport.Builder builder = ValidationReport.about(typeElement);
      builder.addSubreport(validateForMembersInjectionInternal(typeElement));

      ImmutableSet<XConstructorElement> injectConstructors =
          ImmutableSet.<XConstructorElement>builder()
              .addAll(injectedConstructors(typeElement))
              .addAll(assistedInjectedConstructors(typeElement))
              .build();

      switch (injectConstructors.size()) {
        case 0:
          break; // Nothing to validate.
        case 1:
          builder.addSubreport(validateConstructor(getOnlyElement(injectConstructors)));
          break;
        default:
          builder.addError(
              String.format(
                  "Type %s may only contain one injected constructor. Found: %s",
                  typeElement.getQualifiedName(),
                  injectConstructors.stream()
                      .map(methodSignatureFormatter::format)
                      .collect(toImmutableList())),
              typeElement);
      }

      return builder.build();
    }

    private ValidationReport validateConstructor(XConstructorElement constructorElement) {
      superficialValidation.validateTypeOf(constructorElement);
      ValidationReport.Builder builder =
          ValidationReport.about(constructorElement.getEnclosingElement());

      if (InjectionAnnotations.hasInjectAnnotation(constructorElement)
          && constructorElement.hasAnnotation(XTypeNames.ASSISTED_INJECT)) {
        builder.addError("Constructors cannot be annotated with both @Inject and @AssistedInject");
      }

      XClassName injectAnnotation =
          getAnyAnnotation(
                  constructorElement,
                  XTypeNames.INJECT,
                  XTypeNames.INJECT_JAVAX,
                  XTypeNames.ASSISTED_INJECT)
              .map(XAnnotations::asClassName)
              .get();

      if (constructorElement.isPrivate()) {
        builder.addError(
            "Dagger does not support injection into private constructors", constructorElement);
      }

      // If this type has already been processed in a previous round or compilation unit then there
      // is no reason to recheck for invalid scope annotations since it's already been checked.
      // This allows us to skip superficial validation of constructor annotations in subsequent
      // compilations where the annotation types may no longer be on the classpath.
      if (!processedInPreviousRoundOrCompilationUnit(constructorElement)) {
        superficialValidation.validateAnnotationsOf(constructorElement);
        for (XAnnotation qualifier : injectionAnnotations.getQualifiers(constructorElement)) {
          builder.addError(
              String.format(
                  "@Qualifier annotations are not allowed on @%s constructors",
                  injectAnnotation.getSimpleName()),
              constructorElement,
              qualifier);
        }

        String scopeErrorMsg =
            String.format(
                "@Scope annotations are not allowed on @%s constructors",
                injectAnnotation.getSimpleName());

        if (injectAnnotation.equals(XTypeNames.INJECT)
            || injectAnnotation.equals(XTypeNames.INJECT_JAVAX)) {
          scopeErrorMsg += "; annotate the class instead";
        }

        for (Scope scope : injectionAnnotations.getScopes(constructorElement)) {
          builder.addError(
              scopeErrorMsg, constructorElement, scope.scopeAnnotation().xprocessing());
        }
      }

      for (XExecutableParameterElement parameter : constructorElement.getParameters()) {
        superficialValidation.validateTypeOf(parameter);
        validateDependencyRequest(builder, parameter);
      }

      if (throwsCheckedExceptions(constructorElement)) {
        builder.addItem(
            String.format(
                "Dagger does not support checked exceptions on @%s constructors",
                injectAnnotation.getSimpleName()),
            privateMemberDiagnosticKind,
            constructorElement);
      }

      checkInjectIntoPrivateClass(constructorElement, builder);

      XTypeElement enclosingElement = constructorElement.getEnclosingElement();
      if (enclosingElement.isAbstract()) {
        builder.addError(
            String.format(
                "@%s is nonsense on the constructor of an abstract class",
                injectAnnotation.getSimpleName()),
            constructorElement);
      }

      if (enclosingElement.isNested() && !enclosingElement.isStatic()) {
        builder.addError(
            String.format(
                "@%s constructors are invalid on inner classes. "
                    + "Did you mean to make the class static?",
                injectAnnotation.getSimpleName()),
            constructorElement);
      }

      // Note: superficial validation of the annotations is done as part of getting the scopes.
      ImmutableSet<Scope> scopes =
          injectionAnnotations.getScopes(constructorElement.getEnclosingElement());
      if (injectAnnotation.equals(XTypeNames.ASSISTED_INJECT)) {
        for (Scope scope : scopes) {
          builder.addError(
              "A type with an @AssistedInject-annotated constructor cannot be scoped",
              enclosingElement,
              scope.scopeAnnotation().xprocessing());
        }
      } else if (scopes.size() > 1) {
        for (Scope scope : scopes) {
          builder.addError(
              "A single binding may not declare more than one @Scope",
              enclosingElement,
              scope.scopeAnnotation().xprocessing());
        }
      }

      return builder.build();
    }

    private ValidationReport validateField(XFieldElement fieldElement) {
      superficialValidation.validateTypeOf(fieldElement);
      ValidationReport.Builder builder = ValidationReport.about(fieldElement);
      if (fieldElement.isFinal()) {
        builder.addError("@Inject fields may not be final", fieldElement);
      }

      if (fieldElement.isPrivate()) {
        builder.addItem(
            "Dagger does not support injection into private fields",
            privateMemberDiagnosticKind,
            fieldElement);
      }

      if (fieldElement.isStatic()) {
        builder.addItem(
            "Dagger does not support injection into static fields",
            staticMemberDiagnosticKind,
            fieldElement);
      }

      if (fieldElement.isProtected()
          && fieldElement.getEnclosingElement().isFromKotlin()
          ) {
        builder.addError(
            "Dagger injector does not have access to kotlin protected fields", fieldElement);
      }

      validateDependencyRequest(builder, fieldElement);

      return builder.build();
    }

    private ValidationReport validateMethod(XMethodElement methodElement) {
      superficialValidation.validateTypeOf(methodElement);
      ValidationReport.Builder builder = ValidationReport.about(methodElement);
      if (methodElement.isAbstract()) {
        builder.addError("Methods with @Inject may not be abstract", methodElement);
      }

      if (methodElement.isPrivate()) {
        builder.addItem(
            "Dagger does not support injection into private methods",
            privateMemberDiagnosticKind,
            methodElement);
      }

      if (methodElement.isStatic()) {
        builder.addItem(
            "Dagger does not support injection into static methods",
            staticMemberDiagnosticKind,
            methodElement);
      }

      // No need to resolve type parameters since we're only checking existence.
      if (hasTypeParameters(methodElement)) {
        builder.addError("Methods with @Inject may not declare type parameters", methodElement);
      }

      // No need to resolve thrown types since we're only checking existence.
      if (!methodElement.getThrownTypes().isEmpty()) {
        builder.addError(
            "Methods with @Inject may not throw checked exceptions. "
                + "Please wrap your exceptions in a RuntimeException instead.",
            methodElement);
      }

      for (XExecutableParameterElement parameter : methodElement.getParameters()) {
        superficialValidation.validateTypeOf(parameter);
        validateDependencyRequest(builder, parameter);
      }

      return builder.build();
    }

    private void validateDependencyRequest(
        ValidationReport.Builder builder, XVariableElement parameter) {
      dependencyRequestValidator.validateDependencyRequest(builder, parameter, parameter.getType());
      dependencyRequestValidator.checkNotProducer(builder, parameter);
    }

    public ValidationReport validateForMembersInjection(XTypeElement typeElement) {
      return !processedInPreviousRoundOrCompilationUnit(typeElement)
          ? validate(typeElement) // validate everything
          : validateForMembersInjectionInternal(typeElement); // validate only inject members
    }

    private ValidationReport validateForMembersInjectionInternal(XTypeElement typeElement) {
      return reentrantComputeIfAbsent(
          membersInjectionReports, typeElement, this::validateForMembersInjectionInternalUncached);
    }

    private ValidationReport validateForMembersInjectionInternalUncached(XTypeElement typeElement) {
      superficialValidation.validateTypeOf(typeElement);
      // TODO(beder): This element might not be currently compiled, so this error message could be
      // left in limbo. Find an appropriate way to display the error message in that case.
      ValidationReport.Builder builder = ValidationReport.about(typeElement);
      boolean hasInjectedMembers = false;
      for (XFieldElement field : typeElement.getDeclaredFields()) {
        if (InjectionAnnotations.hasInjectAnnotation(field)) {
          hasInjectedMembers = true;
          ValidationReport report = validateField(field);
          if (!report.isClean()) {
            builder.addSubreport(report);
          }
        }
      }
      for (XMethodElement method : typeElement.getDeclaredMethods()) {
        if (InjectionAnnotations.hasInjectAnnotation(method)) {
          hasInjectedMembers = true;
          ValidationReport report = validateMethod(method);
          if (!report.isClean()) {
            builder.addSubreport(report);
          }
        }
      }

      if (hasInjectedMembers) {
        checkInjectIntoPrivateClass(typeElement, builder);
        checkInjectIntoKotlinObject(typeElement, builder);
      }

      Optional.ofNullable(typeElement.getSuperType())
          .filter(supertype -> !supertype.asTypeName().equals(XTypeName.ANY_OBJECT))
          .ifPresent(
              supertype -> {
                superficialValidation.validateSuperTypeOf(typeElement);
                ValidationReport report = validateForMembersInjection(supertype.getTypeElement());
                if (!report.isClean()) {
                  builder.addSubreport(report);
                }
              });

      return builder.build();
    }

    /** Returns true if the given method element declares a checked exception. */
    private boolean throwsCheckedExceptions(XConstructorElement constructorElement) {
      XType runtimeException = processingEnv.findType(XTypeNames.RUNTIME_EXCEPTION);
      XType error = processingEnv.findType(XTypeNames.ERROR);
      superficialValidation.validateThrownTypesOf(constructorElement);
      return !constructorElement.getThrownTypes().stream()
          .allMatch(type -> isSubtype(type, runtimeException) || isSubtype(type, error));
    }

    private void checkInjectIntoPrivateClass(XElement element, ValidationReport.Builder builder) {
      if (!Accessibility.isElementAccessibleFromOwnPackage(closestEnclosingTypeElement(element))) {
        builder.addItem(
            "Dagger does not support injection into private classes",
            privateMemberDiagnosticKind,
            element);
      }
    }

    private void checkInjectIntoKotlinObject(
        XTypeElement element, ValidationReport.Builder builder) {
      if (element.isKotlinObject() || element.isCompanionObject()) {
        builder.addError("Dagger does not support injection into Kotlin objects", element);
      }
    }

    private boolean processedInPreviousRoundOrCompilationUnit(
        XConstructorElement injectConstructor) {
      return processingEnv.findTypeElement(factoryNameForElement(injectConstructor)) != null;
    }

    private boolean processedInPreviousRoundOrCompilationUnit(XTypeElement membersInjectedType) {
      return processingEnv.findTypeElement(membersInjectorNameForType(membersInjectedType)) != null;
    }
  }
}
