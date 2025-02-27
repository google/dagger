/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static androidx.room.compiler.processing.XTypeKt.isVoid;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.getCreatorAnnotations;
import static dagger.internal.codegen.base.Util.reentrantComputeIfAbsent;
import static dagger.internal.codegen.xprocessing.XMethodElements.hasTypeParameters;
import static dagger.internal.codegen.xprocessing.XTypeElements.getAllUnimplementedMethods;
import static dagger.internal.codegen.xprocessing.XTypeElements.hasTypeParameters;
import static dagger.internal.codegen.xprocessing.XTypes.isPrimitive;
import static dagger.internal.codegen.xprocessing.XTypes.isSubtype;
import static javax.lang.model.SourceVersion.isKeyword;

import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ObjectArrays;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.base.ComponentCreatorAnnotation;
import dagger.internal.codegen.binding.ErrorMessages;
import dagger.internal.codegen.binding.ErrorMessages.ComponentCreatorMessages;
import dagger.internal.codegen.binding.MethodSignatureFormatter;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Validates types annotated with component creator annotations. */
@Singleton
public final class ComponentCreatorValidator implements ClearableCache {

  private final Map<XTypeElement, ValidationReport> reports = new HashMap<>();
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final KotlinMetadataUtil metadataUtil;

  @Inject
  ComponentCreatorValidator(
      MethodSignatureFormatter methodSignatureFormatter, KotlinMetadataUtil metadataUtil) {
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.metadataUtil = metadataUtil;
  }

  @Override
  public void clearCache() {
    reports.clear();
  }

  /** Validates that the given {@code type} is potentially a valid component creator type. */
  public ValidationReport validate(XTypeElement type) {
    return reentrantComputeIfAbsent(reports, type, this::validateUncached);
  }

  private ValidationReport validateUncached(XTypeElement type) {
    ValidationReport.Builder report = ValidationReport.about(type);

    ImmutableSet<ComponentCreatorAnnotation> creatorAnnotations = getCreatorAnnotations(type);
    if (!validateOnlyOneCreatorAnnotation(creatorAnnotations, report)) {
      return report.build();
    }

    // Note: there's more validation in ComponentDescriptorValidator:
    // - to make sure the setter methods/factory parameters mirror the deps
    // - to make sure each type or key is set by only one method or parameter
    ElementValidator validator =
        new ElementValidator(type, report, getOnlyElement(creatorAnnotations));
    return validator.validate();
  }

  private boolean validateOnlyOneCreatorAnnotation(
      ImmutableSet<ComponentCreatorAnnotation> creatorAnnotations,
      ValidationReport.Builder report) {
    // creatorAnnotations should never be empty because this should only ever be called for
    // types that have been found to have some creator annotation
    if (creatorAnnotations.size() > 1) {
      String error =
          "May not have more than one component Factory or Builder annotation on a type"
              + ": found "
              + creatorAnnotations;
      report.addError(error);
      return false;
    }

    return true;
  }

  /**
   * Validator for a single {@link XTypeElement} that is annotated with a {@code Builder} or {@code
   * Factory} annotation.
   */
  private final class ElementValidator {
    private final XTypeElement creator;
    private final ValidationReport.Builder report;
    private final ComponentCreatorAnnotation annotation;
    private final ComponentCreatorMessages messages;

    private ElementValidator(
        XTypeElement creator,
        ValidationReport.Builder report,
        ComponentCreatorAnnotation annotation) {
      this.creator = creator;
      this.report = report;
      this.annotation = annotation;
      this.messages = ErrorMessages.creatorMessagesFor(annotation);
    }

    /** Validates the creator type. */
    final ValidationReport validate() {
      XTypeElement enclosingType = creator.getEnclosingTypeElement();

      // If the type isn't enclosed in a component don't validate anything else since the rest of
      // the messages will be bogus.
      if (enclosingType == null
              || !enclosingType.hasAnnotation(annotation.componentAnnotation())) {
        return report.addError(messages.mustBeInComponent()).build();
      }

      // If the type isn't a class or interface, don't validate anything else since the rest of the
      // messages will be bogus.
      if (!validateIsClassOrInterface()) {
        return report.build();
      }

      // If the type isn't a valid creator type, don't validate anything else since the rest of the
      // messages will be bogus.
      if (!validateTypeRequirements()) {
        return report.build();
      }

      switch (annotation.creatorKind()) {
        case FACTORY:
          validateFactory();
          break;
        case BUILDER:
          validateBuilder();
      }

      return report.build();
    }

