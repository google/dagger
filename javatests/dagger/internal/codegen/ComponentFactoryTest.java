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
import static dagger.internal.codegen.ComponentCreatorAnnotation.COMPONENT_FACTORY;
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

/** Tests for {@link dagger.Component.Factory} */
@RunWith(Parameterized.class)
public class ComponentFactoryTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ComponentFactoryTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final ErrorMessages.ComponentCreatorMessages MSGS =
      creatorMessagesFor(COMPONENT_FACTORY);

  @Test
  public void testUsesParameterNames() {
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
            "  @Component.Factory",
            "  interface Factory {",
            "    TestComponent newTestComponent(TestModule mod);",
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
            "  private static final class Factory implements TestComponent.Factory {",
            "    @Override",
            "    public TestComponent newTestComponent(TestModule mod) {",
            "      Preconditions.checkNotNull(mod);",
            "      return new DaggerTestComponent(mod);",
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
  public void testSetterMethodFails() {
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
            "  @Component.Factory",
            "  interface Factory {",
            "    SimpleComponent create();",
            "    Factory set(String s);",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(String.format(MSGS.twoFactoryMethods(), "create()"))
        .inFile(componentFile)
        .onLineContaining("Factory set(String s);");
  }

  @Test
  public void testInheritedSetterMethodFails() {
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
            "    SimpleComponent create();",
            "    Parent set(String s);",
            "  }",
            "",
            "  @Component.Factory",
            "  interface Factory extends Parent {}",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(String.format(MSGS.twoFactoryMethods(), "create()"))
        .inFile(componentFile)
        .onLineContaining("interface Factory");
  }
}
