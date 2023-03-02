/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.internal.codegen.validation;

import com.google.common.base.Preconditions;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraph.ComponentNode;
import dagger.internal.codegen.model.BindingGraphPlugin;
import dagger.internal.codegen.model.DiagnosticReporter;
import java.util.HashSet;
import java.util.Set;

/** BindingGraphPlugin that allows rerun visitGraph on full binding graph. */
public abstract class ValidationBindingGraphPlugin implements BindingGraphPlugin {
  private final Set<ComponentNode> visitFullGraphRequested = new HashSet<>();

  public final boolean visitFullGraphRequested(BindingGraph graph) {
    return visitFullGraphRequested.contains(graph.rootComponentNode());
  }

  /**
   * Request revisit full binding graph for the given pruned graph.
   *
   * <p>If called from revisitFullGraph then no-op.
   */
  public final void requestVisitFullGraph(BindingGraph graph) {
    Preconditions.checkState(
        !graph.isFullBindingGraph(), "Cannot request revisit full graph when visiting full graph.");
    visitFullGraphRequested.add(graph.rootComponentNode());
  }

  public void revisitFullGraph(
      BindingGraph prunedGraph, BindingGraph fullGraph, DiagnosticReporter diagReporter) {}
}
