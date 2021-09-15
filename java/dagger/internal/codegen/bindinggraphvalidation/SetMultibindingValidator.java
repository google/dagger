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

package dagger.internal.codegen.bindinggraphvalidation;

import static dagger.spi.model.BindingKind.DELEGATE;
import static dagger.spi.model.BindingKind.MULTIBOUND_SET;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import dagger.spi.model.Binding;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraphPlugin;
import dagger.spi.model.DiagnosticReporter;
import dagger.spi.model.Key;
import javax.inject.Inject;

/** Validates that there are not multiple set binding contributions to the same binding. */
final class SetMultibindingValidator implements BindingGraphPlugin {

  @Inject
  SetMultibindingValidator() {
  }

  @Override
  public String pluginName() {
    return "Dagger/SetMultibinding";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    bindingGraph.bindings().stream()
        .filter(binding -> binding.kind().equals(MULTIBOUND_SET))
        .forEach(
            binding ->
                checkForDuplicateSetContributions(binding, bindingGraph, diagnosticReporter));
  }

  private void checkForDuplicateSetContributions(
      Binding binding, BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    // Map of delegate target key to the original contribution binding
    Multimap<Key, Binding> dereferencedBindsTargets = HashMultimap.create();
    for (Binding dep : bindingGraph.requestedBindings(binding)) {
      if (dep.kind().equals(DELEGATE)) {
        dereferencedBindsTargets.put(dereferenceDelegateBinding(dep, bindingGraph), dep);
      }
    }

    dereferencedBindsTargets
        .asMap()
        .forEach(
            (targetKey, contributions) -> {
              if (contributions.size() > 1) {
                diagnosticReporter.reportComponent(
                    ERROR,
                    bindingGraph.componentNode(binding.componentPath()).get(),
                    "Multiple set contributions into %s for the same contribution key: %s.\n\n"
                        + "    %s\n",
                    binding.key(),
                    targetKey,
                    Joiner.on("\n    ").join(contributions));
              }
            });
  }

  /** Returns the delegate target of a delegate binding (going through other delegates as well). */
  private Key dereferenceDelegateBinding(Binding binding, BindingGraph bindingGraph) {
    ImmutableSet<Binding> delegateSet = bindingGraph.requestedBindings(binding);
    if (delegateSet.isEmpty()) {
      // There may not be a delegate if the delegate is missing. In this case, we just take the
      // requested key and return that.
      return Iterables.getOnlyElement(binding.dependencies()).key();
    }
    // If there is a binding, first we check if that is a delegate binding so we can dereference
    // that binding if needed.
    Binding delegate = Iterables.getOnlyElement(delegateSet);
    if (delegate.kind().equals(DELEGATE)) {
      return dereferenceDelegateBinding(delegate, bindingGraph);
    }
    return delegate.key();
  }
}
