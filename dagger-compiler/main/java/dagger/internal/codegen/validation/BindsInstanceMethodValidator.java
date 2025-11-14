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

import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.base.ComponentAnnotation.anyComponentAnnotation;
import static dagger.internal.codegen.base.ModuleAnnotation.moduleAnnotation;
import static dagger.internal.codegen.xprocessing.XMethodElements.getEnclosingTypeElement;

import androidx.room3.compiler.processing.XMethodElement;
import androidx.room3.compiler.processing.XType;
import androidx.room3.compiler.processing.XTypeElement;
import androidx.room3.compiler.processing.XVariableElement;
import dagger.internal.codegen.base.DaggerSuperficialValidation;
import dagger.internal.codegen.base.ModuleAnnotation;
import dagger.internal.codegen.binding.InjectionAnnotations;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/** Validates {@link BindsInstance} usages on builder methods. */
public final class BindsInstanceMethodValidator
    extends BindsInstanceElementValidator<XMethodElement> {
  private final DaggerSuperficialValidation superficialValidation;

  @Inject
  BindsInstanceMethodValidator(
      InjectionAnnotations injectionAnnotations,
      DaggerSuperficialValidation superficialValidation) {
    super(injectionAnnotations);
    this.superficialValidation = superficialValidation;
  }

  @Override
  protected ElementValidator elementValidator(XMethodElement method) {
    return new Validator(method);
  }

  private class Validator extends ElementValidator {
    private final XMethodElement method;

    Validator(XMethodElement method) {
      super(method);
      this.method = method;
    }

    @Override
    protected void checkAdditionalProperties() {
      if (!method.isAbstract()) {
        report.addError("@BindsInstance methods must be abstract");
      }
      if (method.getParameters().size() != 1) {
        report.addError(
            "@BindsInstance methods should have exactly one parameter for the bound type");
      }
      XTypeElement enclosingTypeElement = getEnclosingTypeElement(method);
      moduleAnnotation(enclosingTypeElement, superficialValidation)
          .ifPresent(moduleAnnotation -> report.addError(didYouMeanBinds(moduleAnnotation)));
      anyComponentAnnotation(enclosingTypeElement, superficialValidation)
          .ifPresent(
              componentAnnotation ->
                  report.addError(
                      String.format(
                          "@BindsInstance methods should not be included in @%1$ss. "
                              + "Did you mean to put it in a @%1$s.Builder?",
                          componentAnnotation.simpleName())));
    }

    @Override
    protected Optional<XType> bindingElementType() {
      List<? extends XVariableElement> parameters = method.getParameters();
      return parameters.size() == 1
          ? Optional.of(getOnlyElement(parameters).getType())
          : Optional.empty();
    }
  }

  private static String didYouMeanBinds(ModuleAnnotation moduleAnnotation) {
    return String.format(
        "@BindsInstance methods should not be included in @%ss. Did you mean @Binds?",
        moduleAnnotation.simpleName());
  }
}
