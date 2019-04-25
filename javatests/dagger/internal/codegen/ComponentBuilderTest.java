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
import static dagger.internal.codegen.ComponentCreatorAnnotation.COMPONENT_BUILDER;
import static dagger.internal.codegen.ErrorMessages.creatorMessagesFor;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
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

  private final CompilerMode compilerMode;

  public ComponentBuilderTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final ErrorMessages.ComponentCreatorMessages MSGS =
      creatorMessagesFor(COMPONENT_BUILDER);

  @Test
  public void testUsesBuildAndSetterNames() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
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

    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  private static final class Builder implements TestComponent.Builder {",
            "    private TestModule testModule;",
            "",
            "    @Override",
            "    public Builder setTestModule(TestModule testModule) {",
            "      this.testModule = Preconditions.checkNotNull(testModule);",
            "      return this;",
            "    }",
            "",
            "    @Override",
            "    public TestComponent create() {",
            "      if (testModule == null) {",
            "        this.testModule = new TestModule();",
            "      }",
            "      return new DaggerTestComponent(testModule);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(moduleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void testSetterMethodWithMoreThanOneArgFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.setterMethodsMustTakeOneArg())
        .inFile(componentFile)
        .onLineContaining("Builder set(String s, Integer i);");
    assertThat(compilation)
        .hadErrorContaining(MSGS.setterMethodsMustTakeOneArg())
        .inFile(componentFile)
        .onLineContaining("Builder set(Number n, Double d);");
  }

  @Test
  public void testInheritedSetterMethodWithMoreThanOneArgFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.inheritedSetterMethodsMustTakeOneArg(),
                "set1(java.lang.String,java.lang.Integer)"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @Test
  public void testSetterReturningNonVoidOrBuilderFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.setterMethodsMustReturnVoidOrBuilder())
        .inFile(componentFile)
        .onLineContaining("String set(Integer i);");
  }

  @Test
  public void testInheritedSetterReturningNonVoidOrBuilderFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.inheritedSetterMethodsMustReturnVoidOrBuilder(), "set(java.lang.Integer)"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @Test
  public void testGenericsOnSetterMethodFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.methodsMayNotHaveTypeParameters())
        .inFile(componentFile)
        .onLineContaining("<T> Builder set(T t);");
  }

  @Test
  public void testGenericsOnInheritedSetterMethodFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(MSGS.inheritedMethodsMayNotHaveTypeParameters(), "<T>set(T)"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @Test
  public void testBindsInstanceNotAllowedOnBothSetterAndParameter() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.bindsInstanceNotAllowedOnBothSetterMethodAndParameter())
        .inFile(componentFile)
        .onLineContaining("Builder s(");
  }

  @Test
  public void testBindsInstanceNotAllowedOnBothSetterAndParameter_inherited() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.inheritedBindsInstanceNotAllowedOnBothSetterMethodAndParameter(),
                "s(java.lang.String)"))
        .inFile(componentFile)
        .onLineContaining("Builder extends BuilderParent<Builder>");
  }
}
