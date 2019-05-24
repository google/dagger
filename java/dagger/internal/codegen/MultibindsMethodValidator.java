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

package dagger.internal.codegen;

import static dagger.internal.codegen.BindingElementValidator.AllowsMultibindings.NO_MULTIBINDINGS;
import static dagger.internal.codegen.BindingElementValidator.AllowsScoping.NO_SCOPING;
import static dagger.internal.codegen.BindingMethodValidator.Abstractness.MUST_BE_ABSTRACT;
import static dagger.internal.codegen.BindingMethodValidator.ExceptionSuperclass.NO_EXCEPTIONS;
import static dagger.internal.codegen.FrameworkTypes.isFrameworkType;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.multibindings.Multibinds;
import dagger.producers.ProducerModule;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

/** A validator for {@link Multibinds} methods. */
class MultibindsMethodValidator extends BindingMethodValidator {

  /** Creates a validator for {@link Multibinds @Multibinds} methods. */
  @Inject
  MultibindsMethodValidator(
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestValidator dependencyRequestValidator) {
    super(
        elements,
        types,
        Multibinds.class,
        ImmutableSet.of(Module.class, ProducerModule.class),
        dependencyRequestValidator,
        MUST_BE_ABSTRACT,
        NO_EXCEPTIONS,
        NO_MULTIBINDINGS,
        NO_SCOPING);
  }

  @Override
  protected ElementValidator elementValidator(ExecutableElement element) {
    return new Validator(element);
  }

  private class Validator extends MethodValidator {
    Validator(ExecutableElement element) {
      super(element);
    }

    @Override
    protected void checkParameters() {
      if (!element.getParameters().isEmpty()) {
        report.addError(bindingMethods("cannot have parameters"));
      }
    }

    /** Adds an error unless the method returns a {@code Map<K, V>} or {@code Set<T>}. */
    @Override
    protected void checkType() {
      if (!isPlainMap(element.getReturnType())
          && !isPlainSet(element.getReturnType())) {
        report.addError(bindingMethods("must return Map<K, V> or Set<T>"));
      }
    }

    private boolean isPlainMap(TypeMirror returnType) {
      if (!MapType.isMap(returnType)) {
        return false;
      }
      MapType mapType = MapType.from(returnType);
      return !mapType.isRawType()
          && MoreTypes.isType(mapType.valueType()) // No wildcards.
          && !isFrameworkType(mapType.valueType());
    }

    private boolean isPlainSet(TypeMirror returnType) {
      if (!SetType.isSet(returnType)) {
        return false;
      }
      SetType setType = SetType.from(returnType);
      return !setType.isRawType()
          && MoreTypes.isType(setType.elementType()) // No wildcards.
          && !isFrameworkType(setType.elementType());
    }
  }
}
