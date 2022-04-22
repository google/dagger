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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import dagger.internal.codegen.compileroption.CompilerOptions;
import dagger.internal.codegen.compileroption.ProcessingOptions;
import dagger.internal.codegen.compileroption.ValidationType;
import dagger.internal.codegen.validation.DiagnosticReporterFactory.DiagnosticReporterImpl;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraphPlugin;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/** Initializes {@link BindingGraphPlugin}s. */
public final class ValidationBindingGraphPlugins {
  private final ImmutableSet<BindingGraphPlugin> plugins;
  private final DiagnosticReporterFactory diagnosticReporterFactory;
  private final XFiler filer;
  private final XProcessingEnv processingEnv;
  private final CompilerOptions compilerOptions;
  private final Map<String, String> processingOptions;

  @Inject
  ValidationBindingGraphPlugins(
      @Validation ImmutableSet<BindingGraphPlugin> plugins,
      DiagnosticReporterFactory diagnosticReporterFactory,
      XFiler filer,
      XProcessingEnv processingEnv,
      CompilerOptions compilerOptions,
      @ProcessingOptions Map<String, String> processingOptions) {
    this.plugins = plugins;
    this.diagnosticReporterFactory = diagnosticReporterFactory;
    this.filer = filer;
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
    plugins.forEach(this::initializePlugin);
  }

  private void initializePlugin(BindingGraphPlugin plugin) {
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
    boolean errorsAsWarnings =
        graph.isFullBindingGraph()
            && compilerOptions.fullBindingGraphValidationType().equals(ValidationType.WARNING);

    boolean isClean = true;
    for (BindingGraphPlugin plugin : plugins) {
      DiagnosticReporterImpl reporter =
          diagnosticReporterFactory.reporter(graph, plugin.pluginName(), errorsAsWarnings);
      plugin.visitGraph(graph, reporter);
      if (reporter.reportedDiagnosticKinds().contains(ERROR)) {
        isClean = false;
      }
    }
    return isClean;
  }
}
