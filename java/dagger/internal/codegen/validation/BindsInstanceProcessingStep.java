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

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XMessager;
import androidx.room.compiler.processing.XVariableElement;
import androidx.room.compiler.processing.compat.XConverters;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.javapoet.TypeNames;
import javax.inject.Inject;
import javax.lang.model.element.Element;

/**
 * Processing step that validates that the {@code BindsInstance} annotation is applied to the
 * correct elements.
 */
public final class BindsInstanceProcessingStep extends TypeCheckingProcessingStep<XElement> {
  private final BindsInstanceMethodValidator methodValidator;
  private final BindsInstanceParameterValidator parameterValidator;
  private final XMessager messager;

  @Inject
  BindsInstanceProcessingStep(
      BindsInstanceMethodValidator methodValidator,
      BindsInstanceParameterValidator parameterValidator,
      XMessager messager) {
    this.methodValidator = methodValidator;
    this.parameterValidator = parameterValidator;
    this.messager = messager;
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.BINDS_INSTANCE);
  }

  @Override
  protected void process(XElement xElement, ImmutableSet<ClassName> annotations) {
    Element element = XConverters.toJavac(xElement);
    switch (element.getKind()) {
      case PARAMETER:
        parameterValidator.validate((XVariableElement) xElement).printMessagesTo(messager);
        break;
      case METHOD:
        methodValidator.validate((XExecutableElement) xElement).printMessagesTo(messager);
        break;
      default:
        throw new AssertionError(xElement);
    }
  }
}
