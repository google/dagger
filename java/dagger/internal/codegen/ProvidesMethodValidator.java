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

package dagger.internal.codegen;

import static dagger.internal.codegen.BindingElementValidator.AllowsMultibindings.ALLOWS_MULTIBINDINGS;
import static dagger.internal.codegen.BindingElementValidator.AllowsScoping.ALLOWS_SCOPING;
import static dagger.internal.codegen.BindingMethodValidator.Abstractness.MUST_BE_CONCRETE;
import static dagger.internal.codegen.BindingMethodValidator.ExceptionSuperclass.RUNTIME_EXCEPTION;

import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.producers.ProducerModule;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

/** A validator for {@link Provides} methods. */
final class ProvidesMethodValidator extends BindingMethodValidator {

  private final DependencyRequestValidator dependencyRequestValidator;

  @Inject
  ProvidesMethodValidator(
      DaggerElements elements,
      DaggerTypes types,
      DependencyRequestValidator dependencyRequestValidator) {
    super(
        elements,
        types,
        Provides.class,
        ImmutableSet.of(Module.class, ProducerModule.class),
        dependencyRequestValidator,
        MUST_BE_CONCRETE,
        RUNTIME_EXCEPTION,
        ALLOWS_MULTIBINDINGS,
        ALLOWS_SCOPING);
    this.dependencyRequestValidator = dependencyRequestValidator;
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
    protected void checkAdditionalMethodProperties() {
    }

    /** Adds an error if a {@link Provides @Provides} method depends on a producer type. */
    @Override
    protected void checkParameter(VariableElement parameter) {
      super.checkParameter(parameter);
      dependencyRequestValidator.checkNotProducer(report, parameter);
    }
  }
}
