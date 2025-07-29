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

package dagger.hilt.processor.internal.disableinstallincheck;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.ClassNames;
import dagger.hilt.processor.internal.ProcessorErrors;
import dagger.internal.codegen.xprocessing.XElements;

/** Processes the annotations annotated with {@link dagger.hilt.migration.DisableInstallInCheck} */
public final class DisableInstallInCheckProcessingStep extends BaseProcessingStep {
  public DisableInstallInCheckProcessingStep(XProcessingEnv env) {
    super(env);
  }

  @Override
  protected ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(ClassNames.DISABLE_INSTALL_IN_CHECK);
  }

  @Override
  public void processEach(ClassName annotation, XElement element) {
    ProcessorErrors.checkState(
        element.hasAnnotation(ClassNames.MODULE),
        element,
        "@DisableInstallInCheck should only be used on modules. However, it was found annotating"
            + " %s",
        XElements.toStableString(element));
  }
}
