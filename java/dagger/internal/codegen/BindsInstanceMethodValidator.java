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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ComponentAnnotation.anyComponentAnnotation;
import static dagger.internal.codegen.ModuleAnnotation.moduleAnnotation;
import static javax.lang.model.element.Modifier.ABSTRACT;

import com.google.auto.common.MoreElements;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

final class BindsInstanceMethodValidator extends BindsInstanceElementValidator<ExecutableElement> {
  @Inject
  BindsInstanceMethodValidator() {}

  @Override
  protected ElementValidator elementValidator(ExecutableElement element) {
    return new Validator(element);
  }

  private class Validator extends ElementValidator {
    Validator(ExecutableElement element) {
      super(element);
    }

    @Override
    protected void checkAdditionalProperties() {
      if (!element.getModifiers().contains(ABSTRACT)) {
        report.addError("@BindsInstance methods must be abstract");
      }
      if (element.getParameters().size() != 1) {
        report.addError(
            "@BindsInstance methods should have exactly one parameter for the bound type");
      }
      TypeElement enclosingType = MoreElements.asType(element.getEnclosingElement());
      moduleAnnotation(enclosingType)
          .ifPresent(moduleAnnotation -> report.addError(didYouMeanBinds(moduleAnnotation)));
      anyComponentAnnotation(enclosingType)
          .ifPresent(
              componentAnnotation ->
                  report.addError(
                      String.format(
                          "@BindsInstance methods should not be included in @%1$ss. "
                              + "Did you mean to put it in a @%1$s.Builder?",
                          componentAnnotation.simpleName())));
    }

    @Override
    protected Optional<TypeMirror> bindingElementType() {
      List<? extends VariableElement> parameters =
          MoreElements.asExecutable(element).getParameters();
      return parameters.size() == 1
          ? Optional.of(getOnlyElement(parameters).asType())
          : Optional.empty();
    }
  }

  private static String didYouMeanBinds(ModuleAnnotation moduleAnnotation) {
    return String.format(
        "@BindsInstance methods should not be included in @%ss. Did you mean @Binds?",
        moduleAnnotation.annotationClass().getSimpleName());
  }
}
