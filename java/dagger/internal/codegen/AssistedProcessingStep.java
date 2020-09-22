/*
 * Copyright (C) 2020 The Dagger Authors.
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

import static com.google.auto.common.MoreElements.isAnnotationPresent;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.validation.TypeCheckingProcessingStep;
import dagger.internal.codegen.validation.ValidationReport;
import java.lang.annotation.Annotation;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.VariableElement;

/** An annotation processor for {@link dagger.assisted.Assisted}-annotated types. */
final class AssistedProcessingStep extends TypeCheckingProcessingStep<VariableElement> {
  private final InjectionAnnotations injectionAnnotations;
  private final Messager messager;

  @Inject
  AssistedProcessingStep(InjectionAnnotations injectionAnnotations, Messager messager) {
    super(MoreElements::asVariable);
    this.injectionAnnotations = injectionAnnotations;
    this.messager = messager;
  }

  @Override
  public ImmutableSet<Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(Assisted.class);
  }

  @Override
  protected void process(
      VariableElement assisted, ImmutableSet<Class<? extends Annotation>> annotations) {
    new AssistedValidator().validate(assisted).printMessagesTo(messager);
  }

  private final class AssistedValidator {
    ValidationReport<VariableElement> validate(VariableElement assisted) {
      ValidationReport.Builder<VariableElement> report = ValidationReport.about(assisted);

      Element assistedConstructor = assisted.getEnclosingElement();
      if (!isAnnotationPresent(assistedConstructor, AssistedInject.class)
          || assistedConstructor.getKind() != ElementKind.CONSTRUCTOR) {
        report.addError(
            "@Assisted parameters can only be used within an @AssistedInject-annotated "
                + "constructor.",
            assisted);
      }

      injectionAnnotations
          .getQualifiers(assisted)
          .forEach(
              qualifier ->
                  report.addError(
                      "Qualifiers cannot be used with @Assisted parameters.", assisted, qualifier));

      return report.build();
    }
  }
}
