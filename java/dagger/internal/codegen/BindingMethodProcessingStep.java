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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;

/** A step that validates all binding methods that were not validated while processing modules. */
final class BindingMethodProcessingStep extends TypeCheckingProcessingStep<ExecutableElement> {

  private final Messager messager;
  private final AnyBindingMethodValidator anyBindingMethodValidator;

  @Inject
  BindingMethodProcessingStep(
      Messager messager, AnyBindingMethodValidator anyBindingMethodValidator) {
    super(MoreElements::asExecutable);
    this.messager = messager;
    this.anyBindingMethodValidator = anyBindingMethodValidator;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return anyBindingMethodValidator.methodAnnotations();
  }

  @Override
  protected void process(
      ExecutableElement method, ImmutableSet<Class<? extends Annotation>> annotations) {
    checkArgument(
        anyBindingMethodValidator.isBindingMethod(method),
        "%s is not annotated with any of %s",
        method,
        annotations());
    if (!anyBindingMethodValidator.wasAlreadyValidated(method)) {
      anyBindingMethodValidator.validate(method).printMessagesTo(messager);
    }
  }
}
