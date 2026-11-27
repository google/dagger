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

package dagger.internal.codegen.binding;

import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import androidx.room3.compiler.codegen.XClassName;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.graph.Graph;
import com.google.common.graph.MutableNetwork;
import dagger.internal.codegen.model.BindingGraph.DependencyEdge;
import dagger.internal.codegen.model.BindingGraph.Edge;
import dagger.internal.codegen.model.BindingGraph.Node;
import dagger.internal.codegen.model.DependencyRequest;
import dagger.internal.codegen.xprocessing.XTypeNames;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/** Utility methods for pruning prunable entry points. */
final class PrunableEntryPoints {
  /**
   * Returns the set of unused @OnlyUsedBy annotated entry point methods for all components.
   *
   * <p>Returns an empty set if this is a full binding graph.
   */
  public static ImmutableSet<DependencyRequest> prune(MutableNetwork<Node, Edge> network) {
    ImmutableSet<DependencyEdge> entryPointsToPrune = entryPointsToPrune(network);
    for (DependencyEdge entryPoint : entryPointsToPrune) {
      network.removeEdge(entryPoint);
    }
    return entryPointsToPrune.stream()
        .map(DependencyEdge::dependencyRequest)
        .collect(toImmutableSet());
  }

  private static ImmutableSet<DependencyEdge> entryPointsToPrune(
      MutableNetwork<Node, Edge> network) {
    ImmutableSet<DependencyEdge> entryPoints =
        network.edges().stream()
            .flatMap(instancesOf(DependencyEdge.class))
            .filter(DependencyEdge::isEntryPoint)
            .collect(toImmutableSet());
    SetMultimap<XClassName, DependencyEdge> prunableEntryPoints = LinkedHashMultimap.create();
    Set<DependencyEdge> nonPrunableEntryPoints = new HashSet<>();
    for (DependencyEdge entryPoint : entryPoints) {
      if (isPrunableEntryPoint(entryPoint)) {
        getBindingIdentifiers(entryPoint)
            .forEach(identifier -> prunableEntryPoints.put(identifier, entryPoint));
      } else {
        nonPrunableEntryPoints.add(entryPoint);
      }
    }
    if (prunableEntryPoints.isEmpty()) {
      return ImmutableSet.of();
    }
    Queue<DependencyEdge> queue = new ArrayDeque<>(nonPrunableEntryPoints);
    Set<DependencyEdge> entryPointsToKeep = new HashSet<>();
    Graph<Node> graph = network.asGraph();
    Set<Node> visitedNodes = new HashSet<>();
    while (!queue.isEmpty()) {
      DependencyEdge entryPoint = queue.remove();
      if (entryPointsToKeep.add(entryPoint)) {
        for (XClassName identifier :
            reachableUnknownIdentifiers(
                graph, network.incidentNodes(entryPoint).target(), visitedNodes)) {
          queue.addAll(prunableEntryPoints.get(identifier));
        }
      }
    }
    return prunableEntryPoints.values().stream()
        .filter(entryPoint -> !entryPointsToKeep.contains(entryPoint))
        .collect(toImmutableSet());
  }

  /**
   * Returns binding identifiers for reachable binding nodes that weren't visited in a previous
   * round.
   */
  private static Set<XClassName> reachableUnknownIdentifiers(
      Graph<Node> graph, Node startNode, Set<Node> visitedNodes) {
    Queue<Node> queue = new ArrayDeque<>();
    queue.add(startNode);
    Set<XClassName> identifiers = new HashSet<>();
    while (!queue.isEmpty()) {
      Node node = queue.remove();
      if (visitedNodes.add(node)) {
        if (node instanceof BindingNode) {
          ((BindingNode) node).bindingIdentifier().ifPresent(identifiers::add);
        }
        queue.addAll(graph.successors(node));
      }
    }
    return identifiers;
  }

  private static boolean isPrunableEntryPoint(DependencyEdge entryPoint) {
    return entryPoint.dependencyRequest().requestElement().isPresent()
        && entryPoint
            .dependencyRequest()
            .requestElement()
            .get()
            .xprocessing()
            .hasAnnotation(XTypeNames.ONLY_USED_BY);
  }

  private static ImmutableSet<XClassName> getBindingIdentifiers(DependencyEdge entryPoint) {
    return entryPoint
        .dependencyRequest()
        .requestElement()
        .get()
        .xprocessing()
        .getAnnotations(XTypeNames.ONLY_USED_BY)
        .stream()
        .flatMap(annotation -> annotation.getAsTypeList("value").stream())
        .map(type -> type.getTypeElement().asClassName())
        .collect(toImmutableSet());
  }

  private PrunableEntryPoints() {}
}
