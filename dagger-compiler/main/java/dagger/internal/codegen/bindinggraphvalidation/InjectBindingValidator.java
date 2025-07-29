/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.internal.codegen.bindinggraphvalidation;

import static dagger.internal.codegen.model.BindingKind.INJECTION;

import dagger.internal.codegen.model.Binding;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.DiagnosticReporter;
import dagger.internal.codegen.validation.InjectValidator;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugin;
import dagger.internal.codegen.validation.ValidationReport;
import dagger.internal.codegen.validation.ValidationReport.Item;
import javax.inject.Inject;

/** Validates bindings from {@code @Inject}-annotated constructors. */
final class InjectBindingValidator extends ValidationBindingGraphPlugin {
  private final InjectValidator injectValidator;

  @Inject
  InjectBindingValidator(InjectValidator injectValidator) {
    this.injectValidator = injectValidator;
  }

  @Override
  public String pluginName() {
    return "Dagger/InjectBinding";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    bindingGraph.bindings().stream()
        .filter(binding -> binding.kind().equals(INJECTION)) // TODO(dpb): Move to BindingGraph
        .forEach(binding -> validateInjectionBinding(binding, diagnosticReporter));
  }

  private void validateInjectionBinding(Binding node, DiagnosticReporter diagnosticReporter) {
    ValidationReport typeReport =
        injectValidator.validateWhenGeneratingCode(
            node.key().type().xprocessing().getTypeElement());
    for (Item item : typeReport.allItems()) {
      diagnosticReporter.reportBinding(item.kind(), node, item.message());
    }
  }
}
