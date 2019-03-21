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

package dagger.spi;

import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import dagger.model.BindingGraph;
import java.util.Map;
import java.util.Set;

@AutoService(BindingGraphPlugin.class)
public final class FailingPlugin implements BindingGraphPlugin {
  private Map<String, String> options;

  @Override
  public Set<String> supportedOptions() {
    return ImmutableSet.of(
        "error_on_binding",
        "error_on_dependency",
        "error_on_component",
        "error_on_subcomponents");
  }

  @Override
  public void initOptions(Map<String, String> options) {
    this.options = options;
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    if (options.containsKey("error_on_binding")) {
      String key = options.get("error_on_binding");
      bindingGraph.bindings().stream()
          .filter(binding -> binding.key().toString().equals(key))
          .forEach(
              binding ->
                  diagnosticReporter.reportBinding(ERROR, binding, "Bad Binding: %s", binding));
    }

    if (options.containsKey("error_on_component")) {
      diagnosticReporter.reportComponent(
          ERROR,
          bindingGraph.rootComponentNode(),
          "Bad Component: %s",
          bindingGraph.rootComponentNode());
    }

    if (options.containsKey("error_on_subcomponents")) {
      bindingGraph.componentNodes().stream()
          .filter(componentNode -> !componentNode.componentPath().atRoot())
          .forEach(
              componentNode ->
                  diagnosticReporter.reportComponent(
                      ERROR, componentNode, "Bad Subcomponent: %s", componentNode));
    }

    if (options.containsKey("error_on_dependency")) {
      String dependency = options.get("error_on_dependency");
      bindingGraph.dependencyEdges().stream()
          .filter(
              edge ->
                  edge.dependencyRequest()
                      .requestElement()
                      .get()
                      .getSimpleName()
                      .contentEquals(dependency))
          .forEach(
              edge -> diagnosticReporter.reportDependency(ERROR, edge, "Bad Dependency: %s", edge));
    }

  }

  @Override
  public String pluginName() {
    return "FailingPlugin";
  }
}
