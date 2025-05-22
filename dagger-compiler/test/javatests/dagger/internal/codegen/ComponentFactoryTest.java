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

import static dagger.internal.codegen.base.ComponentCreatorAnnotation.COMPONENT_FACTORY;
import static dagger.internal.codegen.binding.ErrorMessages.creatorMessagesFor;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.binding.ErrorMessages;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {@link dagger.Component.Factory} */
@RunWith(Parameterized.class)
public class ComponentFactoryTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public ComponentFactoryTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final ErrorMessages.ComponentCreatorMessages MSGS =
      creatorMessagesFor(COMPONENT_FACTORY);

  @Test
  public void testUsesParameterNames() throws Exception {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides String string() { return null; }",
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
            "  String string();",
            "",
            "  @Component.Factory",
            "  interface Factory {",
            "    TestComponent newTestComponent(TestModule mod);",
            "  }",
            "}");

    CompilerTests.daggerCompiler(moduleFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void testSetterMethodFails() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  @Component.Factory",
            "  interface Factory {",
            "    SimpleComponent create();",
            "    Factory set(String s);",
            "  }",
            "}");
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          MSGS.twoFactoryMethods(),
                          "test.SimpleComponent test.SimpleComponent.Factory.create()"))
                  .onSource(componentFile)
                  .onLineContaining("Factory set(String s);");
            });
  }

  @Test
  public void testInheritedSetterMethodFails() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  interface Parent {",
            "    SimpleComponent create();",
            "    Parent set(String s);",
            "  }",
            "",
            "  @Component.Factory",
            "  interface Factory extends Parent {}",
            "}");
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          MSGS.twoFactoryMethods(),
                          "test.SimpleComponent test.SimpleComponent.Parent.create()"))
                  .onSource(componentFile)
                  .onLineContaining("interface Factory");
            });
  }
}
