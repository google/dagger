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

package dagger.internal.codegen.processingstep;

import static androidx.room3.compiler.processing.XElementKt.isMethod;
import static androidx.room3.compiler.processing.XElementKt.isMethodParameter;

import androidx.room3.compiler.codegen.XClassName;
import androidx.room3.compiler.processing.XElement;
import androidx.room3.compiler.processing.XExecutableParameterElement;
import androidx.room3.compiler.processing.XMethodElement;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.validation.BindsInstanceMethodValidator;
import dagger.internal.codegen.validation.BindsInstanceParameterValidator;
import dagger.internal.codegen.xprocessing.XTypeNames;
import javax.inject.Inject;

/**
 * Processing step that validates that the {@code BindsInstance} annotation is applied to the
 * correct elements.
 */
final class BindsInstanceProcessingStep extends TypeCheckingProcessingStep<XElement> {
  private final BindsInstanceMethodValidator methodValidator;
  private final BindsInstanceParameterValidator parameterValidator;

  @Inject
  BindsInstanceProcessingStep(
      BindsInstanceMethodValidator methodValidator,
      BindsInstanceParameterValidator parameterValidator) {
    this.methodValidator = methodValidator;
    this.parameterValidator = parameterValidator;
  }

  @Override
  public ImmutableSet<XClassName> annotationClassNames() {
    return ImmutableSet.of(XTypeNames.BINDS_INSTANCE);
  }

  @Override
  protected void process(XElement element, ImmutableSet<XClassName> annotations) {
    if (isMethod(element)) {
      methodValidator.validate((XMethodElement) element).printMessagesTo(messager);
    } else if (isMethodParameter(element)) {
      parameterValidator.validate((XExecutableParameterElement) element).printMessagesTo(messager);
    } else {
      throw new AssertionError(element);
    }
  }
}
