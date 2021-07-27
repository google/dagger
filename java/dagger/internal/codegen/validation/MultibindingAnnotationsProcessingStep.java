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

import static dagger.internal.codegen.langmodel.DaggerElements.getAnnotationMirror;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.shaded.compiler.processing.XExecutableElement;
import androidx.room.shaded.compiler.processing.compat.XConverters;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.javapoet.TypeNames;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;

/**
 * Processing step that verifies that {@link dagger.multibindings.IntoSet}, {@link
 * dagger.multibindings.ElementsIntoSet} and {@link dagger.multibindings.IntoMap} are not present on
 * non-binding methods.
 */
public final class MultibindingAnnotationsProcessingStep
    extends XTypeCheckingProcessingStep<XExecutableElement> {
  private final AnyBindingMethodValidator anyBindingMethodValidator;
  private final Messager messager;

  @Inject
  MultibindingAnnotationsProcessingStep(
      AnyBindingMethodValidator anyBindingMethodValidator, Messager messager) {
    this.anyBindingMethodValidator = anyBindingMethodValidator;
    this.messager = messager;
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(TypeNames.INTO_SET, TypeNames.ELEMENTS_INTO_SET, TypeNames.INTO_MAP);
  }

  @Override
  protected void process(XExecutableElement xElement, ImmutableSet<ClassName> annotations) {
    ExecutableElement method = XConverters.toJavac(xElement);
    if (!anyBindingMethodValidator.isBindingMethod(method)) {
      annotations.forEach(
          annotation ->
              messager.printMessage(
                  ERROR,
                  "Multibinding annotations may only be on @Provides, @Produces, or @Binds methods",
                  method,
                  getAnnotationMirror(method, annotation).get()));
    }
  }
}
