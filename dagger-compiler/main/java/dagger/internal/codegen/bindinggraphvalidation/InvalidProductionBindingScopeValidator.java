/*
 * Copyright (C) 2024 The Dagger Authors.
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

import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.internal.codegen.model.Binding;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.DiagnosticReporter;
import dagger.internal.codegen.validation.ValidationBindingGraphPlugin;
import javax.inject.Inject;

/** Reports an error for each production binding type that is invalidly scoped. */
final class InvalidProductionBindingScopeValidator extends ValidationBindingGraphPlugin {

  @Inject
  InvalidProductionBindingScopeValidator() {}

  @Override
  public String pluginName() {
    return "Dagger/InvalidProductionBindingScope";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter reporter) {
    // Note: ProducesMethodValidator validates that @Produces methods aren't scoped, but here we
    // take that a step further and validate that anything that transitively depends on a @Produces
    // method is also not scoped (i.e. all production binding types).
    bindingGraph.bindings().stream()
        .filter(Binding::isProduction)
        .filter(binding -> binding.scope().isPresent())
        .forEach(binding -> reporter.reportBinding(ERROR, binding, errorMessage(binding)));
  }

  private String errorMessage(Binding binding) {
    return String.format(
        "%s cannot be scoped because it delegates to an @Produces method.",
        binding);
  }
}
