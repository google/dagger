/*
 * Copyright (C) 2017 The Dagger Authors.
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

import dagger.model.BindingGraph;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;

/**
 * A pluggable visitor for {@link BindingGraph}.
 *
 * <p>Note: This is still experimental and will change.
 */
public interface BindingGraphPlugin {
  /**
   * Called once for each valid root binding graph encountered by the Dagger processor.
   *
   * @return items used to print notes, warnings, and errors about the binding graph to the {@link
   *     Messager}
   */
  List<ValidationItem> visitGraph(BindingGraph bindingGraph);

  /**
   * Initializes this plugin with a {@link Filer} that it can use to write Java or other files based
   * on the binding graph. This will be called once per instance of this plugin, before any graph is
   * {@linkplain #visitGraph(BindingGraph) visited}.
   */
  default void initFiler(Filer filer) {}

  /**
   * Initializes this plugin with a filtered view of the options passed on the {@code javac}
   * command-line for all keys from {@link #supportedOptions()}. This will be called once per
   * instance of this plugin, before any graph is {@linkplain #visitGraph(BindingGraph) visited}.
   *
   * @see ProcessingEnvironment#getOptions()
   */
  default void initOptions(Map<String, String> options) {}

  /**
   * Returns the annotation-processing options that this plugin uses to configure behavior.
   *
   * @see Processor#getSupportedOptions()
   */
  default Set<String> supportedOptions() {
    return Collections.emptySet();
  }

  /**
   * A distinguishing name of the plugin that will be used in diagnostics printed to the {@link
   * Messager}. By default, the {@linkplain Class#getCanonicalName() fully qualified name} of the
   * plugin is used.
   */
  default String pluginName() {
    return getClass().getCanonicalName();
  }
}
