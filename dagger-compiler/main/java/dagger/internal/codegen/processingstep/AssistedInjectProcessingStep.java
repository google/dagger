/*
 * Copyright (C) 2021 The Dagger Authors.
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

import static dagger.internal.codegen.binding.AssistedInjectionAnnotations.assistedInjectAssistedParameters;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XConstructorElement;
import androidx.room.compiler.processing.XType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedParameter;
import dagger.internal.codegen.validation.InjectValidator;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

/** An annotation processor for {@link dagger.assisted.AssistedInject}-annotated elements. */
final class AssistedInjectProcessingStep extends TypeCheckingProcessingStep<XConstructorElement> {
  private final InjectValidator injectValidator;

  @Inject
  AssistedInjectProcessingStep(InjectValidator injectValidator) {
    this.injectValidator = injectValidator;
  }

  @Override
  public ImmutableSet<XClassName> annotationClassNames() {
    return ImmutableSet.of(XTypeNames.ASSISTED_INJECT);
  }

  @Override
  protected void process(
      XConstructorElement assistedInjectElement, ImmutableSet<XClassName> annotations) {
    // The InjectValidator has already run and reported its errors in InjectProcessingStep, so no
    // need to report its errors. However, the AssistedInjectValidator relies on the InjectValidator
    // returning a clean report, so we check that first before running AssistedInjectValidator. This
    // shouldn't be expensive since InjectValidator caches its results after validating.
    if (injectValidator.validate(assistedInjectElement.getEnclosingElement()).isClean()) {
      new AssistedInjectValidator().validate(assistedInjectElement).printMessagesTo(messager);
    }
  }

  private final class AssistedInjectValidator {
    ValidationReport validate(XConstructorElement constructor) {
      ValidationReport.Builder report = ValidationReport.about(constructor);

      XType assistedInjectType = constructor.getEnclosingElement().getType();
      ImmutableList<AssistedParameter> assistedParameters =
          assistedInjectAssistedParameters(assistedInjectType);

      Set<AssistedParameter> uniqueAssistedParameters = new HashSet<>();
      for (AssistedParameter assistedParameter : assistedParameters) {
        if (!uniqueAssistedParameters.add(assistedParameter)) {
          report.addError(
              String.format(
                  "@AssistedInject constructor has duplicate @Assisted type: %s. Consider setting"
                      + " an identifier on the parameter by using @Assisted(\"identifier\") in both"
                      + " the factory and @AssistedInject constructor",
                  assistedParameter),
              assistedParameter.element());
        }
      }

      return report.build();
    }
  }
}
