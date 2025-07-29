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

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.extension.DaggerStreams.instancesOf;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.Network;
import com.google.common.graph.NetworkBuilder;
import dagger.internal.codegen.base.TarjanSCCs;
import dagger.internal.codegen.model.BindingGraph.Edge;
import dagger.internal.codegen.model.BindingGraph.Node;
import java.util.Map;

/** Transformations on the binding graph network. */
final class BindingGraphTransformations {
  /** Returns a network where {@link BindingType} is present for all binding nodes. */
  static MutableNetwork<Node, Edge> withFixedBindingTypes(MutableNetwork<Node, Edge> network) {
    ImmutableSet<BindingNode> bindingsToFix = bindingsWithMissingBindingTypes(network);
    if (bindingsToFix.isEmpty()) {
      return network;
    }

    MutableNetwork<Node, Edge> fixedNetwork = withFixedBindingTypes(network, bindingsToFix);

    // Check that all bindings now have a BindingType in the fixed network.
    checkState(bindingsWithMissingBindingTypes(fixedNetwork).isEmpty());
    return fixedNetwork;
  }

  private static MutableNetwork<Node, Edge> withFixedBindingTypes(
      Network<Node, Edge> network, ImmutableSet<BindingNode> bindingsToFix) {
    // Topologically sort the bindings so that we're guaranteed all dependencies of a binding are
    // fixed before the bindings itself is fixed.
    ImmutableList<ImmutableSet<BindingNode>> topologicallySortedBindingsToFix =
        TarjanSCCs.compute(
            bindingsToFix,
            binding ->
                network.successors(binding).stream()
                    .flatMap(instancesOf(BindingNode.class))
                    // Filter because we only care about direct dependencies on bindings that need
                    // to be fixed. There might be other cycles through nodes that already have a
                    // type, but those don't matter because it won't affect how we will fix the
                    // types for these bindings.
                    .filter(bindingsToFix::contains)
                    .collect(toImmutableSet()));

    Map<BindingNode, BindingNode> replacements =
        Maps.newHashMapWithExpectedSize(bindingsToFix.size());
    for (ImmutableSet<BindingNode> connectedBindings : topologicallySortedBindingsToFix) {
      BindingType successorBindingType =
          connectedBindings.stream()
                  .flatMap(binding -> network.successors(binding).stream())
                  .flatMap(instancesOf(BindingNode.class))
                  .filter(binding -> !connectedBindings.contains(binding))
                  .map(binding -> replacements.getOrDefault(binding, binding))
                  .anyMatch(BindingNode::isProduction)
              ? BindingType.PRODUCTION
              : BindingType.PROVISION;
      for (BindingNode bindingNode : connectedBindings) {
        replacements.put(bindingNode, bindingNode.withBindingType(successorBindingType));
      }
    }
    return withReplacedBindings(network, ImmutableMap.copyOf(replacements));
  }

  private static ImmutableSet<BindingNode> bindingsWithMissingBindingTypes(
      Network<Node, Edge> network) {
    return network.nodes().stream()
        .flatMap(instancesOf(BindingNode.class))
        .filter(binding -> binding.delegate().optionalBindingType().isEmpty())
        .collect(toImmutableSet());
  }

  // Note: This method creates an entirely new network rather than replacing individual nodes and
  // edges in the original network. We can reconsider this choice, e.g. if it turns out to be
  // too inefficient, but my initial thought is that this approach is a bit nicer because it
  // maintains the original node and edge iteration order, which could be nice for debugging.
  private static MutableNetwork<Node, Edge> withReplacedBindings(
      Network<Node, Edge> network, ImmutableMap<? extends Node, ? extends Node> replacementNodes) {
    MutableNetwork<Node, Edge> newNetwork = NetworkBuilder.from(network).build();
    for (Node node : network.nodes()) {
      newNetwork.addNode(replacementNodes.containsKey(node) ? replacementNodes.get(node) : node);
    }
    for (Edge edge : network.edges()) {
      EndpointPair<Node> incidentNodes = network.incidentNodes(edge);
      Node source = incidentNodes.source();
      Node target = incidentNodes.target();
      newNetwork.addEdge(
          replacementNodes.containsKey(source) ? replacementNodes.get(source) : source,
          replacementNodes.containsKey(target) ? replacementNodes.get(target) : target,
          edge);
    }
    return newNetwork;
  }

  private BindingGraphTransformations() {}
}
