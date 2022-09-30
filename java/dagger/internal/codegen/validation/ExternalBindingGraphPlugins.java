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

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableSet;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.processing.XFiler;
import androidx.room.compiler.processing.XProcessingEnv;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import dagger.internal.codegen.compileroption.ProcessingOptions;
import dagger.internal.codegen.validation.DiagnosticReporterFactory.DiagnosticReporterImpl;
import dagger.spi.DiagnosticReporter;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraphPlugin;
import dagger.spi.model.DaggerProcessingEnv;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Initializes {@link BindingGraphPlugin}s. */
public final class ExternalBindingGraphPlugins {
  private final ImmutableSet<dagger.spi.BindingGraphPlugin> legacyPlugins;
  private final ImmutableSet<BindingGraphPlugin> plugins;
  private final DiagnosticReporterFactory diagnosticReporterFactory;
  private final XFiler filer;
  private final XProcessingEnv processingEnv;
  private final Map<String, String> processingOptions;

  @Inject
  ExternalBindingGraphPlugins(
      @External ImmutableSet<dagger.spi.BindingGraphPlugin> legacyPlugins,
      @External ImmutableSet<BindingGraphPlugin> plugins,
      DiagnosticReporterFactory diagnosticReporterFactory,
      XFiler filer,
      XProcessingEnv processingEnv,
      @ProcessingOptions Map<String, String> processingOptions) {
    this.legacyPlugins = legacyPlugins;
    this.plugins = plugins;
    this.diagnosticReporterFactory = diagnosticReporterFactory;
    this.filer = filer;
    this.processingEnv = processingEnv;
    this.processingOptions = processingOptions;
  }

  /** Returns {@link BindingGraphPlugin#supportedOptions()} from all the plugins. */
  public ImmutableSet<String> allSupportedOptions() {
    return Stream.concat(
            legacyPlugins.stream().flatMap(plugin -> plugin.supportedOptions().stream()),
            plugins.stream().flatMap(plugin -> plugin.supportedOptions().stream()))
        .collect(toImmutableSet());
  }

  /** Initializes the plugins. */
  // TODO(ronshapiro): Should we validate the uniqueness of plugin names?
  public void initializePlugins() {
    plugins.forEach(this::initializePlugin);
    legacyPlugins.forEach(this::initializeLegacyPlugin);
  }

  private void initializePlugin(BindingGraphPlugin plugin) {
    Set<String> supportedOptions = plugin.supportedOptions();
    Map<String, String> filteredOptions =
        supportedOptions.isEmpty()
            ? ImmutableMap.of()
            : Maps.filterKeys(processingOptions, supportedOptions::contains);
    plugin.init(DaggerProcessingEnv.from(processingEnv), filteredOptions);
  }

  private void initializeLegacyPlugin(dagger.spi.BindingGraphPlugin plugin) {
    plugin.initFiler(toJavac(filer));
    plugin.initTypes(toJavac(processingEnv).getTypeUtils()); // ALLOW_TYPES_ELEMENTS
    plugin.initElements(toJavac(processingEnv).getElementUtils()); // ALLOW_TYPES_ELEMENTS
    Set<String> supportedOptions = plugin.supportedOptions();
    if (!supportedOptions.isEmpty()) {
      plugin.initOptions(Maps.filterKeys(processingOptions, supportedOptions::contains));
    }
  }

  /** Returns {@code false} if any of the plugins reported an error. */
  boolean visit(BindingGraph graph) {
    return visitLegacyPlugins(graph) && visitPlugins(graph);
  }

  private boolean visitLegacyPlugins(BindingGraph graph) {
    // Return early to avoid converting the binding graph when there are no external plugins.
    if (legacyPlugins.isEmpty()) {
      return true;
    }

    dagger.model.BindingGraph legacyGraph = ExternalBindingGraphConverter.fromSpiModel(graph);
    boolean isClean = true;
    for (dagger.spi.BindingGraphPlugin legacyPlugin : legacyPlugins) {
      DiagnosticReporterImpl reporter =
          diagnosticReporterFactory.reporter(graph, legacyPlugin.pluginName());
      DiagnosticReporter legacyReporter = ExternalBindingGraphConverter.fromSpiModel(reporter);
      legacyPlugin.visitGraph(legacyGraph, legacyReporter);
      if (reporter.reportedDiagnosticKinds().contains(ERROR)) {
        isClean = false;
      }
    }
    return isClean;
  }

  private boolean visitPlugins(BindingGraph graph) {
    boolean isClean = true;
    for (BindingGraphPlugin plugin : plugins) {
      DiagnosticReporterImpl reporter =
          diagnosticReporterFactory.reporter(graph, plugin.pluginName());
      plugin.visitGraph(graph, reporter);
      if (reporter.reportedDiagnosticKinds().contains(ERROR)) {
        isClean = false;
      }
    }
    return isClean;
  }

  public void endPlugins() {
    legacyPlugins.forEach(dagger.spi.BindingGraphPlugin::onPluginEnd);
    plugins.forEach(BindingGraphPlugin::onPluginEnd);
  }
}
