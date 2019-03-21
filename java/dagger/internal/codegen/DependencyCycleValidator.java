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
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.limit;
import static com.google.common.collect.Iterables.skip;
import static com.google.common.collect.Sets.newHashSetWithExpectedSize;
import static dagger.internal.codegen.DaggerGraphs.shortestPath;
import static dagger.internal.codegen.DaggerStreams.instancesOf;
import static dagger.internal.codegen.DaggerStreams.toImmutableList;
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static dagger.internal.codegen.RequestKinds.extractKeyType;
import static dagger.internal.codegen.RequestKinds.getRequestKind;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.ImmutableNetwork;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Node;
import dagger.model.BindingKind;
import dagger.model.DependencyRequest;
import dagger.model.RequestKind;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.type.TypeMirror;

/** Reports errors for dependency cycles. */
final class DependencyCycleValidator implements BindingGraphPlugin {

  private final DependencyRequestFormatter dependencyRequestFormatter;

  @Inject
  DependencyCycleValidator(DependencyRequestFormatter dependencyRequestFormatter) {
    this.dependencyRequestFormatter = dependencyRequestFormatter;
  }

  @Override
  public String pluginName() {
    return "Dagger/DependencyCycle";
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    ImmutableNetwork<Node, DependencyEdge> dependencyGraph =
        nonCycleBreakingDependencyGraph(bindingGraph);
    // Check each endpoint pair only once, no matter how many parallel edges connect them.
    Set<EndpointPair<Node>> dependencyEndpointPairs = dependencyGraph.asGraph().edges();
    Set<EndpointPair<Node>> visited = newHashSetWithExpectedSize(dependencyEndpointPairs.size());
    for (EndpointPair<Node> endpointPair : dependencyEndpointPairs) {
      cycleContainingEndpointPair(endpointPair, dependencyGraph, visited)
          .ifPresent(cycle -> reportCycle(cycle, bindingGraph, diagnosticReporter));
    }
  }

  private Optional<Cycle<Node>> cycleContainingEndpointPair(
      EndpointPair<Node> endpoints,
      ImmutableNetwork<Node, DependencyEdge> dependencyGraph,
      Set<EndpointPair<Node>> visited) {
    if (!visited.add(endpoints)) {
      // don't recheck endpoints we already know are part of a cycle
      return Optional.empty();
    }

    // If there's a path from the target back to the source, there's a cycle.
    ImmutableList<Node> cycleNodes =
        shortestPath(dependencyGraph, endpoints.target(), endpoints.source());
    if (cycleNodes.isEmpty()) {
      return Optional.empty();
    }

    Cycle<Node> cycle = Cycle.fromPath(cycleNodes);
    visited.addAll(cycle.endpointPairs()); // no need to check any edge in this cycle again
    return Optional.of(cycle);
  }

  /**
   * Reports a dependency cycle at the dependency into the cycle that is closest to an entry point.
   *
   * <p>For cycles found in reachable binding graphs, looks for the shortest path from the component
   * that contains the cycle (all bindings in a cycle must be in the same component; see below) to
   * some binding in the cycle. Then looks for the last dependency in that path that is not in the
   * cycle; that is the dependency that will be reported, so that the dependency trace will end just
   * before the cycle.
   *
   * <p>For cycles found during full binding graph validation, just reports the component that
   * contains the cycle.
   *
   * <p>Proof (by counterexample) that all bindings in a cycle must be in the same component: Assume
   * one binding in the cycle is in a parent component. Bindings cannot depend on bindings in child
   * components, so that binding cannot depend on the next binding in the cycle.
   */
  private void reportCycle(
      Cycle<Node> cycle, BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    if (bindingGraph.isFullBindingGraph()) {
      diagnosticReporter.reportComponent(
          ERROR,
          bindingGraph.componentNode(cycle.nodes().asList().get(0).componentPath()).get(),
          errorMessage(cycle, bindingGraph));
      return;
    }

    ImmutableList<Node> path = shortestPathToCycleFromAnEntryPoint(cycle, bindingGraph);
    Node cycleStartNode = path.get(path.size() - 1);
    Node previousNode = path.get(path.size() - 2);
    DependencyEdge dependencyToReport =
        chooseDependencyEdgeConnecting(previousNode, cycleStartNode, bindingGraph);
    diagnosticReporter.reportDependency(
        ERROR, dependencyToReport, errorMessage(cycle.shift(cycleStartNode), bindingGraph));
  }

