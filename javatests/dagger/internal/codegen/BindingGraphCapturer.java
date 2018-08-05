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

import com.google.common.collect.ImmutableMap;
import dagger.model.BindingGraph;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;

/**
 * A testing plugin that captures {@link dagger.model.BindingGraph}s for tests to make assertions
 * about.
 */
// TODO(dpb): Move to dagger.spi.testing?
final class BindingGraphCapturer implements BindingGraphPlugin {

  private final ImmutableMap.Builder<String, BindingGraph> bindingGraphs = ImmutableMap.builder();

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    bindingGraphs.put(
        bindingGraph
            .rootComponentNode()
            .componentPath()
            .currentComponent()
            .getQualifiedName()
            .toString(),
        bindingGraph);
  }

  /** Returns a map of binding graphs, indexed by the canonical name of the root component type. */
  public ImmutableMap<String, BindingGraph> bindingGraphs() {
    return bindingGraphs.build();
  }
}
