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

package dagger.functional.spi;

import static javax.tools.StandardLocation.CLASS_OUTPUT;

import com.google.auto.service.AutoService;
import com.google.common.base.Joiner;
import com.squareup.javapoet.ClassName;
import dagger.model.BindingGraph;
import dagger.model.BindingGraph.ComponentNode;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Properties;
import javax.annotation.processing.Filer;

@AutoService(BindingGraphPlugin.class)
public final class TestPlugin implements BindingGraphPlugin {
  private Filer filer;

  @Override
  public void initFiler(Filer filer) {
    this.filer = filer;
  }

  @Override
  public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
    Properties properties = new Properties();
    int i = 0;
    for (ComponentNode node : bindingGraph.componentNodes()) {
      properties.setProperty(
          String.format("component[%s]", i++), node.componentPath().toString());
    }

    write(bindingGraph, properties);
  }

  private void write(BindingGraph bindingGraph, Properties properties) {
    ClassName rootComponentName =
        ClassName.get(bindingGraph.rootComponentNode().componentPath().currentComponent());
    try (Writer writer =
        filer
            .createResource(
                CLASS_OUTPUT,
                rootComponentName.packageName(),
                Joiner.on('_').join(rootComponentName.simpleNames()) + ".properties")
            .openWriter()) {
      properties.store(writer, "");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
