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
import static dagger.internal.codegen.TestUtils.endsWithMessage;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModuleBindingValidationTest {
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
          "  @Binds Object duplicate1(String string);",
          "  @Binds Object duplicate2(Long l);",
          "  @Binds Number missingDependency(Integer i);",
          "}");

  private static final JavaFileObject A_MODULE =
      JavaFileObjects.forSourceLines(
          "test.AModule",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module",
          "interface AModule {",
          "  @Binds Object duplicate(String string);",
          "}");

  private static final JavaFileObject INCLUDING_MODULE =
      JavaFileObjects.forSourceLines(
          "test.IncludingModule",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module(includes = AModule.class)",
          "interface IncludingModule {",
          "  @Binds Object duplicate(Long l);",
          "}");

  private static final JavaFileObject MODULE_WITH_CHILD =
      JavaFileObjects.forSourceLines(
          "test.ModuleWithChild",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module(subcomponents = Child.class)",
          "interface ModuleWithChild {}");

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
          "    @BindsInstance Builder duplicate(Object object);",
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
          "  @Binds Object duplicate(Number number);",
          "}");

  // Make sure the error doesn't show other bindings or a dependency trace afterwards.
  private static final Pattern MODULE_WITH_ERRORS_MESSAGE =
      endsWithMessage(
          "[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
          "    @Binds Object test.ModuleWithErrors.duplicate1(String)",
          "    @Binds Object test.ModuleWithErrors.duplicate2(Long)");

  // Make sure the error doesn't show other bindings or a dependency trace afterwards.
  private static final Pattern INCLUDING_MODULE_MESSAGE =
      endsWithMessage(
          "[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
          "    @Binds Object test.AModule.duplicate(String)",
          "    @Binds Object test.IncludingModule.duplicate(Long)");

  // Make sure the error doesn't show other bindings or a dependency trace afterwards.
  private static final Pattern CHILD_MESSAGE =
      endsWithMessage(
          "[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
          "    @BindsInstance test.Child.Builder test.Child.Builder.duplicate(Object)",
          "    @Binds Object test.ChildModule.duplicate(Number)");

  @Test
  public void moduleWithErrors_validationTypeNone() {
    Compilation compilation = daggerCompiler().compile(MODULE_WITH_ERRORS);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void moduleWithErrors_validationTypeError() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.moduleBindingValidation=ERROR")
            .compile(MODULE_WITH_ERRORS);

    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContainingMatch(MODULE_WITH_ERRORS_MESSAGE)
        .inFile(MODULE_WITH_ERRORS)
        .onLineContaining("interface ModuleWithErrors");

    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  public void moduleWithErrors_validationTypeWarning() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.moduleBindingValidation=WARNING")
            .compile(MODULE_WITH_ERRORS);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(MODULE_WITH_ERRORS_MESSAGE)
        .inFile(MODULE_WITH_ERRORS)
        .onLineContaining("interface ModuleWithErrors");

    assertThat(compilation).hadWarningCount(1);
  }

  @Test
  public void moduleIncludingModuleWithCombinedErrors_validationTypeNone() {
    Compilation compilation = daggerCompiler().compile(A_MODULE, INCLUDING_MODULE);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void moduleIncludingModuleWithCombinedErrors_validationTypeError() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.moduleBindingValidation=ERROR")
            .compile(A_MODULE, INCLUDING_MODULE);

    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContainingMatch(INCLUDING_MODULE_MESSAGE)
        .inFile(INCLUDING_MODULE)
        .onLineContaining("interface IncludingModule");

    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  public void moduleIncludingModuleWithCombinedErrors_validationTypeWarning() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.moduleBindingValidation=WARNING")
            .compile(A_MODULE, INCLUDING_MODULE);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(INCLUDING_MODULE_MESSAGE)
        .inFile(INCLUDING_MODULE)
        .onLineContaining("interface IncludingModule");

    assertThat(compilation).hadWarningCount(1);
  }

  @Test
  public void moduleWithSubcomponentWithErrors_validationTypeNone() {
    Compilation compilation = daggerCompiler().compile(MODULE_WITH_CHILD, CHILD, CHILD_MODULE);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void moduleWithSubcomponentWithErrors_validationTypeError() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.moduleBindingValidation=ERROR")
            .compile(MODULE_WITH_CHILD, CHILD, CHILD_MODULE);

    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContainingMatch(CHILD_MESSAGE)
        .inFile(MODULE_WITH_CHILD)
        .onLineContaining("interface ModuleWithChild");

    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  public void moduleWithSubcomponentWithErrors_validationTypeWarning() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.moduleBindingValidation=WARNING")
            .compile(MODULE_WITH_CHILD, CHILD, CHILD_MODULE);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(CHILD_MESSAGE)
        .inFile(MODULE_WITH_CHILD)
        .onLineContaining("interface ModuleWithChild");

    assertThat(compilation).hadWarningCount(1);
  }
}
