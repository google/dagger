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
import static dagger.internal.codegen.DaggerStreams.toImmutableSet;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import dagger.internal.codegen.DiagnosticReporterFactory.DiagnosticReporterImpl;
import dagger.model.BindingGraph;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/** The set of SPI and validation plugins. */
@Singleton
final class BindingGraphPlugins {

  @Qualifier
  @Retention(RUNTIME)
  @Target({FIELD, PARAMETER, METHOD})
  @interface TestingPlugins {}

  private final ImmutableSet<BindingGraphPlugin> plugins;
  private final Filer filer;
  private final Types types;
  private final Elements elements;
  private final Map<String, String> processingOptions;
  private final DiagnosticReporterFactory diagnosticReporterFactory;

  BindingGraphPlugins(
      Iterable<BindingGraphPlugin> plugins,
      Filer filer,
      Types types,
      Elements elements,
      Map<String, String> processingOptions,
      DiagnosticReporterFactory diagnosticReporterFactory) {
    this.plugins = ImmutableSet.copyOf(plugins);
    this.filer = checkNotNull(filer);
    this.types = checkNotNull(types);
    this.elements = checkNotNull(elements);
    this.processingOptions = checkNotNull(processingOptions);
    this.diagnosticReporterFactory = checkNotNull(diagnosticReporterFactory);
  }

  /** Returns {@link BindingGraphPlugin#supportedOptions()} from all the plugins. */
  ImmutableSet<String> allSupportedOptions() {
    return plugins
        .stream()
        .flatMap(plugin -> plugin.supportedOptions().stream())
        .collect(toImmutableSet());
  }

  /** Initializes the plugins. */
  void initializePlugins() {
    plugins.forEach(this::initializePlugin);
  }

  private void initializePlugin(BindingGraphPlugin plugin) {
    plugin.initFiler(filer);
    plugin.initTypes(types);
    plugin.initElements(elements);
    Set<String> supportedOptions = plugin.supportedOptions();
    if (!supportedOptions.isEmpty()) {
      plugin.initOptions(Maps.filterKeys(processingOptions, supportedOptions::contains));
    }
  }

  /**
   * Calls {@link BindingGraphPlugin#visitGraph(BindingGraph, DiagnosticReporter)} on each of the
   * SPI plugins
   *
   * @return the kinds of diagnostics that were reported
   */
  // TODO(ronshapiro): Should we validate the uniqueness of plugin names?
  ImmutableSet<Diagnostic.Kind> visitGraph(BindingGraph graph) {
    ImmutableSet.Builder<Diagnostic.Kind> diagnosticKinds = ImmutableSet.builder();
    for (BindingGraphPlugin plugin : plugins) {
      DiagnosticReporterImpl reporter = diagnosticReporterFactory.reporter(graph, plugin);
      plugin.visitGraph(graph, reporter);
      diagnosticKinds.addAll(reporter.reportedDiagnosticKinds());
    }
    return diagnosticKinds.build();
  }
}
