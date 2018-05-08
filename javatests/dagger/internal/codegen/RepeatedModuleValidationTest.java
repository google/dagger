/*
 * Copyright (C) 2015 The Dagger Authors.
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

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RepeatedModuleValidationTest {
  private static final JavaFileObject MODULE_FILE =
      JavaFileObjects.forSourceLines(
          "test.TestModule",
          "package test;",
          "",
          "import dagger.Module;",
          "",
          "@Module",
          "final class TestModule {}");

  @Test
  public void moduleRepeatedInSubcomponentFactoryMethod() {
    JavaFileObject subcomponentFile =
        JavaFileObjects.forSourceLines(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface TestSubcomponent {",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestSubcomponent newTestSubcomponent(TestModule module);",
            "}");
    Compilation compilation =
        daggerCompiler().compile(MODULE_FILE, subcomponentFile, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("TestModule is present in test.TestComponent.")
        .inFile(componentFile)
        .onLine(7)
        .atColumn(51);
  }

  @Test
  public void moduleRepeatedInSubcomponentBuilderMethod() {
    JavaFileObject subcomponentFile =
        JavaFileObjects.forSourceLines(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface TestSubcomponent {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Builder testModule(TestModule testModule);",
            "    TestSubcomponent build();",
            "  }",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestSubcomponent.Builder newTestSubcomponentBuilder();",
            "}");
    Compilation compilation =
        daggerCompiler().compile(MODULE_FILE, subcomponentFile, componentFile);
    assertThat(compilation).succeeded();
    // TODO(gak): assert about the warning when we have that ability
  }

  @Test
  public void moduleRepeatedButNotPassed() {
    JavaFileObject subcomponentFile =
        JavaFileObjects.forSourceLines(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface TestSubcomponent {",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestSubcomponent newTestSubcomponent();",
            "}");
    Compilation compilation =
        daggerCompiler().compile(MODULE_FILE, subcomponentFile, componentFile);
    assertThat(compilation).succeeded();
  }
}
