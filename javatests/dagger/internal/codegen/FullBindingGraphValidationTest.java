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
public final class FullBindingGraphValidationTest {
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
          "  @Binds Number missingDependency(Integer i);",
          "}");

  // Make sure the error doesn't show other bindings or a dependency trace afterwards.
  private static final Pattern MODULE_WITH_ERRORS_MESSAGE =
      endsWithMessage(
          "[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
          "    @Binds Object test.ModuleWithErrors.object1(String)",
          "    @Binds Object test.ModuleWithErrors.object2(Long)");

  @Test
  public void moduleWithErrors_validationTypeNone() {
    Compilation compilation = daggerCompiler().compile(MODULE_WITH_ERRORS);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void moduleWithErrors_validationTypeError() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
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
            .withOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(MODULE_WITH_ERRORS);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(MODULE_WITH_ERRORS_MESSAGE)
        .inFile(MODULE_WITH_ERRORS)
        .onLineContaining("interface ModuleWithErrors");

    assertThat(compilation).hadWarningCount(1);
  }

  private static final JavaFileObject INCLUDES_MODULE_WITH_ERRORS =
      JavaFileObjects.forSourceLines(
          "test.IncludesModuleWithErrors",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module(includes = ModuleWithErrors.class)",
          "interface IncludesModuleWithErrors {}");

  @Test
  public void includesModuleWithErrors_validationTypeNone() {
    Compilation compilation =
        daggerCompiler().compile(MODULE_WITH_ERRORS, INCLUDES_MODULE_WITH_ERRORS);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void includesModuleWithErrors_validationTypeError() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(MODULE_WITH_ERRORS, INCLUDES_MODULE_WITH_ERRORS);

    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContainingMatch(MODULE_WITH_ERRORS_MESSAGE)
        .inFile(MODULE_WITH_ERRORS)
        .onLineContaining("interface ModuleWithErrors");

    assertThat(compilation)
        .hadErrorContainingMatch("test.ModuleWithErrors has errors")
        .inFile(INCLUDES_MODULE_WITH_ERRORS)
        .onLineContaining("ModuleWithErrors.class");

    assertThat(compilation).hadErrorCount(2);
  }

  @Test
  public void includesModuleWithErrors_validationTypeWarning() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(MODULE_WITH_ERRORS, INCLUDES_MODULE_WITH_ERRORS);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(MODULE_WITH_ERRORS_MESSAGE)
        .inFile(MODULE_WITH_ERRORS)
        .onLineContaining("interface ModuleWithErrors");

    // TODO(b/130284666)
    assertThat(compilation)
        .hadWarningContainingMatch(MODULE_WITH_ERRORS_MESSAGE)
        .inFile(INCLUDES_MODULE_WITH_ERRORS)
        .onLineContaining("interface IncludesModuleWithErrors");

    assertThat(compilation).hadWarningCount(2);
  }

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
          "  @Binds Object object(String string);",
          "}");

  private static final JavaFileObject COMBINED_WITH_A_MODULE_HAS_ERRORS =
      JavaFileObjects.forSourceLines(
          "test.CombinedWithAModuleHasErrors",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module(includes = AModule.class)",
          "interface CombinedWithAModuleHasErrors {",
          "  @Binds Object object(Long l);",
          "}");

  // Make sure the error doesn't show other bindings or a dependency trace afterwards.
  private static final Pattern COMBINED_WITH_A_MODULE_HAS_ERRORS_MESSAGE =
      endsWithMessage(
          "[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
          "    @Binds Object test.AModule.object(String)",
          "    @Binds Object test.CombinedWithAModuleHasErrors.object(Long)");

  @Test
  public void moduleIncludingModuleWithCombinedErrors_validationTypeNone() {
    Compilation compilation = daggerCompiler().compile(A_MODULE, COMBINED_WITH_A_MODULE_HAS_ERRORS);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void moduleIncludingModuleWithCombinedErrors_validationTypeError() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(A_MODULE, COMBINED_WITH_A_MODULE_HAS_ERRORS);

    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContainingMatch(COMBINED_WITH_A_MODULE_HAS_ERRORS_MESSAGE)
        .inFile(COMBINED_WITH_A_MODULE_HAS_ERRORS)
        .onLineContaining("interface CombinedWithAModuleHasErrors");

    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  public void moduleIncludingModuleWithCombinedErrors_validationTypeWarning() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(A_MODULE, COMBINED_WITH_A_MODULE_HAS_ERRORS);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(COMBINED_WITH_A_MODULE_HAS_ERRORS_MESSAGE)
        .inFile(COMBINED_WITH_A_MODULE_HAS_ERRORS)
        .onLineContaining("interface CombinedWithAModuleHasErrors");

    assertThat(compilation).hadWarningCount(1);
  }

  private static final JavaFileObject SUBCOMPONENT_WITH_ERRORS =
      JavaFileObjects.forSourceLines(
          "test.SubcomponentWithErrors",
          "package test;",
          "",
          "import dagger.BindsInstance;",
          "import dagger.Subcomponent;",
          "",
          "@Subcomponent(modules = AModule.class)",
          "interface SubcomponentWithErrors {",
          "  @Subcomponent.Builder",
          "  interface Builder {",
          "    @BindsInstance Builder object(Object object);",
          "    SubcomponentWithErrors build();",
          "  }",
          "}");

  // Make sure the error doesn't show other bindings or a dependency trace afterwards.
  private static final Pattern SUBCOMPONENT_WITH_ERRORS_MESSAGE =
      endsWithMessage(
          "[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
          "    @Binds Object test.AModule.object(String)",
          "    @BindsInstance test.SubcomponentWithErrors.Builder"
              + " test.SubcomponentWithErrors.Builder.object(Object)");

  @Test
  public void subcomponentWithErrors_validationTypeNone() {
    Compilation compilation = daggerCompiler().compile(SUBCOMPONENT_WITH_ERRORS, A_MODULE);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void subcomponentWithErrors_validationTypeError() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(SUBCOMPONENT_WITH_ERRORS, A_MODULE);

    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContainingMatch(SUBCOMPONENT_WITH_ERRORS_MESSAGE)
        .inFile(SUBCOMPONENT_WITH_ERRORS)
        .onLineContaining("interface SubcomponentWithErrors");

    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  public void subcomponentWithErrors_validationTypeWarning() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(SUBCOMPONENT_WITH_ERRORS, A_MODULE);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(SUBCOMPONENT_WITH_ERRORS_MESSAGE)
        .inFile(SUBCOMPONENT_WITH_ERRORS)
        .onLineContaining("interface SubcomponentWithErrors");

    assertThat(compilation).hadWarningCount(1);
  }

  private static final JavaFileObject MODULE_WITH_SUBCOMPONENT_WITH_ERRORS =
      JavaFileObjects.forSourceLines(
          "test.ModuleWithSubcomponentWithErrors",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module(subcomponents = SubcomponentWithErrors.class)",
          "interface ModuleWithSubcomponentWithErrors {}");

  @Test
  public void moduleWithSubcomponentWithErrors_validationTypeNone() {
    Compilation compilation =
        daggerCompiler()
            .compile(MODULE_WITH_SUBCOMPONENT_WITH_ERRORS, SUBCOMPONENT_WITH_ERRORS, A_MODULE);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void moduleWithSubcomponentWithErrors_validationTypeError() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(MODULE_WITH_SUBCOMPONENT_WITH_ERRORS, SUBCOMPONENT_WITH_ERRORS, A_MODULE);

    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContainingMatch(SUBCOMPONENT_WITH_ERRORS_MESSAGE)
        .inFile(MODULE_WITH_SUBCOMPONENT_WITH_ERRORS)
        .onLineContaining("interface ModuleWithSubcomponentWithErrors");

    // TODO(b/130283677)
    assertThat(compilation)
        .hadErrorContainingMatch(SUBCOMPONENT_WITH_ERRORS_MESSAGE)
        .inFile(SUBCOMPONENT_WITH_ERRORS)
        .onLineContaining("interface SubcomponentWithErrors");

    assertThat(compilation).hadErrorCount(2);
  }

  @Test
  public void moduleWithSubcomponentWithErrors_validationTypeWarning() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(MODULE_WITH_SUBCOMPONENT_WITH_ERRORS, SUBCOMPONENT_WITH_ERRORS, A_MODULE);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(SUBCOMPONENT_WITH_ERRORS_MESSAGE)
        .inFile(MODULE_WITH_SUBCOMPONENT_WITH_ERRORS)
        .onLineContaining("interface ModuleWithSubcomponentWithErrors");

    // TODO(b/130283677)
    assertThat(compilation)
        .hadWarningContainingMatch(SUBCOMPONENT_WITH_ERRORS_MESSAGE)
        .inFile(SUBCOMPONENT_WITH_ERRORS)
        .onLineContaining("interface SubcomponentWithErrors");

    assertThat(compilation).hadWarningCount(2);
  }

  private static final JavaFileObject A_SUBCOMPONENT =
      JavaFileObjects.forSourceLines(
          "test.ASubcomponent",
          "package test;",
          "",
          "import dagger.BindsInstance;",
          "import dagger.Subcomponent;",
          "",
          "@Subcomponent(modules = AModule.class)",
          "interface ASubcomponent {",
          "  @Subcomponent.Builder",
          "  interface Builder {",
          "    ASubcomponent build();",
          "  }",
          "}");

  private static final JavaFileObject COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS =
      JavaFileObjects.forSourceLines(
          "test.CombinedWithASubcomponentHasErrors",
          "package test;",
          "",
          "import dagger.Binds;",
          "import dagger.Module;",
          "",
          "@Module(subcomponents = ASubcomponent.class)",
          "interface CombinedWithASubcomponentHasErrors {",
          "  @Binds Object object(Number number);",
          "}");

  // Make sure the error doesn't show other bindings or a dependency trace afterwards.
  private static final Pattern COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS_MESSAGE =
      endsWithMessage(
          "[Dagger/DuplicateBindings] java.lang.Object is bound multiple times:",
          "    @Binds Object test.AModule.object(String)",
          "    @Binds Object test.CombinedWithASubcomponentHasErrors.object(Number)");

  @Test
  public void moduleWithSubcomponentWithCombinedErrors_validationTypeNone() {
    Compilation compilation =
        daggerCompiler().compile(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS, A_SUBCOMPONENT, A_MODULE);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void moduleWithSubcomponentWithCombinedErrors_validationTypeError() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS, A_SUBCOMPONENT, A_MODULE);

    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContainingMatch(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS_MESSAGE)
        .inFile(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS)
        .onLineContaining("interface CombinedWithASubcomponentHasErrors");

    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  public void moduleWithSubcomponentWithCombinedErrors_validationTypeWarning() {
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=WARNING")
            .compile(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS, A_SUBCOMPONENT, A_MODULE);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContainingMatch(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS_MESSAGE)
        .inFile(COMBINED_WITH_A_SUBCOMPONENT_HAS_ERRORS)
        .onLineContaining("interface CombinedWithASubcomponentHasErrors");

    assertThat(compilation).hadWarningCount(1);
  }

  @Test
  public void bothAliasesDifferentValues() {
    Compilation compilation =
        daggerCompiler()
            .withOptions(
                "-Adagger.moduleBindingValidation=NONE",
                "-Adagger.fullBindingGraphValidation=ERROR")
            .compile(MODULE_WITH_ERRORS);

    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContaining(
            "Only one of the equivalent options "
                + "(-Adagger.fullBindingGraphValidation, -Adagger.moduleBindingValidation)"
                + " should be used; prefer -Adagger.fullBindingGraphValidation");

    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  public void bothAliasesSameValue() {
    Compilation compilation =
        daggerCompiler()
            .withOptions(
                "-Adagger.moduleBindingValidation=NONE", "-Adagger.fullBindingGraphValidation=NONE")
            .compile(MODULE_WITH_ERRORS);

    assertThat(compilation).succeeded();

    assertThat(compilation)
        .hadWarningContaining(
            "Only one of the equivalent options "
                + "(-Adagger.fullBindingGraphValidation, -Adagger.moduleBindingValidation)"
                + " should be used; prefer -Adagger.fullBindingGraphValidation");
  }
}
