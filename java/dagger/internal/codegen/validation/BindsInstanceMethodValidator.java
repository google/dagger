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

import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XVariableElement;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.auto.common.MoreElements;
import dagger.internal.codegen.base.ModuleAnnotation;
import dagger.internal.codegen.binding.InjectionAnnotations;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

final class BindsInstanceMethodValidator extends BindsInstanceElementValidator<XExecutableElement> {
  @Inject
  BindsInstanceMethodValidator(InjectionAnnotations injectionAnnotations) {
    super(injectionAnnotations);
  }

  @Override
  protected ElementValidator elementValidator(XExecutableElement xElement) {
    return new Validator(xElement);
  }

  private class Validator extends ElementValidator {
    Validator(XExecutableElement xElement) {
      super(xElement);
    }

    @Override
    protected void checkAdditionalProperties() {
      if (!xElement.isAbstract()) {
        report.addError("@BindsInstance methods must be abstract");
      }
      if (xElement.getParameters().size() != 1) {
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
      List<? extends XVariableElement> parameters = xElement.getParameters();
      return parameters.size() == 1
          ? Optional.of(XConverters.toJavac(getOnlyElement(parameters).getType()))
          : Optional.empty();
    }
  }

  private static String didYouMeanBinds(ModuleAnnotation moduleAnnotation) {
    return String.format(
        "@BindsInstance methods should not be included in @%ss. Did you mean @Binds?",
        moduleAnnotation.annotationName());
  }
}
