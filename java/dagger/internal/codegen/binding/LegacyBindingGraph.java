/*
 * Copyright (C) 2014 The Dagger Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import dagger.spi.model.Key;
import dagger.spi.model.RequestKind;
import java.util.Collection;
import java.util.Map;
import javax.lang.model.element.TypeElement;

// TODO(bcorso): Remove the LegacyBindingGraph after we've migrated to the new BindingGraph.
/** The canonical representation of a full-resolved graph. */
final class LegacyBindingGraph {
  private final ComponentDescriptor componentDescriptor;
  private final ImmutableMap<Key, ResolvedBindings> contributionBindings;
  private final ImmutableMap<Key, ResolvedBindings> membersInjectionBindings;
  private final ImmutableList<LegacyBindingGraph> subgraphs;

  LegacyBindingGraph(
      ComponentDescriptor componentDescriptor,
      ImmutableMap<Key, ResolvedBindings> contributionBindings,
      ImmutableMap<Key, ResolvedBindings> membersInjectionBindings,
      ImmutableList<LegacyBindingGraph> subgraphs) {
    this.componentDescriptor = componentDescriptor;
    this.contributionBindings = contributionBindings;
    this.membersInjectionBindings = membersInjectionBindings;
    this.subgraphs = checkForDuplicates(subgraphs);
  }

  ComponentDescriptor componentDescriptor() {
    return componentDescriptor;
  }

  ResolvedBindings resolvedBindings(BindingRequest request) {
    return request.isRequestKind(RequestKind.MEMBERS_INJECTION)
        ? membersInjectionBindings.get(request.key())
        : contributionBindings.get(request.key());
  }

  Iterable<ResolvedBindings> resolvedBindings() {
    // Don't return an immutable collection - this is only ever used for looping over all bindings
    // in the graph. Copying is wasteful, especially if is a hashing collection, since the values
    // should all, by definition, be distinct.
    return Iterables.concat(membersInjectionBindings.values(), contributionBindings.values());
  }

  ImmutableList<LegacyBindingGraph> subgraphs() {
    return subgraphs;
  }

  private static ImmutableList<LegacyBindingGraph> checkForDuplicates(
      ImmutableList<LegacyBindingGraph> graphs) {
    Map<TypeElement, Collection<LegacyBindingGraph>> duplicateGraphs =
        Maps.filterValues(
            Multimaps.index(graphs, graph -> graph.componentDescriptor().typeElement()).asMap(),
            overlapping -> overlapping.size() > 1);
    if (!duplicateGraphs.isEmpty()) {
      throw new IllegalArgumentException("Expected no duplicates: " + duplicateGraphs);
    }
    return graphs;
  }
}
