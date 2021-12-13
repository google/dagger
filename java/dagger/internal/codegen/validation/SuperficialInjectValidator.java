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

import androidx.room.compiler.processing.XTypeElement;
import dagger.internal.codegen.base.ClearableCache;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.validation.DaggerSuperficialValidation.ValidationException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.element.TypeElement;

/** Validates inject types in a round. */
@Singleton
public final class SuperficialInjectValidator implements ClearableCache {

  private final Map<XTypeElement, ValidationException> validatedTypeElements = new HashMap<>();

  @Inject
  SuperficialInjectValidator() {}

  public void throwIfNotValid(XTypeElement injectTypeElement) {
    ValidationException validationException =
        validatedTypeElements.computeIfAbsent(injectTypeElement, this::validate);
    if (validationException != null) {
      throw validationException;
    }
  }

  private ValidationException validate(XTypeElement xInjectTypeElement) {
    // The inject validator inspects:
    //   1. the type itself
    //   2. the type's annotations (needed for scoping)
    //   3. the type's superclass (needed for inherited @Inject members)
    //   4. the direct fields, constructors, and methods annotated with @Inject
    // TODO(bcorso): Call the #validate() methods from XProcessing instead once we have validation
    // for types other than elements, e.g. annotations, annotation values, and types.
    TypeElement injectTypeElement = toJavac(xInjectTypeElement);
    try {
      DaggerSuperficialValidation.validateType("class type", injectTypeElement.asType());
      DaggerSuperficialValidation.validateAnnotations(injectTypeElement.getAnnotationMirrors());
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

  @Override
  public void clearCache() {
    validatedTypeElements.clear();
  }
}
