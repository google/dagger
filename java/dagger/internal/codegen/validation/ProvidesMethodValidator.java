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

import static dagger.internal.codegen.validation.BindingElementValidator.AllowsMultibindings.ALLOWS_MULTIBINDINGS;
import static dagger.internal.codegen.validation.BindingElementValidator.AllowsScoping.ALLOWS_SCOPING;
import static dagger.internal.codegen.validation.BindingMethodValidator.Abstractness.MUST_BE_CONCRETE;
import static dagger.internal.codegen.validation.BindingMethodValidator.ExceptionSuperclass.RUNTIME_EXCEPTION;

import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XVariableElement;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import javax.inject.Inject;

/** A validator for {@link dagger.Provides} methods. */
final class ProvidesMethodValidator extends BindingMethodValidator {

  private final DependencyRequestValidator dependencyRequestValidator;

  @Inject
  ProvidesMethodValidator(
      DaggerElements elements,
      DaggerTypes types,
      KotlinMetadataUtil kotlinMetadataUtil,
      DependencyRequestValidator dependencyRequestValidator,
      InjectionAnnotations injectionAnnotations) {
    super(
        elements,
        types,
        kotlinMetadataUtil,
        TypeNames.PROVIDES,
        ImmutableSet.of(TypeNames.MODULE, TypeNames.PRODUCER_MODULE),
        dependencyRequestValidator,
        MUST_BE_CONCRETE,
        RUNTIME_EXCEPTION,
        ALLOWS_MULTIBINDINGS,
        ALLOWS_SCOPING,
        injectionAnnotations);
    this.dependencyRequestValidator = dependencyRequestValidator;
  }

  @Override
  protected ElementValidator elementValidator(XExecutableElement xElement) {
    return new Validator(xElement);
  }

  private class Validator extends MethodValidator {
    Validator(XExecutableElement xElement) {
      super(xElement);
    }

    @Override
    protected void checkAdditionalMethodProperties() {
    }

    /** Adds an error if a {@link dagger.Provides @Provides} method depends on a producer type. */
    @Override
    protected void checkParameter(XVariableElement parameter) {
      super.checkParameter(parameter);
      dependencyRequestValidator.checkNotProducer(report, XConverters.toJavac(parameter));
    }
  }
}
