/*
 * Copyright (C) 2015 Google, Inc.
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Utility code that looks for bindings matching a key in all subcomponents in a binding graph so
 * that a user is advised that a binding exists elsewhere when it is not found in the current
 * subgraph. If a binding matching a key exists in a sub- or sibling component, that is often what
 * the user actually wants to use.
 */
class MissingBindingSuggestions {
  /**
   * Searches the entire binding graph from the top-level graph for a binding matching
   * {@code key}.
   */
  static ImmutableList<String> forKey(BindingGraph topLevelGraph, BindingKey key) {
    ImmutableList.Builder<String> resolutions = new ImmutableList.Builder<>();
    Deque<BindingGraph> graphsToTry = new ArrayDeque<>();

    graphsToTry.add(topLevelGraph);
    do {
      BindingGraph graph = graphsToTry.removeLast();
      ResolvedBindings bindings = graph.resolvedBindings().get(key);
      if ((bindings == null) || bindings.bindings().isEmpty()) {
        graphsToTry.addAll(graph.subgraphs().values());
      } else {
        resolutions.add("A binding with matching key exists in component: "
            + graph.componentDescriptor().componentDefinitionType().getQualifiedName());
      }
    } while (!graphsToTry.isEmpty());

    return resolutions.build();
  }

  private MissingBindingSuggestions() {}
}