  private ImmutableList<Node> shortestPathToCycleFromAnEntryPoint(
      Cycle<Node> cycle, BindingGraph bindingGraph) {
    Node someCycleNode = cycle.nodes().asList().get(0);
    ComponentNode componentContainingCycle =
        bindingGraph.componentNode(someCycleNode.componentPath()).get();
    ImmutableList<Node> pathToCycle =
        shortestPath(bindingGraph.network(), componentContainingCycle, someCycleNode);
    return subpathToCycle(pathToCycle, cycle);
  }

  /**
   * Returns the subpath from the head of {@code path} to the first node in {@code path} that's in
   * the cycle.
   */
  private ImmutableList<Node> subpathToCycle(ImmutableList<Node> path, Cycle<Node> cycle) {
    ImmutableList.Builder<Node> subpath = ImmutableList.builder();
    for (Node node : path) {
      subpath.add(node);
      if (cycle.nodes().contains(node)) {
        return subpath.build();
      }
    }
    throw new IllegalArgumentException(
        "path " + path + " doesn't contain any nodes in cycle " + cycle);
  }

  private String errorMessage(Cycle<Node> cycle, BindingGraph graph) {
    StringBuilder message = new StringBuilder("Found a dependency cycle:");
    ImmutableList<DependencyRequest> cycleRequests =
        cycle.endpointPairs().stream()
            // TODO(dpb): Would be nice to take the dependency graph here.
            .map(endpointPair -> nonCycleBreakingEdge(endpointPair, graph))
            .map(DependencyEdge::dependencyRequest)
            .collect(toImmutableList())
            .reverse();
    dependencyRequestFormatter.formatIndentedList(message, cycleRequests, 0);
    return message.toString();
  }

  /**
   * Returns one of the edges between two nodes that doesn't {@linkplain
   * #breaksCycle(DependencyEdge, BindingGraph) break} a cycle.
   */
  private DependencyEdge nonCycleBreakingEdge(EndpointPair<Node> endpointPair, BindingGraph graph) {
    return graph.network().edgesConnecting(endpointPair.source(), endpointPair.target()).stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .filter(edge -> !breaksCycle(edge, graph))
        .findFirst()
        .get();
  }

  private boolean breaksCycle(DependencyEdge edge, BindingGraph graph) {
    if (edge.dependencyRequest().key().multibindingContributionIdentifier().isPresent()) {
      return false;
    }
    if (breaksCycle(edge.dependencyRequest().key().type(), edge.dependencyRequest().kind())) {
      return true;
    }
    Node target = graph.network().incidentNodes(edge).target();
    if (target instanceof dagger.model.Binding
        && ((dagger.model.Binding) target).kind().equals(BindingKind.OPTIONAL)) {
      /* For @BindsOptionalOf bindings, unwrap the type inside the Optional. If the unwrapped type
       * breaks the cycle, so does the optional binding. */
      TypeMirror optionalValueType = OptionalType.from(edge.dependencyRequest().key()).valueType();
      RequestKind requestKind = getRequestKind(optionalValueType);
      return breaksCycle(extractKeyType(requestKind, optionalValueType), requestKind);
    }
    return false;
  }

  private boolean breaksCycle(TypeMirror requestedType, RequestKind requestKind) {
    switch (requestKind) {
      case PROVIDER:
      case LAZY:
      case PROVIDER_OF_LAZY:
        return true;

      case INSTANCE:
        if (MapType.isMap(requestedType)) {
          MapType mapType = MapType.from(requestedType);
          return !mapType.isRawType() && mapType.valuesAreTypeOf(Provider.class);
        }
        // fall through

      default:
        return false;
    }
  }

