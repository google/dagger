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

package dagger.hilt.processor.internal.earlyentrypoint;

import static dagger.internal.codegen.xprocessing.XElements.asTypeElement;

import androidx.room.compiler.processing.XElement;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import dagger.hilt.processor.internal.BaseProcessingStep;
import dagger.hilt.processor.internal.ClassNames;

/** Validates {@link dagger.hilt.android.EarlyEntryPoint} usages. */
public final class EarlyEntryPointProcessingStep extends BaseProcessingStep {
  public EarlyEntryPointProcessingStep(XProcessingEnv env) {
    super(env);
  }

  @Override
  public ImmutableSet<ClassName> annotationClassNames() {
    return ImmutableSet.of(ClassNames.EARLY_ENTRY_POINT);
  }

  @Override
  public void processEach(ClassName annotation, XElement element) {
    new AggregatedEarlyEntryPointGenerator(asTypeElement(element)).generate();
  }
}
