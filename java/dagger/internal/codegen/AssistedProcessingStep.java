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
import static dagger.internal.codegen.langmodel.DaggerElements.closestEnclosingTypeElement;

import androidx.room.shaded.compiler.processing.XVariableElement;
import androidx.room.shaded.compiler.processing.compat.XConverters;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.assisted.AssistedInject;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.kotlin.KotlinMetadataUtil;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.validation.XTypeCheckingProcessingStep;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * An annotation processor for {@link dagger.assisted.Assisted}-annotated types.
 *
 * <p>This processing step should run after {@link AssistedFactoryProcessingStep}.
 */
final class AssistedProcessingStep extends XTypeCheckingProcessingStep<XVariableElement> {
  private final KotlinMetadataUtil kotlinMetadataUtil;
  private final InjectionAnnotations injectionAnnotations;
  private final DaggerElements elements;
  private final Messager messager;

  @Inject
  AssistedProcessingStep(
      KotlinMetadataUtil kotlinMetadataUtil,
      InjectionAnnotations injectionAnnotations,
      DaggerElements elements,
      Messager messager) {
    this.kotlinMetadataUtil = kotlinMetadataUtil;
    this.injectionAnnotations = injectionAnnotations;
    this.elements = elements;
    this.messager = messager;
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.ASSISTED);
  }

  @Override
  protected void process(XVariableElement xElement, ImmutableSet<ClassName> annotations) {
    // TODO(bcorso): Remove conversion to javac type and use XProcessing throughout.
    VariableElement assisted = XConverters.toJavac(xElement);
    new AssistedValidator().validate(assisted).printMessagesTo(messager);
  }

  private final class AssistedValidator {
    ValidationReport<VariableElement> validate(VariableElement assisted) {
      ValidationReport.Builder<VariableElement> report = ValidationReport.about(assisted);

      Element enclosingElement = assisted.getEnclosingElement();
      if (!isAssistedInjectConstructor(enclosingElement)
          && !isAssistedFactoryCreateMethod(enclosingElement)
          // The generated java stubs for kotlin data classes contain a "copy" method that has
          // the same parameters (and annotations) as the constructor, so just ignore it.
          && !isKotlinDataClassCopyMethod(enclosingElement)) {
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

  private boolean isAssistedInjectConstructor(Element element) {
    return element.getKind() == ElementKind.CONSTRUCTOR
        && isAnnotationPresent(element, AssistedInject.class);
  }

  private boolean isAssistedFactoryCreateMethod(Element element) {
    if (element.getKind() == ElementKind.METHOD) {
      TypeElement enclosingElement = closestEnclosingTypeElement(element);
      return AssistedInjectionAnnotations.isAssistedFactoryType(enclosingElement)
          // This assumes we've already validated AssistedFactory and that a valid method exists.
          && AssistedInjectionAnnotations.assistedFactoryMethod(enclosingElement, elements)
              .equals(element);
    }
    return false;
  }

  private boolean isKotlinDataClassCopyMethod(Element element) {
    // Note: This is a best effort. Technically, we could check the return type and parameters of
    // the copy method to verify it's the one associated with the constructor, but I'd rather keep
    // this simple to avoid encoding too many details of kapt's stubs. At worst, we'll be allowing
    // an @Assisted annotation that has no affect, which is already true for many of Dagger's other
    // annotations.
    return element.getKind() == ElementKind.METHOD
        && element.getSimpleName().contentEquals("copy")
        && kotlinMetadataUtil.isDataClass(closestEnclosingTypeElement(element));
  }
}
