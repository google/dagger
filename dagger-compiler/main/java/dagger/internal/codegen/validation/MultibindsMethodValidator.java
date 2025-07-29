/*
 * Copyright (C) 2016 The Dagger Authors.
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

import static dagger.internal.codegen.base.FrameworkTypes.isMapValueFrameworkType;
import static dagger.internal.codegen.base.FrameworkTypes.isSetValueFrameworkType;
import static dagger.internal.codegen.validation.BindingElementValidator.AllowsMultibindings.NO_MULTIBINDINGS;
import static dagger.internal.codegen.validation.BindingElementValidator.AllowsScoping.NO_SCOPING;
import static dagger.internal.codegen.validation.BindingMethodValidator.Abstractness.MUST_BE_ABSTRACT;
import static dagger.internal.codegen.validation.BindingMethodValidator.ExceptionSuperclass.NO_EXCEPTIONS;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;
import static dagger.internal.codegen.xprocessing.XTypes.isWildcard;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.xprocessing.XTypeNames;
import javax.inject.Inject;

/** A validator for {@link dagger.multibindings.Multibinds} methods. */
class MultibindsMethodValidator extends BindingMethodValidator {

  /** Creates a validator for {@link dagger.multibindings.Multibinds @Multibinds} methods. */
  @Inject
  MultibindsMethodValidator(
      XProcessingEnv processingEnv,
      DependencyRequestValidator dependencyRequestValidator,
      InjectionAnnotations injectionAnnotations) {
    super(
        XTypeNames.MULTIBINDS,
        ImmutableSet.of(XTypeNames.MODULE, XTypeNames.PRODUCER_MODULE),
        MUST_BE_ABSTRACT,
        NO_EXCEPTIONS,
        NO_MULTIBINDINGS,
        NO_SCOPING,
        processingEnv,
        dependencyRequestValidator,
        injectionAnnotations);
  }

  @Override
  protected ElementValidator elementValidator(XMethodElement method) {
    return new Validator(method);
  }

  private class Validator extends MethodValidator {
    private final XMethodElement method;

    Validator(XMethodElement method) {
      super(method);
      this.method = method;
    }

    @Override
    protected void checkParameters() {
      if (!method.getParameters().isEmpty()) {
        report.addError(bindingMethods("cannot have parameters"));
      }
    }

    /** Adds an error unless the method returns a {@code Map<K, V>} or {@code Set<T>}. */
    @Override
    protected void checkType() {
      if (MapType.isMap(method.getReturnType())) {
        checkMapType(MapType.from(method.getReturnType()));
      } else if (SetType.isSet(method.getReturnType())) {
        checkSetType(SetType.from(method.getReturnType()));
      } else {
        report.addError(bindingMethods("return type must be either a Set or Map type."));
      }
    }

    private void checkMapType(MapType mapType) {
      if (mapType.isRawType()) {
        report.addError(bindingMethods("return type cannot be a raw Map type"));
      } else if (isWildcard(mapType.keyType())) {
        report.addError(
            bindingMethods("return type cannot use a wildcard as the Map key type."));
      } else if (isWildcard(mapType.valueType())) {
        report.addError(
            bindingMethods("return type cannot use a wildcard as the Map value type."));
      } else if (isMapValueFrameworkType(mapType.valueType())) {
        String frameworkTypeName = getSimpleName(mapType.valueType().getTypeElement());
        report.addError(
            bindingMethods(
                "return type cannot use '%s' in the Map value type.", frameworkTypeName));
      }
    }

    private void checkSetType(SetType setType) {
      if (setType.isRawType()) {
        report.addError(bindingMethods("return type cannot be a raw Set type"));
      } else if (isWildcard(setType.elementType())) {
        report.addError(bindingMethods("return type cannot use a wildcard as the Set value type."));
      } else if (isSetValueFrameworkType(setType.elementType())) {
        String frameworkTypeName = getSimpleName(setType.elementType().getTypeElement());
        report.addError(
            bindingMethods(
                "return type cannot use '%s' in the Set value type.", frameworkTypeName));
      }
    }
  }
}
