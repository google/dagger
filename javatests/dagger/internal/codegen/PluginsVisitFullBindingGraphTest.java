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

import static dagger.internal.codegen.TestUtils.endsWithMessage;
import static javax.tools.Diagnostic.Kind.ERROR;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableMap;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraphPlugin;
import dagger.spi.model.DiagnosticReporter;
import dagger.testing.compile.CompilerTests;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for -Adagger.pluginsVisitFullBindingGraph. */
@RunWith(JUnit4.class)
public final class PluginsVisitFullBindingGraphTest {
  private static final Source MODULE_WITHOUT_ERRORS =
      CompilerTests.javaSource(
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

  private static final Source MODULE_WITH_ERRORS =
      CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(MODULE_WITH_ERRORS)
        .withBindingGraphPlugins(ErrorPlugin::new)
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testWithVisitPlugins() {
    CompilerTests.daggerCompiler(MODULE_WITH_ERRORS)
        .withProcessingOptions(ImmutableMap.of("dagger.pluginsVisitFullBindingGraphs", "Enabled"))
        .withBindingGraphPlugins(ErrorPlugin::new)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContainingMatch(PLUGIN_ERROR_MESSAGE.toString())
                  .onSource(MODULE_WITH_ERRORS)
                  .onLineContaining("interface ModuleWithErrors");
            });
  }

  @Test
  public void testWithValidationNone() {
    CompilerTests.daggerCompiler(MODULE_WITHOUT_ERRORS)
        .withProcessingOptions(ImmutableMap.of("dagger.fullBindingGraphValidation", "NONE"))
        .withBindingGraphPlugins(ErrorPlugin::new)
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testWithValidationError() {
    // Test that pluginsVisitFullBindingGraph is enabled with fullBindingGraphValidation.
    CompilerTests.daggerCompiler(MODULE_WITHOUT_ERRORS)
        .withProcessingOptions(ImmutableMap.of("dagger.fullBindingGraphValidation", "ERROR"))
        .withBindingGraphPlugins(ErrorPlugin::new)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContainingMatch(PLUGIN_ERROR_MESSAGE.toString())
                  .onSource(MODULE_WITHOUT_ERRORS)
                  .onLineContaining("interface ModuleWithoutErrors");
            });
  }

  @Test
  public void testWithValidationErrorAndVisitPlugins() {
    CompilerTests.daggerCompiler(MODULE_WITHOUT_ERRORS)
        .withProcessingOptions(
            ImmutableMap.of(
                "dagger.fullBindingGraphValidation", "ERROR",
                "dagger.pluginsVisitFullBindingGraphs", "Enabled"))
        .withBindingGraphPlugins(ErrorPlugin::new)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContainingMatch(PLUGIN_ERROR_MESSAGE.toString())
                  .onSource(MODULE_WITHOUT_ERRORS)
                  .onLineContaining("interface ModuleWithoutErrors");
            });
  }

  /** A test plugin that just reports each component with the given {@link Diagnostic.Kind}. */
  private static final class ErrorPlugin implements BindingGraphPlugin {
    @Override
    public void visitGraph(BindingGraph bindingGraph, DiagnosticReporter diagnosticReporter) {
      diagnosticReporter.reportComponent(ERROR, bindingGraph.rootComponentNode(), "Error!");
    }
  }
}
