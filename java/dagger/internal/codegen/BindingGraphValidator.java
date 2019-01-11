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

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.common.collect.ImmutableSet;
import dagger.internal.codegen.DiagnosticReporterFactory.DiagnosticReporterImpl;
import dagger.model.BindingGraph;
import dagger.spi.BindingGraphPlugin;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Validates a {@link BindingGraph}. */
@Singleton
final class BindingGraphValidator {
  private final ImmutableSet<BindingGraphPlugin> validationPlugins;
  private final ImmutableSet<BindingGraphPlugin> externalPlugins;
  private final DiagnosticReporterFactory diagnosticReporterFactory;

  @Inject
  BindingGraphValidator(
      @Validation Set<BindingGraphPlugin> validationPlugins,
      ImmutableSet<BindingGraphPlugin> externalPlugins,
      DiagnosticReporterFactory diagnosticReporterFactory) {
    this.validationPlugins = ImmutableSet.copyOf(validationPlugins);
    this.externalPlugins = ImmutableSet.copyOf(externalPlugins);
    this.diagnosticReporterFactory = checkNotNull(diagnosticReporterFactory);
  }

  /** Returns {@code true} if no errors are reported for {@code graph}. */
  boolean isValid(BindingGraph graph) {
    return isValid(validationPlugins, graph) && isValid(externalPlugins, graph);
  }

  private boolean isValid(ImmutableSet<BindingGraphPlugin> plugins, BindingGraph graph) {
    boolean isValid = true;
    for (BindingGraphPlugin plugin : plugins) {
      DiagnosticReporterImpl reporter = diagnosticReporterFactory.reporter(graph, plugin);
      plugin.visitGraph(graph, reporter);
      if (reporter.reportedDiagnosticKinds().contains(ERROR)) {
        isValid = false;
      }
    }
    return isValid;
  }
}