    /** Validates that the type is a class or interface type and returns true if it is. */
    private boolean validateIsClassOrInterface() {
      if (creator.isClass()) {
        validateConstructor();
        return true;
      }
      if (creator.isInterface()) {
        return true;
      }
      report.addError(messages.mustBeClassOrInterface());
      return false;
    }

    private void validateConstructor() {
      List<XConstructorElement> constructors = creator.getConstructors();

      boolean valid = true;
      if (constructors.size() != 1) {
        valid = false;
      } else {
        XConstructorElement constructor = getOnlyElement(constructors);
        valid = constructor.getParameters().isEmpty() && !constructor.isPrivate();
      }

      if (!valid) {
        report.addError(messages.invalidConstructor());
      }
    }

    /** Validates basic requirements about the type that are common to both creator kinds. */
    private boolean validateTypeRequirements() {
      boolean isClean = true;
      if (hasTypeParameters(creator)) {
        report.addError(messages.generics());
        isClean = false;
      }
      if (creator.isPrivate()) {
        report.addError(messages.isPrivate());
        isClean = false;
      }
      if (!creator.isStatic()) {
        report.addError(messages.mustBeStatic());
        isClean = false;
      }
      // Note: Must be abstract, so no need to check for final.
      if (!creator.isAbstract()) {
        report.addError(messages.mustBeAbstract());
        isClean = false;
      }
      return isClean;
    }

    private void validateBuilder() {
      validateClassMethodName();
      XMethodElement buildMethod = null;
      for (XMethodElement method : getAllUnimplementedMethods(creator)) {
        switch (method.getParameters().size()) {
          case 0: // If this is potentially a build() method, validate it returns the correct type.
            if (validateFactoryMethodReturnType(method)) {
              if (buildMethod != null) {
                // If we found more than one build-like method, fail.
                error(
                    method,
                    messages.twoFactoryMethods(),
                    messages.inheritedTwoFactoryMethods(),
                    methodSignatureFormatter.format(buildMethod));
              }
            }
            // We set the buildMethod regardless of the return type to reduce error spam.
            buildMethod = method;
            break;

          case 1: // If this correctly had one parameter, make sure the return types are valid.
            validateSetterMethod(method);
            break;

          default: // more than one parameter
            error(
                method,
                messages.setterMethodsMustTakeOneArg(),
                messages.inheritedSetterMethodsMustTakeOneArg());
            break;
        }
      }

      if (buildMethod == null) {
        report.addError(messages.missingFactoryMethod());
      } else {
        validateNotGeneric(buildMethod);
      }
    }

    private void validateClassMethodName() {
      // Only Kotlin class can have method name the same as a Java reserved keyword, so only check
      // the method name if this class is a Kotlin class.
      if (metadataUtil.hasMetadata(creator)) {
        metadataUtil
            .getAllMethodNamesBySignature(creator)
            .forEach(
                (signature, name) -> {
                  if (isKeyword(name)) {
                    report.addError("Can not use a Java keyword as method name: " + signature);
                  }
                });
      }
    }

    private void validateSetterMethod(XMethodElement method) {
      XType returnType = method.asMemberOf(creator.getType()).getReturnType();
      if (!isVoid(returnType) && !isSubtype(creator.getType(), returnType)) {
        error(
            method,
            messages.setterMethodsMustReturnVoidOrBuilder(),
            messages.inheritedSetterMethodsMustReturnVoidOrBuilder());
      }

      validateNotGeneric(method);

      XExecutableParameterElement parameter = method.getParameters().get(0);

      boolean methodIsBindsInstance = method.hasAnnotation(XTypeNames.BINDS_INSTANCE);
      boolean parameterIsBindsInstance = parameter.hasAnnotation(XTypeNames.BINDS_INSTANCE);
      boolean bindsInstance = methodIsBindsInstance || parameterIsBindsInstance;

      if (methodIsBindsInstance && parameterIsBindsInstance) {
        error(
            method,
            messages.bindsInstanceNotAllowedOnBothSetterMethodAndParameter(),
            messages.inheritedBindsInstanceNotAllowedOnBothSetterMethodAndParameter());
      }

      if (!bindsInstance && isPrimitive(parameter.getType())) {
        error(
            method,
            messages.nonBindsInstanceParametersMayNotBePrimitives(),
            messages.inheritedNonBindsInstanceParametersMayNotBePrimitives());
      }
    }

