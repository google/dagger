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

package dagger.internal.codegen.base;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.min;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.graph.SuccessorsFunction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An implementation of Tarjan's algorithm for finding the SCC of a graph. This is based on the
 * psuedo code algorithm here:
 * http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
 */
public final class TarjanSCCs {

  /** Returns the set of strongly connected components in reverse topological order. */
  public static <NodeT> ImmutableList<ImmutableSet<NodeT>> compute(
      ImmutableCollection<NodeT> nodes, SuccessorsFunction<NodeT> successorsFunction) {
    return new TarjanSCC<>(nodes, successorsFunction).compute();
  }

  private static class TarjanSCC<NodeT> {
    private final ImmutableCollection<NodeT> nodes;
    private final SuccessorsFunction<NodeT> successorsFunction;
    private final Deque<NodeT> stack;
    private final Set<NodeT> onStack;
    private final Map<NodeT, Integer> indexes;
    private final Map<NodeT, Integer> lowLinks;
    private final List<ImmutableSet<NodeT>> stronglyConnectedComponents = new ArrayList<>();

    TarjanSCC(ImmutableCollection<NodeT> nodes, SuccessorsFunction<NodeT> successorsFunction) {
      this.nodes = nodes;
      this.successorsFunction = successorsFunction;
      this.stack = new ArrayDeque<>(nodes.size());
      this.onStack = Sets.newHashSetWithExpectedSize(nodes.size());
      this.indexes = Maps.newHashMapWithExpectedSize(nodes.size());
      this.lowLinks = Maps.newHashMapWithExpectedSize(nodes.size());
    }

    private ImmutableList<ImmutableSet<NodeT>> compute() {
      checkState(indexes.isEmpty(), "TarjanSCC#compute() can only be called once per instance!");
      for (NodeT node : nodes) {
        if (!indexes.containsKey(node)) {
          stronglyConnect(node);
        }
      }
      return ImmutableList.copyOf(stronglyConnectedComponents);
    }

    private void stronglyConnect(NodeT node) {
      // Set the index and lowLink for node to the smallest unused index and add it to the stack
      lowLinks.put(node, indexes.size());
      indexes.put(node, indexes.size());
      stack.push(node);
      onStack.add(node);

      for (NodeT successor : successorsFunction.successors(node)) {
        if (!indexes.containsKey(successor)) {
          // Successor has not been processed.
          stronglyConnect(successor);
          lowLinks.put(node, min(lowLinks.get(node), lowLinks.get(successor)));
        } else if (onStack.contains(successor)) {
          // Successor is on the stack and hence in the current SCC.
          lowLinks.put(node, min(lowLinks.get(node), indexes.get(successor)));
        } else {
          // Successor is not on the stack and hence in an already processed SCC, so ignore.
        }
      }

      // If node is the root of the SCC, pop the stack until reaching the root to get all SCC nodes.
      if (lowLinks.get(node).equals(indexes.get(node))) {
        ImmutableSet.Builder<NodeT> scc = ImmutableSet.builder();
        NodeT currNode;
        do {
          currNode = stack.pop();
          onStack.remove(currNode);
          scc.add(currNode);
        } while (!node.equals(currNode));
        stronglyConnectedComponents.add(scc.build());
      }
    }
  }

  private TarjanSCCs() {}
}
