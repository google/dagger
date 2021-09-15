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

import static com.google.common.base.Preconditions.checkArgument;

import androidx.room.compiler.processing.XExecutableElement;
import androidx.room.compiler.processing.XMessager;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import javax.inject.Inject;

/** A step that validates all binding methods that were not validated while processing modules. */
public final class BindingMethodProcessingStep
    extends TypeCheckingProcessingStep<XExecutableElement> {

  private final XMessager messager;
  private final AnyBindingMethodValidator anyBindingMethodValidator;

  @Inject
  BindingMethodProcessingStep(
      XMessager messager, AnyBindingMethodValidator anyBindingMethodValidator) {
    this.messager = messager;
    this.anyBindingMethodValidator = anyBindingMethodValidator;
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return anyBindingMethodValidator.methodAnnotations();
  }

  @Override
  protected void process(XExecutableElement xElement, ImmutableSet<ClassName> annotations) {
    checkArgument(
        anyBindingMethodValidator.isBindingMethod(xElement),
        "%s is not annotated with any of %s",
        xElement,
        annotations());
    if (!anyBindingMethodValidator.wasAlreadyValidated(xElement)) {
      anyBindingMethodValidator.validate(xElement).printMessagesTo(messager);
    }
  }
}
