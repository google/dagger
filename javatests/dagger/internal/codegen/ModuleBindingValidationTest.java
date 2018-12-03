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
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.TestUtils.message;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModuleBindingValidationTest {
  private static final JavaFileObject MODULE =
      JavaFileObjects.forSourceLines(
          "test.TestModule",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module",
          "interface TestModule {",
          "  @Binds Object toString(String string);",
          "  @Binds Object toLong(Long l);",
          "}");

  private static final JavaFileObject INCLUDING_MODULE =
      JavaFileObjects.forSourceLines(
          "test.IncludingModule",
          "package test;",
          "",
          "import dagger.Module;",
          "",
          "@Module(includes = TestModule.class)",
          "interface IncludingModule {}");

  @Test
  public void moduleBindingValidationErrors() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.moduleBindingValidation=ERROR")
            .compile(MODULE, INCLUDING_MODULE);
    assertThat(compilation).failed();

    // Make sure the module-level error doesn't show a dependency trace afterwards (note the $).
    assertThat(compilation)
        .hadErrorContainingMatch(
            message(
                "^\\Q[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
                "    @Binds Object test.TestModule.toLong(Long)",
                "    @Binds Object test.TestModule.toString(String)\\E$"))
        .inFile(MODULE)
        .onLineContaining("interface TestModule");

    assertThat(compilation)
        .hadErrorContaining("test.TestModule has errors")
        .inFile(INCLUDING_MODULE)
        .onLineContaining("TestModule.class");

    // The duplicate bindings error is reported only once, for TestModule, and not again for
    // IncludingModule.
    assertThat(compilation).hadErrorCount(2);
  }

  @Test
  public void moduleBindingValidationWarning() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.moduleBindingValidation=WARNING")
            .compile(MODULE, INCLUDING_MODULE);
    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(
            message(
                "^\\Q[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
                "    @Binds Object test.TestModule.toLong(Long)",
                "    @Binds Object test.TestModule.toString(String)\\E$"))
        .inFile(MODULE)
        .onLineContaining("interface TestModule");

    // Make sure the module-level error doesn't show a dependency trace.
    assertThat(compilation)
        .hadWarningContainingMatch(
            message(
                "^\\Q[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
                "    @Binds Object test.TestModule.toLong(Long)",
                "    @Binds Object test.TestModule.toString(String)\\E$"))
        .inFile(INCLUDING_MODULE)
        .onLineContaining("interface IncludingModule");

    // If module binding validation reports warnings, the duplicate bindings warning occurs twice:
    // once for TestModule and once for IncludingModule, which includes it.
    assertThat(compilation).hadWarningCount(2);
  }

  @Test
  public void moduleBindingValidationNone() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.moduleBindingValidation=NONE")
            .compile(MODULE, INCLUDING_MODULE);
    assertThat(compilation).succeededWithoutWarnings();
  }
}