  private DependencyEdge chooseDependencyEdgeConnecting(
      Node source, Node target, BindingGraph bindingGraph) {
    return bindingGraph.network().edgesConnecting(source, target).stream()
        .flatMap(instancesOf(DependencyEdge.class))
        .findFirst()
        .get();
  }

  /** Returns the subgraph containing only {@link DependencyEdge}s that would not break a cycle. */
  // TODO(dpb): Return a network containing only Binding nodes.
  private ImmutableNetwork<Node, DependencyEdge> nonCycleBreakingDependencyGraph(
      BindingGraph bindingGraph) {
    MutableNetwork<Node, DependencyEdge> dependencyNetwork =
        NetworkBuilder.from(bindingGraph.network())
            .expectedNodeCount(bindingGraph.network().nodes().size())
            .expectedEdgeCount(bindingGraph.dependencyEdges().size())
            .build();
    bindingGraph.dependencyEdges().stream()
        .filter(edge -> !breaksCycle(edge, bindingGraph))
        .forEach(
            edge -> {
              EndpointPair<Node> endpoints = bindingGraph.network().incidentNodes(edge);
              dependencyNetwork.addEdge(endpoints.source(), endpoints.target(), edge);
            });
    return ImmutableNetwork.copyOf(dependencyNetwork);
  }

  /**
   * An ordered set of endpoint pairs representing the edges in the cycle. The target of each pair
   * is the source of the next pair. The target of the last pair is the source of the first pair.
   */
  @AutoValue
  abstract static class Cycle<N> {
    /**
     * The ordered set of endpoint pairs representing the edges in the cycle. The target of each
     * pair is the source of the next pair. The target of the last pair is the source of the first
     * pair.
     */
    abstract ImmutableSet<EndpointPair<N>> endpointPairs();

    /** Returns the nodes that participate in the cycle. */
    ImmutableSet<N> nodes() {
      return endpointPairs().stream()
          .flatMap(pair -> Stream.of(pair.source(), pair.target()))
          .collect(toImmutableSet());
    }

    /** Returns the number of edges in the cycle. */
    int size() {
      return endpointPairs().size();
    }

    /**
     * Shifts this cycle so that it starts with a specific node.
     *
     * @return a cycle equivalent to this one but whose first pair starts with {@code startNode}
     */
    Cycle<N> shift(N startNode) {
      int startIndex = Iterables.indexOf(endpointPairs(), pair -> pair.source().equals(startNode));
      checkArgument(
          startIndex >= 0, "startNode (%s) is not part of this cycle: %s", startNode, this);
      if (startIndex == 0) {
        return this;
      }
      ImmutableSet.Builder<EndpointPair<N>> shifted = ImmutableSet.builder();
      shifted.addAll(skip(endpointPairs(), startIndex));
      shifted.addAll(limit(endpointPairs(), size() - startIndex));
      return new AutoValue_DependencyCycleValidator_Cycle<>(shifted.build());
    }

    @Override
    public final String toString() {
      return endpointPairs().toString();
    }

    /**
     * Creates a {@link Cycle} from a nonempty list of nodes, assuming there is an edge between each
     * pair of nodes as well as an edge from the last node to the first.
     */
    static <N> Cycle<N> fromPath(List<N> nodes) {
      checkArgument(!nodes.isEmpty());
      ImmutableSet.Builder<EndpointPair<N>> cycle = ImmutableSet.builder();
      cycle.add(EndpointPair.ordered(getLast(nodes), nodes.get(0)));
      for (int i = 0; i < nodes.size() - 1; i++) {
        cycle.add(EndpointPair.ordered(nodes.get(i), nodes.get(i + 1)));
      }
      return new AutoValue_DependencyCycleValidator_Cycle<>(cycle.build());
    }
  }
}
