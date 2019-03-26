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
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.TYPEVAR;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import dagger.BindsInstance;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;

/**
 * Processing step that validates that the {@code BindsInstance} annotation is applied to the
 * correct elements.
 */
final class BindsInstanceProcessingStep extends TypeCheckingProcessingStep<Element> {
  private final Messager messager;

  @Inject
  BindsInstanceProcessingStep(Messager messager) {
    super(element -> element);
    this.messager = messager;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(BindsInstance.class);
  }

  @Override
  protected void process(Element element, ImmutableSet<Class<? extends Annotation>> annotations) {
    ValidationReport.Builder<Element> report = ValidationReport.about(element);

    switch (element.getKind()) {
      case METHOD:
        ExecutableElement method = MoreElements.asExecutable(element);
        validateBindsInstanceMethod(method, report);
        break;
      case PARAMETER:
        VariableElement parameter = MoreElements.asVariable(element);
        validateBindsInstanceParameterType(parameter, report);
        validateBindsInstanceParameterEnclosingMethod(parameter, report);
        break;
      default:
        // Shouldn't be possible given the target elements @BindsInstance allows.
        throw new AssertionError();
    }

    report.build().printMessagesTo(messager);
  }

  private void validateBindsInstanceMethod(
      ExecutableElement method, ValidationReport.Builder<Element> report) {
    if (!method.getModifiers().contains(ABSTRACT)) {
      report.addError("@BindsInstance methods must be abstract");
    }
    if (method.getParameters().size() != 1) {
      report.addError(
          "@BindsInstance methods should have exactly one parameter for the bound type");
    } else {
      validateBindsInstanceParameterType(getOnlyElement(method.getParameters()), report);
    }
    TypeElement enclosingType = MoreElements.asType(method.getEnclosingElement());
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

  private static String didYouMeanBinds(ModuleAnnotation moduleAnnotation) {
    return String.format(
        "@BindsInstance methods should not be included in @%ss. Did you mean @Binds?",
        moduleAnnotation.annotationClass().getSimpleName());
  }

  private void validateBindsInstanceParameterType(
      VariableElement parameter, ValidationReport.Builder<Element> report) {
    if (FrameworkTypes.isFrameworkType(parameter.asType())) {
      report.addError("@BindsInstance parameters may not be framework types", parameter);
    }
  }

  private void validateBindsInstanceParameterEnclosingMethod(
      VariableElement parameter, ValidationReport.Builder<Element> report) {
    Element enclosing = parameter.getEnclosingElement();
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
          "@BindsInstance parameters may not be used in methods with a void, array or "
              + "primitive return type");
    }
  }
}
