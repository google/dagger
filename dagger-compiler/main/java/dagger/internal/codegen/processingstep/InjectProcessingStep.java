/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static androidx.room.compiler.processing.XElementKt.isConstructor;
import static androidx.room.compiler.processing.XElementKt.isField;
import static androidx.room.compiler.processing.XElementKt.isMethod;
import static dagger.internal.codegen.xprocessing.XElements.asConstructor;
import static dagger.internal.codegen.xprocessing.XElements.asField;
import static dagger.internal.codegen.xprocessing.XElements.asMethod;
import static dagger.internal.codegen.xprocessing.XTypeNames.injectTypeNames;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.processing.XElement;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.internal.codegen.binding.InjectBindingRegistry;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.Set;
import javax.inject.Inject;

/**
 * An annotation processor for generating Dagger implementation code based on the {@link Inject}
 * annotation.
 */
// TODO(gak): add some error handling for bad source files
// TODO(bcorso): Add support in TypeCheckingProcessingStep to perform custom validation and use
// SuperficialInjectValidator rather than SuperficialValidator.
final class InjectProcessingStep extends TypeCheckingProcessingStep<XElement> {
  private final InjectBindingRegistry injectBindingRegistry;
  private final Set<XElement> processedElements = Sets.newHashSet();

  @Inject
  InjectProcessingStep(InjectBindingRegistry injectBindingRegistry) {
    this.injectBindingRegistry = injectBindingRegistry;
  }

  @Override
  public ImmutableSet<XClassName> annotationClassNames() {
    return ImmutableSet.<XClassName>builder()
        .addAll(injectTypeNames())
        .add(XTypeNames.ASSISTED_INJECT)
        .build();
  }

  // Override to avoid prevalidation. The InjectProcessingStep does all of the required validation
  // within InjectValidator so there's no need to prevalidate the nearest enclosing type element.
  // TODO(bcorso): Once all processing steps handle their own validation we can remove this.
  @Override
  protected boolean requiresPreValidation() {
    return false;
  }

  @Override
  protected void process(XElement injectElement, ImmutableSet<XClassName> annotations) {
    // Only process an element once to avoid getting duplicate errors when an element is annotated
    // with multiple inject annotations.
    if (processedElements.contains(injectElement)) {
      return;
    }

    if (isConstructor(injectElement)) {
      injectBindingRegistry.tryRegisterInjectConstructor(asConstructor(injectElement));
    } else if (isField(injectElement)) {
      injectBindingRegistry.tryRegisterInjectField(asField(injectElement));
    } else if (isMethod(injectElement)) {
      injectBindingRegistry.tryRegisterInjectMethod(asMethod(injectElement));
    }

    processedElements.add(injectElement);
  }
}
