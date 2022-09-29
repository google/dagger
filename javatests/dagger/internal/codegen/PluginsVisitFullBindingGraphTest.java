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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static dagger.internal.codegen.TestUtils.endsWithMessage;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import dagger.model.BindingGraph;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for -Adagger.pluginsVisitFullBindingGraph. */
@RunWith(JUnit4.class)
public final class PluginsVisitFullBindingGraphTest {
  private static final JavaFileObject MODULE_WITHOUT_ERRORS =
      JavaFileObjects.forSourceLines(
          "test.ModuleWithoutErrors",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module",
          "interface ModuleWithoutErrors {",
          "  @Binds Object object(String string);",
          "}");

  private static final JavaFileObject MODULE_WITH_ERRORS =
      JavaFileObjects.forSourceLines(
          "test.ModuleWithErrors",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module",
          "interface ModuleWithErrors {",
          "  @Binds Object object1(String string);",
          "  @Binds Object object2(Long l);",
          "}");

  private static final Pattern PLUGIN_ERROR_MESSAGE =
      endsWithMessage(
          "[dagger.internal.codegen.PluginsVisitFullBindingGraphTest.ErrorPlugin] Error!");

  @Test
  public void testNoFlags() {
    Compilation compilation = daggerCompiler().compile(MODULE_WITH_ERRORS);
    assertThat(compilation).succeeded();
  }

  @Test
  public void testWithVisitPlugins() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.pluginsVisitFullBindingGraphs=Enabled")
            .compile(MODULE_WITH_ERRORS);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContainingMatch(PLUGIN_ERROR_MESSAGE)
        .inFile(MODULE_WITH_ERRORS)
        .onLineContaining("interface ModuleWithErrors");
  }

  @Test
  public void testWithValidationNone() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=NONE")
            .compile(MODULE_WITHOUT_ERRORS);
    assertThat(compilation).succeeded();
  }

  @Test
  public void testWithValidationError() {
    // Test that pluginsVisitFullBindingGraph is enabled with fullBindingGraphValidation.
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(MODULE_WITHOUT_ERRORS);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContainingMatch(PLUGIN_ERROR_MESSAGE)
        .inFile(MODULE_WITHOUT_ERRORS)
        .onLineContaining("interface ModuleWithoutErrors");
  }

  @Test
  public void testWithValidationErrorAndVisitPlugins() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .withOptions("-Adagger.pluginsVisitFullBindingGraphs=Enabled")
            .compile(MODULE_WITHOUT_ERRORS);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContainingMatch(PLUGIN_ERROR_MESSAGE)
        .inFile(MODULE_WITHOUT_ERRORS)
        .onLineContaining("interface ModuleWithoutErrors");
  }

  /** A test plugin that just reports each component with the given {@link Diagnostic.Kind}. */
  private static final class ErrorPlugin implements BindingGraphPlugin {
    @Override
    public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
      diagnosticReporter.reportComponent(ERROR, bindingGraph.rootComponentNode(), "Error!");
    }
  }

  private static Compiler daggerCompiler() {
    return javac().withProcessors(ComponentProcessor.forTesting(new ErrorPlugin()));
  }
}
