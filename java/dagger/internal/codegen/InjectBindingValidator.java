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

package dagger.internal.codegen;

import static dagger.model.BindingKind.INJECTION;

import com.google.auto.common.MoreTypes;
import dagger.internal.codegen.ValidationReport.Item;
import dagger.model.BindingGraph;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

/** Validates bindings from {@code @Inject}-annotated constructors. */
final class InjectBindingValidator implements BindingGraphPlugin {

  private final InjectValidator injectValidator;

  @Inject
  InjectBindingValidator(InjectValidator injectValidator) {
    this.injectValidator = injectValidator.whenGeneratingCode();
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

  private void validateInjectionBinding(
      dagger.model.Binding node, DiagnosticReporter diagnosticReporter) {
    ValidationReport<TypeElement> typeReport =
        injectValidator.validateType(MoreTypes.asTypeElement(node.key().type()));
    for (Item item : typeReport.allItems()) {
      diagnosticReporter.reportBinding(item.kind(), node, item.message());
    }
  }
}
