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

package dagger.spi.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

// TODO(bcorso): Move this into dagger/spi?
/**
 * A pluggable visitor for {@link BindingGraph}.
 *
 * <p>Note: This is still experimental and will change.
 */
public interface BindingGraphPlugin {
  /**
   * Called once for each valid root binding graph encountered by the Dagger processor. May report
   * diagnostics using {@code diagnosticReporter}.
   */
  void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter);

  /**
   * Initializes this plugin with a {@link DaggerProcessingEnv}.
   *
   * <p>This will be called once per instance of this plugin, before any graph is
   * {@linkplain #visitGraph(BindingGraph, DiagnosticReporter) visited}.
   */
  default void init(DaggerProcessingEnv processingEnv, Map<String, String> options) {}

  /**
   * Returns the annotation-processing options that this plugin uses to configure behavior.
   *
   * @see javax.annotation.processing.Processor#getSupportedOptions()
   */
  default Set<String> supportedOptions() {
    return Collections.emptySet();
  }

  /**
   * A distinguishing name of the plugin that will be used in diagnostics printed to the messager.
   * By default, the {@linkplain Class#getCanonicalName() fully qualified name} of the plugin is
   * used.
   */
  default String pluginName() {
    return getClass().getCanonicalName();
  }

  /**
   * Perform any extra work after the plugin finished all its visiting. This will be called once per
   * instance of this plugin, after all graphs were {@linkplain #visitGraph(BindingGraph,
   * DiagnosticReporter) visited}
   */
  default void onPluginEnd() {}
}
