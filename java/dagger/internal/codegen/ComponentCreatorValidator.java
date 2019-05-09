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

package dagger.internal.codegen;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ComponentCreatorAnnotation.getCreatorAnnotations;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ObjectArrays;
import dagger.BindsInstance;
import dagger.internal.codegen.ErrorMessages.ComponentCreatorMessages;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/** Validates types annotated with component creator annotations. */
final class ComponentCreatorValidator {

  private final DaggerElements elements;
  private final DaggerTypes types;

  @Inject
  ComponentCreatorValidator(DaggerElements elements, DaggerTypes types) {
    this.elements = elements;
    this.types = types;
  }

  /** Validates that the given {@code type} is potentially a valid component creator type. */
  public ValidationReport<TypeElement> validate(TypeElement type) {
    ValidationReport.Builder<TypeElement> report = ValidationReport.about(type);

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
      ValidationReport.Builder<?> report) {
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
   * Validator for a single {@link TypeElement} that is annotated with a {@code Builder} or {@code
   * Factory} annotation.
   */
  private final class ElementValidator {
    private final TypeElement type;
    private final Element component;
    private final ValidationReport.Builder<TypeElement> report;
    private final ComponentCreatorAnnotation annotation;
    private final ComponentCreatorMessages messages;

    private ElementValidator(
        TypeElement type,
        ValidationReport.Builder<TypeElement> report,
        ComponentCreatorAnnotation annotation) {
      this.type = type;
      this.component = type.getEnclosingElement();
      this.report = report;
      this.annotation = annotation;
      this.messages = ErrorMessages.creatorMessagesFor(annotation);
    }

    /** Validates the creator type. */
    final ValidationReport<TypeElement> validate() {
      if (!isAnnotationPresent(component, annotation.componentAnnotation())) {
        report.addError(messages.mustBeInComponent());
      }

      // If the type isn't a class or interface, don't validate anything else since the rest of the
      // messages will be bogus.
      if (!validateIsClassOrInterface()) {
        return report.build();
      }

      validateTypeRequirements();
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
      switch (type.getKind()) {
        case CLASS:
          validateConstructor();
          return true;
        case INTERFACE:
          return true;
        default:
          report.addError(messages.mustBeClassOrInterface());
      }
      return false;
    }

    private void validateConstructor() {
      List<? extends Element> allElements = type.getEnclosedElements();
      List<ExecutableElement> constructors = ElementFilter.constructorsIn(allElements);

      boolean valid = true;
      if (constructors.size() != 1) {
        valid = false;
      } else {
        ExecutableElement constructor = getOnlyElement(constructors);
        valid =
            constructor.getParameters().isEmpty() && !constructor.getModifiers().contains(PRIVATE);
      }

      if (!valid) {
        report.addError(messages.invalidConstructor());
      }
    }

    /** Validates basic requirements about the type that are common to both creator kinds. */
    private void validateTypeRequirements() {
      if (!type.getTypeParameters().isEmpty()) {
        report.addError(messages.generics());
      }

      Set<Modifier> modifiers = type.getModifiers();
      if (modifiers.contains(PRIVATE)) {
        report.addError(messages.isPrivate());
      }
      if (!modifiers.contains(STATIC)) {
        report.addError(messages.mustBeStatic());
      }
      // Note: Must be abstract, so no need to check for final.
      if (!modifiers.contains(ABSTRACT)) {
        report.addError(messages.mustBeAbstract());
      }
    }

    private void validateBuilder() {
      ExecutableElement buildMethod = null;
      for (ExecutableElement method : elements.getUnimplementedMethods(type)) {
        switch (method.getParameters().size()) {
          case 0: // If this is potentially a build() method, validate it returns the correct type.
            if (validateFactoryMethodReturnType(method)) {
              if (buildMethod != null) {
                // If we found more than one build-like method, fail.
                error(
                    method,
                    messages.twoFactoryMethods(),
                    messages.inheritedTwoFactoryMethods(),
                    buildMethod);
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

    private void validateSetterMethod(ExecutableElement method) {
      TypeMirror returnType = types.resolveExecutableType(method, type.asType()).getReturnType();
      if (returnType.getKind() != TypeKind.VOID && !types.isSubtype(type.asType(), returnType)) {
        error(
            method,
            messages.setterMethodsMustReturnVoidOrBuilder(),
            messages.inheritedSetterMethodsMustReturnVoidOrBuilder());
      }

      validateNotGeneric(method);

      VariableElement parameter = method.getParameters().get(0);

      boolean methodIsBindsInstance = isAnnotationPresent(method, BindsInstance.class);
      boolean parameterIsBindsInstance = isAnnotationPresent(parameter, BindsInstance.class);
      boolean bindsInstance = methodIsBindsInstance || parameterIsBindsInstance;

      if (methodIsBindsInstance && parameterIsBindsInstance) {
        error(
            method,
            messages.bindsInstanceNotAllowedOnBothSetterMethodAndParameter(),
            messages.inheritedBindsInstanceNotAllowedOnBothSetterMethodAndParameter());
      }

      if (!bindsInstance && parameter.asType().getKind().isPrimitive()) {
        error(
            method,
            messages.nonBindsInstanceParametersMayNotBePrimitives(),
            messages.inheritedNonBindsInstanceParametersMayNotBePrimitives());
      }
    }

    private void validateFactory() {
      ImmutableList<ExecutableElement> abstractMethods =
          elements.getUnimplementedMethods(type).asList();
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
              abstractMethods.get(0));
          return;
      }

      validateFactoryMethod(getOnlyElement(abstractMethods));
    }

    /** Validates that the given {@code method} is a valid component factory method. */
    private void validateFactoryMethod(ExecutableElement method) {
      validateNotGeneric(method);

      if (!validateFactoryMethodReturnType(method)) {
        // If we can't determine that the single method is a valid factory method, don't bother
        // validating its parameters.
        return;
      }

      for (VariableElement parameter : method.getParameters()) {
        if (!isAnnotationPresent(parameter, BindsInstance.class)
            && parameter.asType().getKind().isPrimitive()) {
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
    private boolean validateFactoryMethodReturnType(ExecutableElement method) {
      TypeMirror returnType = types.resolveExecutableType(method, type.asType()).getReturnType();

      if (!types.isSubtype(component.asType(), returnType)) {
        error(
            method,
            messages.factoryMethodMustReturnComponentType(),
            messages.inheritedFactoryMethodMustReturnComponentType());
        return false;
      }

      if (isAnnotationPresent(method, BindsInstance.class)) {
        error(
            method,
            messages.factoryMethodMayNotBeAnnotatedWithBindsInstance(),
            messages.inheritedFactoryMethodMayNotBeAnnotatedWithBindsInstance());
        return false;
      }

      TypeElement componentType = MoreElements.asType(component);
      if (!types.isSameType(componentType.asType(), returnType)) {
        ImmutableSet<ExecutableElement> methodsOnlyInComponent =
            methodsOnlyInComponent(componentType);
        if (!methodsOnlyInComponent.isEmpty()) {
          report.addWarning(
              messages.factoryMethodReturnsSupertypeWithMissingMethods(
                  componentType, type, returnType, method, methodsOnlyInComponent),
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
        ExecutableElement method,
        String enclosedError,
        String inheritedError,
        Object... extraArgs) {
      if (method.getEnclosingElement().equals(type)) {
        report.addError(String.format(enclosedError, extraArgs), method);
      } else {
        report.addError(String.format(inheritedError, ObjectArrays.concat(extraArgs, method)));
      }
    }

    /** Validates that the given {@code method} is not generic. * */
    private void validateNotGeneric(ExecutableElement method) {
      if (!method.getTypeParameters().isEmpty()) {
        error(
            method,
            messages.methodsMayNotHaveTypeParameters(),
            messages.inheritedMethodsMayNotHaveTypeParameters());
      }
    }

    /**
     * Returns all methods defind in {@code componentType} which are not inherited from a supertype.
     */
    private ImmutableSet<ExecutableElement> methodsOnlyInComponent(TypeElement componentType) {
      // TODO(ronshapiro): Ideally this shouldn't return methods which are redeclared from a
      // supertype, but do not change the return type. We don't have a good/simple way of checking
      // that, and it doesn't seem likely, so the warning won't be too bad.
      return ImmutableSet.copyOf(methodsIn(componentType.getEnclosedElements()));
    }
  }
}
