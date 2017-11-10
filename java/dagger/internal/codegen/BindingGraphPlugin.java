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

package dagger.internal.codegen;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;

/**
 * A pluggable visitor for {@link BindingNetwork}.
 *
 * <p>Note: This is still experimental and will change.
 */
public abstract class BindingGraphPlugin {

  private Filer filer;

  final void setFiler(Filer filer) {
    this.filer = filer;
  }

  /**
   * Returns a filer that this plug-in can use to write Java or other files based on the binding
   * graph.
   */
  protected final Filer filer() {
    return filer;
  }

  /** Called once for each valid root binding graph encountered by the Dagger processor. */
  protected abstract void visitGraph(BindingNetwork bindingNetwork);

  /**
   * Returns the annotation-processing options that this plugin uses to configure behavior.
   *
   * @see Processor#getSupportedOptions()
   */
  protected Set<String> getSupportedOptions() {
    return Collections.emptySet();
  }

  /**
   * If {@link #getSupportedOptions()} returns a non-empty set, then this method will be called with
   * matching options that were actually passed on the {@code javac} command-line.
   *
   * @see ProcessingEnvironment#getOptions()
   */
  // TODO(dpb, ronshapiro): Consider a protected method returning ProcessingEnvironment instead.
  protected void setOptions(Map<String, String> options) {}
}
