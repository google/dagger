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

import static dagger.internal.codegen.base.FrameworkTypes.isFrameworkType;
import static dagger.internal.codegen.validation.BindingElementValidator.AllowsMultibindings.NO_MULTIBINDINGS;
import static dagger.internal.codegen.validation.BindingElementValidator.AllowsScoping.NO_SCOPING;
import static dagger.internal.codegen.validation.BindingMethodValidator.Abstractness.MUST_BE_ABSTRACT;
import static dagger.internal.codegen.validation.BindingMethodValidator.ExceptionSuperclass.NO_EXCEPTIONS;
import static dagger.internal.codegen.xprocessing.XTypes.isWildcard;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.base.MapType;
import dagger.internal.codegen.base.SetType;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.javapoet.TypeNames;
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
        TypeNames.MULTIBINDS,
        ImmutableSet.of(TypeNames.MODULE, TypeNames.PRODUCER_MODULE),
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
      if (!isPlainMap(method.getReturnType()) && !isPlainSet(method.getReturnType())) {
        report.addError(bindingMethods("must return Map<K, V> or Set<T>"));
      }
    }

    private boolean isPlainMap(XType returnType) {
      if (!MapType.isMap(returnType)) {
        return false;
      }
      MapType mapType = MapType.from(returnType);
      return !mapType.isRawType()
          && !isWildcard(mapType.valueType())
          && !isFrameworkType(mapType.valueType());
    }

    private boolean isPlainSet(XType returnType) {
      if (!SetType.isSet(returnType)) {
        return false;
      }
      SetType setType = SetType.from(returnType);
      return !setType.isRawType()
          && !isWildcard(setType.elementType())
          && !isFrameworkType(setType.elementType());
    }
  }
}
