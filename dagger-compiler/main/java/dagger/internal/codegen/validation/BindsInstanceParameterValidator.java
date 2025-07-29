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

import static dagger.internal.codegen.xprocessing.XTypes.isDeclared;
import static dagger.internal.codegen.xprocessing.XTypes.isTypeVariable;

import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XType;
import dagger.internal.codegen.binding.InjectionAnnotations;
import java.util.Optional;
import javax.inject.Inject;

/** Validates {@link BindsInstance} usages on factory method parameters. */
public final class BindsInstanceParameterValidator
    extends BindsInstanceElementValidator<XExecutableParameterElement> {
  @Inject
  BindsInstanceParameterValidator(InjectionAnnotations injectionAnnotations) {
    super(injectionAnnotations);
  }

  @Override
  protected ElementValidator elementValidator(XExecutableParameterElement parameter) {
    return new Validator(parameter);
  }

  private class Validator extends ElementValidator {
    private final XExecutableParameterElement parameter;

    Validator(XExecutableParameterElement parameter) {
      super(parameter);
      this.parameter = parameter;
    }

    @Override
    protected void checkAdditionalProperties() {
      if (!parameter.getEnclosingElement().isAbstract()) {
        report.addError("@BindsInstance parameters may only be used in abstract methods");
      }

      // The above check should rule out constructors since constructors cannot be abstract, so we
      // know the XExecutableElement enclosing the parameter has to be an XMethodElement.
      XMethodElement method = (XMethodElement) parameter.getEnclosingElement();
      if (!(isDeclared(method.getReturnType()) || isTypeVariable(method.getReturnType()))) {
        report.addError(
            "@BindsInstance parameters may not be used in methods with a void, array or primitive "
                + "return type");
      }
    }

    @Override
    protected Optional<XType> bindingElementType() {
      return Optional.of(parameter.getType());
    }
  }
}
