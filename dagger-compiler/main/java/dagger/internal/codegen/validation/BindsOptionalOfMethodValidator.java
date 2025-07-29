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

import static dagger.internal.codegen.base.Keys.isValidImplicitProvisionKey;
import static dagger.internal.codegen.binding.InjectionAnnotations.injectedConstructors;
import static dagger.internal.codegen.validation.BindingElementValidator.AllowsMultibindings.NO_MULTIBINDINGS;
import static dagger.internal.codegen.validation.BindingElementValidator.AllowsScoping.NO_SCOPING;
import static dagger.internal.codegen.validation.BindingMethodValidator.Abstractness.MUST_BE_ABSTRACT;
import static dagger.internal.codegen.validation.BindingMethodValidator.ExceptionSuperclass.NO_EXCEPTIONS;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.xprocessing.XTypeNames;
import javax.inject.Inject;

/** A validator for {@link dagger.BindsOptionalOf} methods. */
final class BindsOptionalOfMethodValidator extends BindingMethodValidator {
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  BindsOptionalOfMethodValidator(
      XProcessingEnv processingEnv,
      DependencyRequestValidator dependencyRequestValidator,
      InjectionAnnotations injectionAnnotations) {
    super(
        XTypeNames.BINDS_OPTIONAL_OF,
        ImmutableSet.of(XTypeNames.MODULE, XTypeNames.PRODUCER_MODULE),
        MUST_BE_ABSTRACT,
        NO_EXCEPTIONS,
        NO_MULTIBINDINGS,
        NO_SCOPING,
        processingEnv,
        dependencyRequestValidator,
        injectionAnnotations);
    this.injectionAnnotations = injectionAnnotations;
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
    protected void checkKeyType(XType keyType) {
      super.checkKeyType(keyType);
      if (isValidImplicitProvisionKey(
              injectionAnnotations.getQualifiers(method).stream().findFirst(), keyType)
          && !injectedConstructors(keyType.getTypeElement()).isEmpty()) {
        report.addError(
            "@BindsOptionalOf methods cannot return unqualified types that have an @Inject-"
                + "annotated constructor because those are always present");
      }
    }

    @Override
    protected void checkParameters() {
      if (!method.getParameters().isEmpty()) {
        report.addError("@BindsOptionalOf methods cannot have parameters");
      }
    }
  }
}
