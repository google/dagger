/*
 * Copyright (C) 2021 The Dagger Authors.
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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static dagger.internal.codegen.langmodel.DaggerElements.isAnnotationPresent;
import static javax.lang.model.util.ElementFilter.constructorsIn;

import androidx.room.compiler.processing.XTypeElement;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.DaggerSuperficialValidation.ValidationException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

/** Validates inject types in a round. */
// TODO(bcorso): InjectValidator also handles @AssistedInject constructors, so we should update this
// class to do superficial validation for @AssistedInject constructors too.
@Singleton
public final class SuperficialInjectValidator implements ClearableCache {

  // We keep two separate caches because the same type might need validation as both an inject type
  // and an inject super type, and we validate different things depending on that context.
  private final Map<XTypeElement, ValidationException> validatedInjectTypeElements =
      new HashMap<>();
  private final Map<XTypeElement, ValidationException> validatedInjectSuperTypeElements =
      new HashMap<>();

  @Inject
  SuperficialInjectValidator() {}

  public void throwIfInjectTypeNotValid(XTypeElement injectTypeElement) {
    ValidationException validationException =
        validatedInjectTypeElements.computeIfAbsent(injectTypeElement, this::validate);
    if (validationException != null) {
      throw validationException;
    }
  }

  public void throwIfInjectSuperTypeNotValid(XTypeElement injectSuperTypeElement) {
    ValidationException validationException =
        validatedInjectSuperTypeElements.computeIfAbsent(
            injectSuperTypeElement, this::validateSuperType);
    if (validationException != null) {
      throw validationException;
    }
  }

  private ValidationException validate(XTypeElement xInjectTypeElement) {
    // The inject validator inspects:
    //   1. the type itself
    //   2. the type's annotations, if an @Inject constructor exists (needed for scoping)
    //   3. the type's superclass (needed for inherited @Inject members)
    //   4. the direct fields, constructors, and methods annotated with @Inject
    // TODO(bcorso): Call the #validate() methods from XProcessing instead once we have validation
    // for types other than elements, e.g. annotations, annotation values, and types.
    TypeElement injectTypeElement = toJavac(xInjectTypeElement);
    try {
      DaggerSuperficialValidation.validateType("class type", injectTypeElement.asType());
      // We only validate annotations if this type has an @Inject constructor. Otherwise, Dagger
      // isn't responsible for creating this type, so no need to care about scope annotations.
      if (constructorsIn(injectTypeElement.getEnclosedElements()).stream()
          .anyMatch(constructor -> isAnnotationPresent(constructor, TypeNames.INJECT))) {
        DaggerSuperficialValidation.validateAnnotations(injectTypeElement.getAnnotationMirrors());
      }
      DaggerSuperficialValidation.validateType(
          "superclass type", injectTypeElement.getSuperclass());
      injectTypeElement.getEnclosedElements().stream()
          .filter(element -> isAnnotationPresent(element, TypeNames.INJECT))
          .forEach(DaggerSuperficialValidation::validateElement);
      return null;
    } catch (ValidationException validationException) {
      return validationException.append("InjectValidator.validate: " + injectTypeElement);
    }
  }

  private ValidationException validateSuperType(XTypeElement xInjectSuperTypeElement) {
    // Note that we skip validating annotations and constructors in supertypes because scopes and
    // @Inject constructors are ignored in super types.
    TypeElement injectSupertypeElement = toJavac(xInjectSuperTypeElement);
    try {
      DaggerSuperficialValidation.validateType("class type", injectSupertypeElement.asType());
      DaggerSuperficialValidation.validateType(
          "super type", injectSupertypeElement.getSuperclass());
      injectSupertypeElement.getEnclosedElements().stream()
          .filter(element -> isAnnotationPresent(element, TypeNames.INJECT))
          .filter(element -> element.getKind() != ElementKind.CONSTRUCTOR)
          .forEach(DaggerSuperficialValidation::validateElement);
      return null;
    } catch (ValidationException validationException) {
      return validationException.append(
          "InjectValidator.validateSuperType: " + injectSupertypeElement);
    }
  }

  @Override
  public void clearCache() {
    validatedInjectTypeElements.clear();
    validatedInjectSuperTypeElements.clear();
  }
}
