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

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModuleBindingValidationTest {
  private static final JavaFileObject INCLUDING_MODULE =
      JavaFileObjects.forSourceLines(
          "test.IncludingModule",
          "package test;",
          "",
          "import dagger.Module;",
          "",
          "@Module(includes = TestModule.class)",
          "interface IncludingModule {}");

  private static final JavaFileObject MODULE =
      JavaFileObjects.forSourceLines(
          "test.TestModule",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module(subcomponents = Child.class)",
          "interface TestModule {",
          "  @Binds Object toString(String string);",
          "  @Binds Object toLong(Long l);",
          "}");

  private static final JavaFileObject CHILD =
      JavaFileObjects.forSourceLines(
          "test.Child",
          "package test;",
          "",
          "import dagger.BindsInstance;",
          "import dagger.Subcomponent;",
          "",
          "@Subcomponent(modules = ChildModule.class)",
          "interface Child {",
          "  @Subcomponent.Builder",
          "  interface Builder {",
          "    @BindsInstance Builder object(Object object);",
          "    Child build();",
          "  }",
          "}");

  private static final JavaFileObject CHILD_MODULE =
      JavaFileObjects.forSourceLines(
          "test.ChildModule",
          "package test;",
          "",
          "import dagger.Module;",
          "import dagger.Binds;",
          "",
          "@Module",
          "interface ChildModule {",
          "  @Binds Object toNumber(Number number);",
          "}");

  // Make sure the module-level errors don't show a dependency trace afterwards (note the $).
  private static final String MODULE_MESSAGE =
      Pattern.quote(
              message(
                  "[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
                  "    @Binds Object test.TestModule.toLong(Long)",
                  "    @Binds Object test.TestModule.toString(String)"))
          + "$";

  // Make sure the module-level errors don't show a dependency trace afterwards (note the $).
  private static final String CHILD_MESSAGE =
      Pattern.quote(
              message(
                  "[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
                  "    @BindsInstance test.Child.Builder test.Child.Builder.object(Object)",
                  "    @Binds Object test.ChildModule.toNumber(Number)",
                  "    @Binds Object test.TestModule.toLong(Long)",
                  "    @Binds Object test.TestModule.toString(String)"))
          + "$";

  private static final ImmutableList<JavaFileObject> SOURCES =
      ImmutableList.of(MODULE, INCLUDING_MODULE, CHILD, CHILD_MODULE);

  @Test
  public void error() {
    Compilation compilation =
        daggerCompiler().withOptions("-Adagger.moduleBindingValidation=ERROR").compile(SOURCES);
    assertThat(compilation).failed();

    // Some javacs report only one error for each source line.
    // Assert that one of the expected errors is reported.
    assertThat(compilation)
        .hadErrorContainingMatch(CHILD_MESSAGE + "|" + MODULE_MESSAGE)
        .inFile(MODULE)
        .onLineContaining("interface TestModule");

    assertThat(compilation)
        .hadErrorContaining("test.TestModule has errors")
        .inFile(INCLUDING_MODULE)
        .onLineContaining("TestModule.class");

  }

  @Test
  public void warning() {
    Compilation compilation =
        daggerCompiler().withOptions("-Adagger.moduleBindingValidation=WARNING").compile(SOURCES);
    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(MODULE_MESSAGE)
        .inFile(MODULE)
        .onLineContaining("interface TestModule");

    assertThat(compilation)
        .hadWarningContainingMatch(CHILD_MESSAGE)
        .inFile(MODULE)
        .onLineContaining("interface TestModule");

    // TODO(dpb): When warning, don't repeat in including modules.
    assertThat(compilation)
        .hadWarningContainingMatch(MODULE_MESSAGE)
        .inFile(INCLUDING_MODULE)
        .onLineContaining("interface IncludingModule");

    assertThat(compilation)
        .hadWarningContainingMatch(CHILD_MESSAGE)
        .inFile(INCLUDING_MODULE)
        .onLineContaining("interface IncludingModule");

    // If module binding validation reports warnings, the warnings occur twice:
    // once for TestModule and once for IncludingModule, which includes it.
    assertThat(compilation).hadWarningCount(4);
  }

  @Test
  public void none() {
    Compilation compilation =
        daggerCompiler().withOptions("-Adagger.moduleBindingValidation=NONE").compile(SOURCES);
    assertThat(compilation).succeededWithoutWarnings();
  }
}
