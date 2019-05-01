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

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import dagger.BindsInstance;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;

/**
 * Processing step that validates that the {@code BindsInstance} annotation is applied to the
 * correct elements.
 */
final class BindsInstanceProcessingStep extends TypeCheckingProcessingStep<Element> {
  private final BindsInstanceMethodValidator methodValidator;
  private final BindsInstanceParameterValidator parameterValidator;
  private final Messager messager;

  @Inject
  BindsInstanceProcessingStep(
      BindsInstanceMethodValidator methodValidator,
      BindsInstanceParameterValidator parameterValidator,
      Messager messager) {
    super(element -> element);
    this.methodValidator = methodValidator;
    this.parameterValidator = parameterValidator;
    this.messager = messager;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(BindsInstance.class);
  }

  @Override
  protected void process(Element element, ImmutableSet<Class<? extends Annotation>> annotations) {
    switch (element.getKind()) {
      case PARAMETER:
        parameterValidator.validate(MoreElements.asVariable(element)).printMessagesTo(messager);
        break;
      case METHOD:
        methodValidator.validate(MoreElements.asExecutable(element)).printMessagesTo(messager);
        break;
      default:
        throw new AssertionError(element);
    }
  }
}
