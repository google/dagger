/*
 * Copyright (C) 2022 The Dagger Authors.
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

package spi;

import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ComponentNode;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import javax.tools.Diagnostic;

/**
 * A SPI plugin that reports an error when visiting a root component.
 */
public class TestPlugin implements BindingGraphPlugin {
  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    ComponentNode componentNode = bindingGraph.rootComponentNode();
    if (componentNode.isRealComponent()) {
      diagnosticReporter.reportComponent(
          Diagnostic.Kind.ERROR,
          componentNode,
          "Found component: " + componentNode.componentPath()
      );
    }
  }
}