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

import static dagger.internal.codegen.BindingMethodValidator.Abstractness.MUST_BE_CONCRETE;
import static dagger.internal.codegen.BindingMethodValidator.AllowsMultibindings.ALLOWS_MULTIBINDINGS;
import static dagger.internal.codegen.BindingMethodValidator.ExceptionSuperclass.RUNTIME_EXCEPTION;
import static dagger.internal.codegen.ErrorMessages.provisionMayNotDependOnProducerType;

import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import dagger.producers.ProducerModule;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A validator for {@link Provides} methods.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ProvidesMethodValidator extends BindingMethodValidator {

  ProvidesMethodValidator(Elements elements, Types types) {
    super(
        elements,
        types,
        Provides.class,
        ImmutableSet.of(Module.class, ProducerModule.class),
        MUST_BE_CONCRETE,
        RUNTIME_EXCEPTION,
        ALLOWS_MULTIBINDINGS);
  }

  @Override
  protected void checkMethod(ValidationReport.Builder<ExecutableElement> builder) {
    super.checkMethod(builder);
    checkDependsOnProducers(builder);
  }

  /** Adds an error if a {@link Provides @Provides} method depends on a producer type. */
  private void checkDependsOnProducers(ValidationReport.Builder<ExecutableElement> builder) {
    for (VariableElement parameter : builder.getSubject().getParameters()) {
      if (FrameworkTypes.isProducerType(parameter.asType())) {
        builder.addError(provisionMayNotDependOnProducerType(parameter.asType()), parameter);
      }
    }
  }
}
