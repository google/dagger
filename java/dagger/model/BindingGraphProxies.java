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

package dagger.model;

import com.google.common.graph.Network;
import dagger.model.BindingGraph.Edge;
import dagger.model.BindingGraph.MissingBinding;
import dagger.model.BindingGraph.Node;

/**
 * Exposes package-private constructors to the {@code dagger.internal.codegen} package. <em>This
 * class should only be used in the Dagger implementation and is not part of any documented
 * API.</em>
 */
public final class BindingGraphProxies {
  /** Creates a new {@link BindingGraph}. */
  public static BindingGraph bindingGraph(Network<Node, Edge> network, boolean isFullBindingGraph) {
    return BindingGraph.create(network, isFullBindingGraph);
  }

  /** Creates a new {@link MissingBinding}. */
  public static MissingBinding missingBindingNode(ComponentPath component, Key key) {
    return MissingBinding.create(component, key);
  }

  private BindingGraphProxies() {}
}
