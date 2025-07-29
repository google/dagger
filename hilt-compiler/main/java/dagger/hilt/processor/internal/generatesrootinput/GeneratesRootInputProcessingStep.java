/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal.generatesrootinput;

import static androidx.room.compiler.processing.XElementKt.isTypeElement;
import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.internal.codegen.xprocessing.XElements;

/**
 * Processes the annotations annotated with {@link dagger.hilt.GeneratesRootInput} which generate
 * input for components and should be processed before component creation.
 */
public final class GeneratesRootInputProcessingStep extends BaseProcessingStep {
  public GeneratesRootInputProcessingStep(XProcessingEnv env) {
    super(env);
  }

  @Override
  protected ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(ClassNames.GENERATES_ROOT_INPUT);
  }

  @Override
  public void processEach(ClassName annotation, XElement element) {
    ProcessorErrors.checkState(
        isTypeElement(element) && asTypeElement(element).isAnnotationClass(),
        element,
        "%s should only annotate other annotations. However, it was found annotating %s",
        annotation.simpleName(),
        XElements.toStableString(element));

    new GeneratesRootInputPropagatedDataGenerator(processingEnv(), asTypeElement(element))
        .generate();
  }
}
