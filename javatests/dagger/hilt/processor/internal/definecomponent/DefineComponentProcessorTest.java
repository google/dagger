/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal.definecomponent;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DefineComponentProcessorTest {

  @Test
  public void testDefineComponentOutput() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent(parent = SingletonComponent.class)",
            "interface FooComponent {",
            "  static int staticField = 1;",
            "  static int staticMethod() { return staticField; }",
            "}");

    JavaFileObject builder =
        JavaFileObjects.forSourceLines(
            "test.FooComponentBuilder",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent.Builder",
            "interface FooComponentBuilder {",
            "  static int staticField = 1;",
            "  static int staticMethod() { return staticField; }",
            "",
            "  FooComponent create();",
            "}");

    JavaFileObject componentOutput =
        JavaFileObjects.forSourceLines(
            "dagger.hilt.processor.internal.definecomponent.codegen._test_FooComponent",
            "package dagger.hilt.processor.internal.definecomponent.codegen;",
            "",
            "@DefineComponentClasses(component = \"test.FooComponent\")",
            "@Generated(\"" + DefineComponentProcessingStep.class.getName() + "\")",
            "public class _test_FooComponent {}");

    JavaFileObject builderOutput =
        JavaFileObjects.forSourceLines(
            "dagger.hilt.processor.internal.definecomponent.codegen._test_FooComponentBuilder",
            "package dagger.hilt.processor.internal.definecomponent.codegen;",
            "",
            "@DefineComponentClasses(builder = \"test.FooComponentBuilder\")",
            "@Generated(\"" + DefineComponentProcessingStep.class.getName() + "\")",
            "public class _test_FooComponentBuilder {}");

    Compilation compilation = compiler().compile(component, builder);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile(sourceName(componentOutput))
        .containsElementsIn(componentOutput);
    assertThat(compilation)
        .generatedSourceFile(sourceName(builderOutput))
        .containsElementsIn(builderOutput);
  }

  private static String sourceName(JavaFileObject fileObject) {
    return fileObject.getName().replace(".java", "").replace('.', '/');
  }

  @Test
  public void testDefineComponentClass_fails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent( parent = SingletonComponent.class )",
            "abstract class FooComponent {}");

    Compilation compilation = compiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent is only allowed on interfaces. Found: test.FooComponent");
  }

  @Test
  public void testDefineComponentWithTypeParameters_fails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent( parent = SingletonComponent.class )",
            "interface FooComponent<T> {}");

    Compilation compilation = compiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("@DefineComponent test.FooComponent<T>, cannot have type parameters.");
  }

  @Test
  public void testDefineComponentWithInvalidComponent_fails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "import dagger.hilt.android.qualifiers.ApplicationContext;",
            "",
            "@DefineComponent( parent = ApplicationContext.class )",
            "interface FooComponent {}");

    Compilation compilation = compiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent test.FooComponent, references a type not annotated with "
                + "@DefineComponent: dagger.hilt.android.qualifiers.ApplicationContext")
        .inFile(component);
  }

  @Test
  public void testDefineComponentExtendsInterface_fails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.DefineComponent;",
            "",
            "interface Foo {}",
            "",
            "@DefineComponent( parent = SingletonComponent.class )",
            "interface FooComponent extends Foo {}");

    Compilation compilation = compiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent test.FooComponent, cannot extend a super class or interface."
                + " Found: [test.Foo]");
  }

  @Test
  public void testDefineComponentNonStaticMethod_fails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent( parent = SingletonComponent.class )",
            "interface FooComponent {",
            "  int nonStaticMethod();",
            "}");

    Compilation compilation = compiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent test.FooComponent, cannot have non-static methods. "
                + "Found: [nonStaticMethod()]");
  }

  @Test
  public void testDefineComponentDependencyCycle_fails() {
    JavaFileObject component1 =
        JavaFileObjects.forSourceLines(
            "test.Component1",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent(parent = Component2.class)",
            "interface Component1 {}");

    JavaFileObject component2 =
        JavaFileObjects.forSourceLines(
            "test.Component2",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent(parent = Component1.class)",
            "interface Component2 {}");

    Compilation compilation = compiler().compile(component1, component2);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(2);
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent cycle: test.Component1 -> test.Component2 -> test.Component1");
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent cycle: test.Component2 -> test.Component1 -> test.Component2");
  }

  @Test
  public void testDefineComponentNoParent_fails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent",
            "interface FooComponent {}");
    Compilation compilation = compiler().compile(component);
    assertThat(compilation)
        .hadErrorContaining("@DefineComponent test.FooComponent is missing a parent declaration.");
  }

  @Test
  public void testDefineComponentBuilderClass_fails() {
    JavaFileObject builder =
        JavaFileObjects.forSourceLines(
            "test.FooComponentBuilder",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent.Builder",
            "abstract class FooComponentBuilder {}");

    Compilation compilation = compiler().compile(builder);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent.Builder is only allowed on interfaces. "
                + "Found: test.FooComponentBuilder");
  }

  @Test
  public void testDefineComponentBuilderWithTypeParameters_fails() {
    JavaFileObject builder =
        JavaFileObjects.forSourceLines(
            "test.FooComponentBuilder",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent.Builder",
            "interface FooComponentBuilder<T> {}");

    Compilation compilation = compiler().compile(builder);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent.Builder test.FooComponentBuilder<T>, cannot have type "
                + "parameters.");
  }

  @Test
  public void testDefineComponentBuilderExtendsInterface_fails() {
    JavaFileObject builder =
        JavaFileObjects.forSourceLines(
            "test.FooComponentBuilder",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "interface Foo {}",
            "",
            "@DefineComponent.Builder",
            "interface FooComponentBuilder extends Foo {}");

    Compilation compilation = compiler().compile(builder);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent.Builder test.FooComponentBuilder, cannot extend a super class "
                + "or interface. Found: [test.Foo]");
  }

  @Test
  public void testDefineComponentBuilderNoBuilderMethod_fails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent.Builder",
            "interface FooComponentBuilder {}");

    Compilation compilation = compiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent.Builder test.FooComponentBuilder, must have exactly 1 build "
                + "method that takes no parameters. Found: []");
  }

  @Test
  public void testDefineComponentBuilderPrimitiveReturnType_fails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent.Builder",
            "interface FooComponentBuilder {",
            "  int nonStaticMethod();",
            "}");

    Compilation compilation = compiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent.Builder method, test.FooComponentBuilder#nonStaticMethod(), "
                + "must return a @DefineComponent type. Found: int");
  }

  @Test
  public void testDefineComponentBuilderWrongReturnType_fails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "interface Foo {}",
            "",
            "@DefineComponent.Builder",
            "interface FooComponentBuilder {",
            "  Foo build();",
            "}");

    Compilation compilation = compiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@DefineComponent.Builder method, test.FooComponentBuilder#build(), must return "
                + "a @DefineComponent type. Found: test.Foo");
  }

  private static Compiler compiler() {
    return javac().withProcessors(new DefineComponentProcessor());
  }
}
