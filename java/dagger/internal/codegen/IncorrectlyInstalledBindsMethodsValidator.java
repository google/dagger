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

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.toCollection;
import static javax.tools.Diagnostic.Kind.WARNING;

import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import dagger.Binds;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.Node;
import dagger.model.ComponentPath;
import dagger.multibindings.IntoSet;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Prints warnings to help users debug <a
 * href="https://github.com/google/dagger/wiki/Dagger-2.17-@Binds-bugs">the floating {@code @Binds}
 * bug</a>.
 */
@Singleton
final class IncorrectlyInstalledBindsMethodsValidator implements BindingGraphPlugin {
  private final SetMultimap<ComponentPath, ContributionBinding> incorrectlyInstalledBindingsCache =
      MultimapBuilder.hashKeys().linkedHashSetValues().build();
  private final CompilerOptions compilerOptions;

  @Inject
  IncorrectlyInstalledBindsMethodsValidator(CompilerOptions compilerOptions) {
    this.compilerOptions = compilerOptions;
  }

  @Override
  public void visitGraph(BindingGraph graph, DiagnosticReporter diagnosticReporter) {
    checkState(
        compilerOptions.floatingBindsMethods() || incorrectlyInstalledBindingsCache.isEmpty());
    for (Entry<ComponentPath, ContributionBinding> entry :
        incorrectlyInstalledBindingsCache.entries()) {
      ComponentPath idealComponentPath = entry.getKey();
      ContributionBinding incorrectlyInstalledBinding = entry.getValue();
      graph
          .bindingNodes(incorrectlyInstalledBinding.key())
          .stream()
          .filter(bindingNode -> bindingNode.binding().equals(incorrectlyInstalledBinding))
          .forEach(
              bindingNode -> report(bindingNode, idealComponentPath, graph, diagnosticReporter));
    }
  }

  private void report(
      BindingNode incompatiblyInstalledBinding,
      ComponentPath idealComponentPath,
      BindingGraph graph,
      DiagnosticReporter diagnosticReporter) {
    // TODO(dpb): consider creating this once per visitGraph()
    ImmutableGraph<Node> dependencyGraph = dependencyGraph(graph).asGraph();
    Set<Node> culpableDependencies =
        Graphs.reachableNodes(dependencyGraph, incompatiblyInstalledBinding)
            .stream()
            .filter(node -> isChild(idealComponentPath, node.componentPath()))
            .filter(node -> !node.equals(incompatiblyInstalledBinding))
            .collect(toCollection(LinkedHashSet::new));
    if (culpableDependencies.isEmpty()) {
      return;
    }
    StringBuilder warning =
        new StringBuilder()
            .append("Floating @Binds method detected:\n  ")
            .append(incompatiblyInstalledBinding)
            .append("\n  It is installed in:       ")
            .append(idealComponentPath)
            .append("\n  But is being resolved in: ")
            .append(incompatiblyInstalledBinding.componentPath())
            .append("\n  This is because it depends transitively on:");

    while (!culpableDependencies.isEmpty()) {
      BindingNode culpableDependency = (BindingNode) Iterables.get(culpableDependencies, 0);
      warning
          .append("\n      ")
          .append(culpableDependency)
          .append(", resolved in: ")
          .append(culpableDependency.componentPath());
      culpableDependencies.removeAll(Graphs.reachableNodes(dependencyGraph, culpableDependency));
    }

    diagnosticReporter.reportComponent(WARNING, graph.rootComponentNode(), warning.toString());
  }

  private boolean isChild(ComponentPath possibleParent, ComponentPath possibleChild) {
    return !possibleParent.equals(possibleChild)
        && possibleChild.components().containsAll(possibleParent.components());
  }

  private ImmutableNetwork<Node, Edge> dependencyGraph(BindingGraph graph) {
    MutableNetwork<Node, Edge> dependencyGraph = NetworkBuilder.from(graph).build();
    for (DependencyEdge dependencyEdge : graph.dependencyEdges()) {
      EndpointPair<Node> endpoint = graph.incidentNodes(dependencyEdge);
      dependencyGraph.addEdge(endpoint.source(), endpoint.target(), dependencyEdge);
    }
    return ImmutableNetwork.copyOf(dependencyGraph);
  }

  void recordBinding(ComponentPath componentPath, ContributionBinding binding) {
    incorrectlyInstalledBindingsCache.put(componentPath, binding);
  }

  @dagger.Module
  interface Module {
    @Binds
    @IntoSet
    @Validation
    BindingGraphPlugin validator(IncorrectlyInstalledBindsMethodsValidator validator);
  }
}