    private void validateFactory() {
      ImmutableList<XMethodElement> abstractMethods = getAllUnimplementedMethods(creator);
      switch (abstractMethods.size()) {
        case 0:
          report.addError(messages.missingFactoryMethod());
          return;
        case 1:
          break; // good
        default:
          error(
              abstractMethods.get(1),
              messages.twoFactoryMethods(),
              messages.inheritedTwoFactoryMethods(),
              methodSignatureFormatter.format(abstractMethods.get(0)));
          return;
      }

      validateFactoryMethod(getOnlyElement(abstractMethods));
    }

    /** Validates that the given {@code method} is a valid component factory method. */
    private void validateFactoryMethod(XMethodElement method) {
      validateNotGeneric(method);

      if (!validateFactoryMethodReturnType(method)) {
        // If we can't determine that the single method is a valid factory method, don't bother
        // validating its parameters.
        return;
      }

      for (XExecutableParameterElement parameter : method.getParameters()) {
        if (!parameter.hasAnnotation(XTypeNames.BINDS_INSTANCE)
            && isPrimitive(parameter.getType())) {
          error(
              method,
              messages.nonBindsInstanceParametersMayNotBePrimitives(),
              messages.inheritedNonBindsInstanceParametersMayNotBePrimitives());
        }
      }
    }

    /**
     * Validates that the factory method that actually returns a new component instance. Returns
     * true if the return type was valid.
     */
    private boolean validateFactoryMethodReturnType(XMethodElement method) {
      XTypeElement component = creator.getEnclosingTypeElement();
      XType returnType = method.asMemberOf(creator.getType()).getReturnType();
      if (!isSubtype(component.getType(), returnType)) {
        error(
            method,
            messages.factoryMethodMustReturnComponentType(),
            messages.inheritedFactoryMethodMustReturnComponentType());
        return false;
      }

      if (method.hasAnnotation(XTypeNames.BINDS_INSTANCE)) {
        error(
            method,
            messages.factoryMethodMayNotBeAnnotatedWithBindsInstance(),
            messages.inheritedFactoryMethodMayNotBeAnnotatedWithBindsInstance());
        return false;
      }

      if (!returnType.isSameType(component.getType())) {
        // TODO(ronshapiro): Ideally this shouldn't return methods which are redeclared from a
        // supertype, but do not change the return type. We don't have a good/simple way of checking
        // that, and it doesn't seem likely, so the warning won't be too bad.
        ImmutableSet<XMethodElement> declaredMethods =
            ImmutableSet.copyOf(component.getDeclaredMethods());
        if (!declaredMethods.isEmpty()) {
          report.addWarning(
              messages.factoryMethodReturnsSupertypeWithMissingMethods(
                  component, creator, returnType, method, declaredMethods),
              method);
        }
      }
      return true;
    }

    /**
     * Generates one of two error messages. If the method is enclosed in the subject, we target the
     * error to the method itself. Otherwise we target the error to the subject and list the method
     * as an argument. (Otherwise we have no way of knowing if the method is being compiled in this
     * pass too, so javac might not be able to pinpoint it's line of code.)
     */
    /*
     * For Component.Builder, the prototypical example would be if someone had:
     *    libfoo: interface SharedBuilder { void badSetter(A a, B b); }
     *    libbar: BarComponent { BarBuilder extends SharedBuilder } }
     * ... the compiler only validates BarBuilder when compiling libbar, but it fails because
     * of libfoo's SharedBuilder (which could have been compiled in a previous pass).
     * So we can't point to SharedBuilder#badSetter as the subject of the BarBuilder validation
     * failure.
     *
     * This check is a little more strict than necessary -- ideally we'd check if method's enclosing
     * class was included in this compile run.  But that's hard, and this is close enough.
     */
    private void error(
        XMethodElement method, String enclosedError, String inheritedError, Object... extraArgs) {
      if (method.getEnclosingElement().equals(creator)) {
        report.addError(String.format(enclosedError, extraArgs), method);
      } else {
        report.addError(
            String.format(
                inheritedError,
                ObjectArrays.concat(extraArgs, methodSignatureFormatter.format(method))));
      }
    }

    /** Validates that the given {@code method} is not generic. * */
    private void validateNotGeneric(XMethodElement method) {
      if (hasTypeParameters(method)) {
        error(
            method,
            messages.methodsMayNotHaveTypeParameters(),
            messages.inheritedMethodsMayNotHaveTypeParameters());
      }
    }
  }
}
