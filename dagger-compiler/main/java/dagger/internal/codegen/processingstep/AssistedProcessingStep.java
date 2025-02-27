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

package dagger.internal.codegen.processingstep;

import static androidx.room.compiler.processing.XElementKt.isConstructor;
import static androidx.room.compiler.processing.XElementKt.isMethod;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedFactoryMethod;
import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.isAssistedFactoryType;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XElements.closestEnclosingTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XExecutableParameterElement;
import androidx.room.compiler.processing.XTypeElement;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.binding.InjectionAnnotations;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.xprocessing.XTypeNames;
import javax.inject.Inject;

/**
 * An annotation processor for {@link dagger.assisted.Assisted}-annotated types.
 *
 * <p>This processing step should run after {@link AssistedFactoryProcessingStep}.
 */
final class AssistedProcessingStep extends TypeCheckingProcessingStep<XExecutableParameterElement> {
  private final InjectionAnnotations injectionAnnotations;

  @Inject
  AssistedProcessingStep(InjectionAnnotations injectionAnnotations) {
    this.injectionAnnotations = injectionAnnotations;
  }

  @Override
  public ImmutableSet<XClassName> annotationClassNames() {
    return ImmutableSet.of(XTypeNames.ASSISTED);
  }

  @Override
  protected void process(
      XExecutableParameterElement assisted, ImmutableSet<XClassName> annotations) {
    new AssistedValidator().validate(assisted).printMessagesTo(messager);
  }

  private final class AssistedValidator {
    ValidationReport validate(XExecutableParameterElement assisted) {
      ValidationReport.Builder report = ValidationReport.about(assisted);

      XExecutableElement enclosingElement = assisted.getEnclosingElement();
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

  private boolean isAssistedInjectConstructor(XExecutableElement executableElement) {
    return isConstructor(executableElement)
        && executableElement.hasAnnotation(XTypeNames.ASSISTED_INJECT);
  }

  private boolean isAssistedFactoryCreateMethod(XExecutableElement executableElement) {
    if (isMethod(executableElement)) {
      XTypeElement enclosingElement = closestEnclosingTypeElement(executableElement);
      return isAssistedFactoryType(enclosingElement)
          // This assumes we've already validated AssistedFactory and that a valid method exists.
          && assistedFactoryMethod(enclosingElement).equals(executableElement);
    }
    return false;
  }

  private boolean isKotlinDataClassCopyMethod(XExecutableElement executableElement) {
    // Note: This is a best effort. Technically, we could check the return type and parameters of
    // the copy method to verify it's the one associated with the constructor, but I'd rather keep
    // this simple to avoid encoding too many details of kapt's stubs. At worst, we'll be allowing
    // an @Assisted annotation that has no affect, which is already true for many of Dagger's other
    // annotations.
    return isMethod(executableElement)
        && getSimpleName(asMethod(executableElement)).contentEquals("copy")
        && closestEnclosingTypeElement(executableElement.getEnclosingElement()).isDataClass();
  }
}
