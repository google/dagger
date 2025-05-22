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

import static dagger.internal.codegen.base.ComponentCreatorAnnotation.COMPONENT_BUILDER;
import static dagger.internal.codegen.binding.ErrorMessages.creatorMessagesFor;

import androidx.room.compiler.processing.util.Source;
import dagger.internal.codegen.binding.ErrorMessages;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {@link dagger.Component.Builder} */
@RunWith(Parameterized.class)
public class ComponentBuilderTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public ComponentBuilderTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final ErrorMessages.ComponentCreatorMessages MSGS =
      creatorMessagesFor(COMPONENT_BUILDER);

  @Test
  public void testUsesBuildAndSetterNames() throws Exception {
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
            "  @Component.Builder",
            "  interface Builder {",
            "    Builder setTestModule(TestModule testModule);",
            "    TestComponent create();",
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
  public void testSetterMethodWithMoreThanOneArgFails() {
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
            "  @Component.Builder",
            "  interface Builder {",
            "    SimpleComponent build();",
            "    Builder set(String s, Integer i);",
            "    Builder set(Number n, Double d);",
            "  }",
            "}");

    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject
                  .hasErrorContaining(MSGS.setterMethodsMustTakeOneArg())
                  .onSource(componentFile)
                  .onLineContaining("Builder set(String s, Integer i);");
              subject
                  .hasErrorContaining(MSGS.setterMethodsMustTakeOneArg())
                  .onSource(componentFile)
                  .onLineContaining("Builder set(Number n, Double d);");
            });
  }

  @Test
  public void testInheritedSetterMethodWithMoreThanOneArgFails() {
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
            "    SimpleComponent build();",
            "    Builder set1(String s, Integer i);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");

    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      String.format(
                          MSGS.inheritedSetterMethodsMustTakeOneArg(),
                          "test.SimpleComponent.Builder test.SimpleComponent.Parent.set1("
                              + "String, Integer)"))
                  .onSource(componentFile)
                  .onLineContaining("interface Builder");
            });
  }

  @Test
  public void testSetterReturningNonVoidOrBuilderFails() {
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
            "  @Component.Builder",
            "  interface Builder {",
            "    SimpleComponent build();",
            "    String set(Integer i);",
            "  }",
            "}");

    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(MSGS.setterMethodsMustReturnVoidOrBuilder())
                  .onSource(componentFile)
                  .onLineContaining("String set(Integer i);");
            });
  }

  @Test
  public void testInheritedSetterReturningNonVoidOrBuilderFails() {
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
            "    SimpleComponent build();",
            "    String set(Integer i);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");

    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      String.format(
                          MSGS.inheritedSetterMethodsMustReturnVoidOrBuilder(),
                          "String test.SimpleComponent.Parent.set(Integer)"))
                  .onSource(componentFile)
                  .onLineContaining("interface Builder");
            });
  }

  @Test
  public void testGenericsOnSetterMethodFails() {
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
            "  @Component.Builder",
            "  interface Builder {",
            "    SimpleComponent build();",
            "    <T> Builder set(T t);",
            "  }",
            "}");

    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(MSGS.methodsMayNotHaveTypeParameters())
                  .onSource(componentFile)
                  .onLineContaining("<T> Builder set(T t);");
            });
  }

  @Test
  public void testGenericsOnInheritedSetterMethodFails() {
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
            "    SimpleComponent build();",
            "    <T> Builder set(T t);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");

    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      String.format(
                          MSGS.inheritedMethodsMayNotHaveTypeParameters(),
                          "test.SimpleComponent.Builder test.SimpleComponent.Parent.set(T)"))
                  .onSource(componentFile)
                  .onLineContaining("interface Builder");
            });
  }

  @Test
  public void testBindsInstanceNotAllowedOnBothSetterAndParameter() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  abstract String s();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance",
            "    Builder s(@BindsInstance String s);",
            "",
            "    SimpleComponent build();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(MSGS.bindsInstanceNotAllowedOnBothSetterMethodAndParameter())
                  .onSource(componentFile)
                  .onLineContaining("Builder s(");
            });
  }

  @Test
  public void testBindsInstanceNotAllowedOnBothSetterAndParameter_inherited() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  abstract String s();",
            "",
            "  interface BuilderParent<B extends BuilderParent> {",
            "    @BindsInstance",
            "    B s(@BindsInstance String s);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends BuilderParent<Builder> {",
            "    SimpleComponent build();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      String.format(
                          MSGS.inheritedBindsInstanceNotAllowedOnBothSetterMethodAndParameter(),
                          "@BindsInstance B test.SimpleComponent.BuilderParent.s(String)"))
                  .onSource(componentFile)
                  .onLineContaining("Builder extends BuilderParent<Builder>");
            });
  }
}
