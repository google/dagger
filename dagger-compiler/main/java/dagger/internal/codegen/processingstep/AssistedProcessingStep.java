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

package dagger.internal.codegen.processingstep;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XExecutableParameterElement;
import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.validation.AssistedValidator;
import dagger.internal.codegen.xprocessing.XTypeNames;
import javax.inject.Inject;

/**
 * An annotation processor for {@link dagger.assisted.Assisted}-annotated types.
 *
 * <p>This processing step should run after {@link AssistedFactoryProcessingStep}.
 */
final class AssistedProcessingStep extends TypeCheckingProcessingStep<XExecutableParameterElement> {
  private final AssistedValidator assistedValidator;

  @Inject
  AssistedProcessingStep(AssistedValidator assistedValidator) {
    this.assistedValidator = assistedValidator;
  }

  @Override
  public ImmutableSet<XClassName> annotationClassNames() {
    return ImmutableSet.of(XTypeNames.ASSISTED);
  }

  @Override
  protected void process(
      XExecutableParameterElement assisted, ImmutableSet<XClassName> annotations) {
    // If the AssistedValidator already validated this element as part of InjectValidator, then we
    // don't need to report the errors again.
    if (assistedValidator.containsCache(assisted)) {
      return;
    }
    assistedValidator.validate(assisted).printMessagesTo(messager);
  }
}
