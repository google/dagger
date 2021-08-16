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

package dagger.internal.codegen;

import static com.google.auto.common.MoreTypes.asDeclared;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.langmodel.DaggerElements.closestEnclosingTypeElement;

import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations;
import dagger.internal.codegen.binding.AssistedInjectionAnnotations.AssistedParameter;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.internal.codegen.langmodel.DaggerTypes;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.validation.XTypeCheckingProcessingStep;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;

/** An annotation processor for {@link dagger.assisted.AssistedInject}-annotated elements. */
final class AssistedInjectProcessingStep extends XTypeCheckingProcessingStep<XExecutableElement> {
  private final DaggerTypes types;
  private final XMessager messager;
  private final XProcessingEnv processingEnv;

  @Inject
  AssistedInjectProcessingStep(
      DaggerTypes types, XMessager messager, XProcessingEnv processingEnv) {
    this.types = types;
    this.messager = messager;
    this.processingEnv = processingEnv;
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.ASSISTED_INJECT);
  }

  @Override
  protected void process(
      XExecutableElement assistedInjectElement, ImmutableSet<ClassName> annotations) {
    new AssistedInjectValidator().validate(assistedInjectElement).printMessagesTo(messager);
  }

  private final class AssistedInjectValidator {
    ValidationReport validate(XExecutableElement constructor) {
      ExecutableElement javaConstructor = XConverters.toJavac(constructor);
      checkState(javaConstructor.getKind() == ElementKind.CONSTRUCTOR);
      ValidationReport.Builder report = ValidationReport.about(constructor);

      DeclaredType assistedInjectType =
          asDeclared(closestEnclosingTypeElement(javaConstructor).asType());
      ImmutableList<AssistedParameter> assistedParameters =
          AssistedInjectionAnnotations.assistedInjectAssistedParameters(assistedInjectType, types);

      Set<AssistedParameter> uniqueAssistedParameters = new HashSet<>();
      for (AssistedParameter assistedParameter : assistedParameters) {
        if (!uniqueAssistedParameters.add(assistedParameter)) {
          report.addError(
              String.format("@AssistedInject constructor has duplicate @Assisted type: %s. "
                  + "Consider setting an identifier on the parameter by using "
                  + "@Assisted(\"identifier\") in both the factory and @AssistedInject constructor",
                  assistedParameter),
              XConverters.toXProcessing(assistedParameter.variableElement(), processingEnv));
        }
      }

      return report.build();
    }
  }
}
