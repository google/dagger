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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.DaggerStreams.instancesOf;
import static dagger.internal.codegen.RequestKinds.canBeSatisfiedByProductionBinding;
import static javax.tools.Diagnostic.Kind.ERROR;

import dagger.model.BindingGraph;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Node;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.stream.Stream;
import javax.inject.Inject;

/**
 * Reports an error for each provision-only dependency request that is satisfied by a production
 * binding.
 */
// TODO(b/29509141): Clarify the error.
final class ProvisionDependencyOnProducerBindingValidator implements BindingGraphPlugin {

  @Inject
  ProvisionDependencyOnProducerBindingValidator() {}

  @Override
  public String pluginName() {
    return "Dagger/ProviderDependsOnProducer";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    provisionDependenciesOnProductionBindings(bindingGraph)
        .forEach(
            provisionDependent ->
                diagnosticReporter.reportDependency(
                    ERROR,
                    provisionDependent,
                    provisionDependent.isEntryPoint()
                        ? entryPointErrorMessage(provisionDependent)
                        : dependencyErrorMessage(provisionDependent, bindingGraph)));
  }

  private Stream<DependencyEdge> provisionDependenciesOnProductionBindings(
      BindingGraph bindingGraph) {
    return bindingGraph.bindings().stream()
        .filter(binding -> binding.isProduction())
        .flatMap(binding -> incomingDependencies(binding, bindingGraph))
        .filter(edge -> !dependencyCanUseProduction(edge, bindingGraph));
  }

  /** Returns the dependencies on {@code binding}. */
  // TODO(dpb): Move to BindingGraph.
  private Stream<DependencyEdge> incomingDependencies(
      dagger.model.Binding binding, BindingGraph bindingGraph) {
    return bindingGraph.network().inEdges(binding).stream()
        .flatMap(instancesOf(DependencyEdge.class));
  }

  // TODO(ronshapiro): merge with MissingBindingValidator.dependencyCanUseProduction
  private boolean dependencyCanUseProduction(DependencyEdge edge, BindingGraph bindingGraph) {
    return edge.isEntryPoint()
        ? canBeSatisfiedByProductionBinding(edge.dependencyRequest().kind())
        : bindingRequestingDependency(edge, bindingGraph).isProduction();
  }

  /**
   * Returns the binding that requests a dependency.
   *
   * @throws IllegalArgumentException if {@code dependency} is an {@linkplain
   *     DependencyEdge#isEntryPoint() entry point}.
   */
  // TODO(dpb): Move to BindingGraph.
  private dagger.model.Binding bindingRequestingDependency(
      DependencyEdge dependency, BindingGraph bindingGraph) {
    checkArgument(!dependency.isEntryPoint());
    Node source = bindingGraph.network().incidentNodes(dependency).source();
    verify(
        source instanceof dagger.model.Binding,
        "expected source of %s to be a binding, but was: %s",
        dependency,
        source);
    return (dagger.model.Binding) source;
  }

  private String entryPointErrorMessage(DependencyEdge entryPoint) {
    return String.format(
        "%s is a provision entry-point, which cannot depend on a production.",
        entryPoint.dependencyRequest().key());
  }

  private String dependencyErrorMessage(
      DependencyEdge dependencyOnProduction, BindingGraph bindingGraph) {
    return String.format(
        "%s is a provision, which cannot depend on a production.",
        bindingRequestingDependency(dependencyOnProduction, bindingGraph).key());
  }
}
