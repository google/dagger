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

import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.TYPEVAR;

import com.google.auto.common.MoreElements;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

final class BindsInstanceParameterValidator extends BindsInstanceElementValidator<VariableElement> {
  @Inject
  BindsInstanceParameterValidator() {}

  @Override
  protected ElementValidator elementValidator(VariableElement element) {
    return new Validator(element);
  }

  private class Validator extends ElementValidator {
    Validator(VariableElement element) {
      super(element);
    }

    @Override
    protected void checkAdditionalProperties() {
      Element enclosing = element.getEnclosingElement();
      if (!enclosing.getKind().equals(METHOD)) {
        report.addError(
            "@BindsInstance should only be applied to methods or parameters of methods");
        return;
      }

      ExecutableElement method = MoreElements.asExecutable(enclosing);
      if (!method.getModifiers().contains(ABSTRACT)) {
        report.addError("@BindsInstance parameters may only be used in abstract methods");
      }

      TypeKind returnKind = method.getReturnType().getKind();
      if (!(returnKind.equals(DECLARED) || returnKind.equals(TYPEVAR))) {
        report.addError(
            "@BindsInstance parameters may not be used in methods with a void, array or primitive "
                + "return type");
      }
    }

    @Override
    protected Optional<TypeMirror> bindingElementType() {
      return Optional.of(element.asType());
    }
  }
}
