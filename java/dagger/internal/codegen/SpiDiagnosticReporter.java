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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.graph.SuccessorsFunction;
import dagger.model.BindingGraph.BindingNode;
import dagger.model.BindingGraph.ComponentNode;
import dagger.model.BindingGraph.DependencyEdge;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.Node;
import dagger.model.DependencyRequest;
import dagger.spi.ValidationItem;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 * Reports the validation items from {@link
 * dagger.spi.BindingGraphPlugin#visitGraph(dagger.model.BindingGraph) binding graph plugins} and
 * reports them to the {@link Messager}.
 */
// TODO(ronshapiro): If multiple plugins print errors on the same node/edge, should we condense the
// messages and only print the dependency trace once?
final class SpiDiagnosticReporter {
  private final Messager messager;
  private final DependencyRequestFormatter dependencyRequestFormatter;

  @Inject
  SpiDiagnosticReporter(
      Messager messager,
      DependencyRequestFormatter dependencyRequestFormatter) {
    this.messager = messager;
    this.dependencyRequestFormatter = dependencyRequestFormatter;

  }

  /** Reports the {@link ValidationItem}s to the {@link Messager}. */
  void report(
      dagger.model.BindingGraph graph, ImmutableListMultimap<String, ValidationItem> items) {
    for (Entry<String, ValidationItem> entry : items.entries()) {
      new Worker(graph, entry.getKey(), entry.getValue()).report();
    }
  }

  // TODO(dpb): Consider making one Worker per graph/plugin, and calling
  // items.forEach(worker::report) instead of Worker per item
  private class Worker {
    private final dagger.model.BindingGraph graph;
    private final String plugin;
    private final ValidationItem item;
    private final TypeElement rootComponent;

    Worker(dagger.model.BindingGraph graph, String plugin, ValidationItem item) {
      this.graph = graph;
      this.plugin = plugin;
      this.item = item;
      this.rootComponent = graph.rootComponentNode().componentPath().currentComponent();
    }

    /**
     * Reports the supplied diagnostic with extra context for users to the {@link Messager}. For
     * example, if a {@link ValidationItem} is reported on a {@link BindingNode}, a dependency trace
     * will be appended to the diagnostic message.
     */
    private void report() {
      if (isInstance(item.node(), ComponentNode.class)) {
        reportComponent((ComponentNode) item.node().get());
      } else if (isInstance(item.node(), BindingNode.class)) {
        reportBinding((BindingNode) item.node().get());
      } else if (isInstance(item.edge(), DependencyEdge.class)) {
        reportDependencyRequest((DependencyEdge) item.edge().get());
      } else {
        throw new AssertionError("Unknown ValidationItem kind: " + item);
      }
    }

    private void reportComponent(ComponentNode node) {
      CharSequence message = messageBuilder();
      if (!node.componentPath().currentComponent().equals(rootComponent)) {
        message = appendComponentPath(message, node);
      }
      messager.printMessage(item.diagnosticKind(), message, rootComponent);
    }

    private void reportBinding(BindingNode targetNode) {
      // TODO(ronshapiro): should this also include the binding element?
      reportAtEntryPointsWithDependencyTrace(targetNode, messageBuilder());
    }

    private void reportDependencyRequest(DependencyEdge edge) {
      StringBuilder message =
          messageBuilder()
              .append('\n')
              .append(dependencyRequestFormatter.format(edge.dependencyRequest()));

      if (edge.isEntryPoint()) {
        printAtEntryPoint(edge, message);
      } else {
        BindingNode sourceNode = (BindingNode) graph.incidentNodes(edge).source();
        reportAtEntryPointsWithDependencyTrace(sourceNode, message);
      }
    }

    /**
     * For each entry point that depends on {@code targetNode}, appends the {@link
     * #dependencyTrace(DependencyEdge, BindingNode)} to the binding onto {@code message} and prints
     * to the messager.
     */
    private void reportAtEntryPointsWithDependencyTrace(
        BindingNode targetNode, CharSequence baseMessage) {
      for (DependencyEdge entryPoint : graph.entryPointEdgesDependingOnBindingNode(targetNode)) {
        printAtEntryPoint(
            entryPoint,
            new StringBuilder(baseMessage).append(dependencyTrace(entryPoint, targetNode)));
      }
    }

    // TODO(ronshapiro): Adding a DependencyPath type to dagger.model could be useful, i.e.
    // bindingGraph.shortestPathFromEntryPoint(DependencyEdge, BindingNode)
    private CharSequence dependencyTrace(DependencyEdge entryPoint, BindingNode targetNode) {
      checkArgument(entryPoint.isEntryPoint());
      Node entryPointBinding = graph.incidentNodes(entryPoint).target();
      ImmutableList<Node> shortestPath =
          shortestPath(
              node -> Sets.filter(graph.successors(node), BindingNode.class::isInstance),
              entryPointBinding,
              targetNode);

      StringBuilder trace = new StringBuilder(shortestPath.size() * 100 /* a guess heuristic */);
      for (int i = shortestPath.size() - 1; i > 0; i--) {
        Set<Edge> dependenciesBetween =
            graph.edgesConnecting(shortestPath.get(i - 1), shortestPath.get(i));
        DependencyRequest dependencyRequest =
            // If a binding requests a key more than once, any of them should be fine to get to
            // the shortest path
            ((DependencyEdge) Iterables.get(dependenciesBetween, 0)).dependencyRequest();
        trace.append('\n').append(dependencyRequestFormatter.format(dependencyRequest));
      }
      trace.append('\n').append(dependencyRequestFormatter.format(entryPoint.dependencyRequest()));
      return trace;
    }

    /**
     * Prints {@code message} at {@code entryPoint}'s element if it is defined in the {@code
     * rootComponent}, otherwise at the root component.
     */
    private void printAtEntryPoint(DependencyEdge entryPoint, CharSequence message) {
      checkArgument(entryPoint.isEntryPoint());
      Element entryPointElement = entryPoint.dependencyRequest().requestElement().get();
      Element elementToReport =
          // TODO(ronshapiro): should we create a HashSet out of getEnclosedElements() so we don't
          // need to do an O(n) contains() each time?
          rootComponent.getEnclosedElements().contains(entryPointElement)
              ? entryPointElement
              : rootComponent;

      Node component = graph.incidentNodes(entryPoint).source();
      if (!component.equals(graph.rootComponentNode())) {
        message = appendComponentPath(message, component);
      }
      messager.printMessage(item.diagnosticKind(), message, elementToReport);
    }

    private CharSequence appendComponentPath(CharSequence message, Node node) {
      return new StringBuilder(message).append("\ncomponent path: ").append(node.componentPath());
    }

    private StringBuilder messageBuilder() {
      return new StringBuilder(String.format("[%s] ", plugin)).append(item.message());
    }
  }

  private static boolean isInstance(Optional<?> optional, Class<?> clazz) {
    return optional.filter(clazz::isInstance).isPresent();
  }

  /**
   * Returns a shortest path from {@code nodeU} to {@code nodeV} in {@code graph} as a list of the
   * nodes visited in sequence, including both {@code nodeU} and {@code nodeV}. (Note that there may
   * be many possible shortest paths.)
   *
   * <p>If {@code nodeV} is not {@link
   * com.google.common.graph.Graphs#reachableNodes(com.google.common.graph.Graph, Object) reachable}
   * from {@code nodeU}, the list returned is empty.
   *
   * @throws IllegalArgumentException if {@code nodeU} or {@code nodeV} is not present in {@code
   *     graph}
   */
  // This is copied from common.graph since it is not yet open-sourced.
  // TODO(ronshapiro): When the API is released and becomes stable, remove this copy.
  private static <N> ImmutableList<N> shortestPath(SuccessorsFunction<N> graph, N nodeU, N nodeV) {
    if (nodeU.equals(nodeV)) {
      return ImmutableList.of(nodeU);
    }
    Set<N> successors = ImmutableSet.copyOf(graph.successors(nodeU));
    if (successors.contains(nodeV)) {
      return ImmutableList.of(nodeU, nodeV);
    }

    Map<N, N> visitedNodeToPathPredecessor = new HashMap<>(); // encodes shortest path tree
    Queue<N> currentNodes = new ArrayDeque<N>();
    Queue<N> nextNodes = new ArrayDeque<N>();
    for (N node : successors) {
      visitedNodeToPathPredecessor.put(node, nodeU);
    }
    currentNodes.addAll(successors);

    // Perform a breadth-first traversal starting with the successors of nodeU.
    while (!currentNodes.isEmpty()) {
      while (!currentNodes.isEmpty()) {
        N currentNode = currentNodes.remove();
        for (N nextNode : graph.successors(currentNode)) {
          if (visitedNodeToPathPredecessor.containsKey(nextNode)) {
            continue; // we already have a shortest path to nextNode
          }
          visitedNodeToPathPredecessor.put(nextNode, currentNode);
          if (nextNode.equals(nodeV)) {
            ImmutableList.Builder<N> builder = ImmutableList.builder();
            N node = nodeV;
            builder.add(node);
            while (!node.equals(nodeU)) {
              node = visitedNodeToPathPredecessor.get(node);
              builder.add(node);
            }
            return builder.build().reverse();
          }
          nextNodes.add(nextNode);
        }
      }
      Queue<N> emptyQueue = currentNodes;
      currentNodes = nextNodes;
      nextNodes = emptyQueue; // reusing empty queue faster than allocating new one
    }

    return ImmutableList.of();
  }
}
