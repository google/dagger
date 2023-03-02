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

package dagger.internal.codegen.validation;

import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.compileroption.ProcessingOptions;
import dagger.internal.codegen.compileroption.ValidationType;
import dagger.internal.codegen.model.BindingGraph;
import dagger.internal.codegen.model.BindingGraphPlugin;
import dagger.internal.codegen.model.DaggerProcessingEnv;
import dagger.internal.codegen.validation.DiagnosticReporterFactory.DiagnosticReporterImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/** Initializes {@link BindingGraphPlugin}s. */
public final class ValidationBindingGraphPlugins {
  private final ImmutableSet<ValidationBindingGraphPlugin> plugins;
  private final DiagnosticReporterFactory diagnosticReporterFactory;
  private final XProcessingEnv processingEnv;
  private final CompilerOptions compilerOptions;
  private final Map<String, String> processingOptions;

  @Inject
  ValidationBindingGraphPlugins(
      @Validation ImmutableSet<ValidationBindingGraphPlugin> plugins,
      DiagnosticReporterFactory diagnosticReporterFactory,
      XProcessingEnv processingEnv,
      CompilerOptions compilerOptions,
      @ProcessingOptions Map<String, String> processingOptions) {
    this.plugins = plugins;
    this.diagnosticReporterFactory = diagnosticReporterFactory;
    this.processingEnv = processingEnv;
    this.compilerOptions = compilerOptions;
    this.processingOptions = processingOptions;
  }

  /** Returns {@link BindingGraphPlugin#supportedOptions()} from all the plugins. */
  public ImmutableSet<String> allSupportedOptions() {
    return plugins.stream()
        .flatMap(plugin -> plugin.supportedOptions().stream())
        .collect(toImmutableSet());
  }

  /** Initializes the plugins. */
  // TODO(ronshapiro): Should we validate the uniqueness of plugin names?
  public void initializePlugins() {
    DaggerProcessingEnv daggerProcessingEnv = DaggerProcessingEnv.from(processingEnv);
    plugins.forEach(plugin -> plugin.init(daggerProcessingEnv, pluginOptions(plugin)));
  }

  /** Returns the filtered map of processing options supported by the given plugin. */
  private ImmutableMap<String, String> pluginOptions(BindingGraphPlugin plugin) {
    Set<String> supportedOptions = plugin.supportedOptions();
    return supportedOptions.isEmpty()
        ? ImmutableMap.of()
        : ImmutableMap.copyOf(Maps.filterKeys(processingOptions, supportedOptions::contains));
  }

  /** Returns {@code false} if any of the plugins reported an error. */
  boolean visit(Optional<BindingGraph> prunedGraph, Supplier<BindingGraph> fullGraphSupplier) {
    BindingGraph graph = prunedGraph.isPresent() ? prunedGraph.get() : fullGraphSupplier.get();

    boolean isClean = true;
    List<ValidationBindingGraphPlugin> rerunPlugins = new ArrayList<>();
    for (ValidationBindingGraphPlugin plugin : plugins) {
      DiagnosticReporterImpl reporter = createReporter(plugin.pluginName(), graph);
      plugin.visitGraph(graph, reporter);
      if (plugin.visitFullGraphRequested(graph)) {
        rerunPlugins.add(plugin);
      }
      if (reporter.reportedDiagnosticKinds().contains(ERROR)) {
        isClean = false;
      }
    }
    if (!rerunPlugins.isEmpty()) {
      BindingGraph fullGraph = fullGraphSupplier.get();
      for (ValidationBindingGraphPlugin plugin : rerunPlugins) {
        DiagnosticReporterImpl reporter = createReporter(plugin.pluginName(), fullGraph);
        plugin.revisitFullGraph(prunedGraph.get(), fullGraph, reporter);
        if (reporter.reportedDiagnosticKinds().contains(ERROR)) {
          isClean = false;
        }
      }
    }
    return isClean;
  }

  private DiagnosticReporterImpl createReporter(String pluginName, BindingGraph graph) {
    boolean errorsAsWarnings =
        graph.isFullBindingGraph()
            && compilerOptions.fullBindingGraphValidationType().equals(ValidationType.WARNING);
    return errorsAsWarnings
        ? diagnosticReporterFactory.reporterWithErrorAsWarnings(graph, pluginName)
        : diagnosticReporterFactory.reporter(graph, pluginName);
  }

  public void endPlugins() {
    plugins.forEach(BindingGraphPlugin::onPluginEnd);
  }
}
