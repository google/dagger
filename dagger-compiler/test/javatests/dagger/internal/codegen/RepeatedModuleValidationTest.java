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

import androidx.room.compiler.processing.util.Source;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RepeatedModuleValidationTest {
  private static final Source MODULE_FILE =
        CompilerTests.javaSource(
          "test.TestModule",
          "package test;",
          "",
          "import dagger.Module;",
          "",
          "@Module",
          "final class TestModule {}");

  @Test
  public void moduleRepeatedInSubcomponentFactoryMethod() {
    Source subcomponentFile =
        CompilerTests.javaSource(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface TestSubcomponent {",
            "}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestSubcomponent newTestSubcomponent(TestModule module);",
            "}");
    CompilerTests.daggerCompiler(MODULE_FILE, subcomponentFile, componentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("TestModule is present in test.TestComponent.")
                  .onSource(componentFile)
                  .onLine(7);
            });
  }

  @Test
  public void moduleRepeatedInSubcomponentBuilderMethod() {
    Source subcomponentFile =
        CompilerTests.javaSource(
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
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestSubcomponent.Builder newTestSubcomponentBuilder();",
            "}");
    CompilerTests.daggerCompiler(MODULE_FILE, subcomponentFile, componentFile)
        .compile(subject -> subject.hasErrorCount(0));
    // TODO(gak): assert about the warning when we have that ability
  }

  @Test
  public void moduleRepeatedButNotPassed() {
    Source subcomponentFile =
        CompilerTests.javaSource(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface TestSubcomponent {",
            "}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestSubcomponent newTestSubcomponent();",
            "}");
    CompilerTests.daggerCompiler(MODULE_FILE, subcomponentFile, componentFile)
        .compile(subject -> subject.hasErrorCount(0));
  }
}
