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

import androidx.room.compiler.processing.util.Source;
import dagger.hilt.android.testing.compile.HiltCompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DefineComponentProcessorTest {

  @Test
  public void testDefineComponentOutput() {
    Source component =
        HiltCompilerTests.javaSource(
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

    Source builder =
        HiltCompilerTests.javaSource(
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

    Source componentOutput =
        HiltCompilerTests.javaSource(
            "dagger.hilt.processor.internal.definecomponent.codegen._test_FooComponent",
            "package dagger.hilt.processor.internal.definecomponent.codegen;",
            "",
            "import dagger.hilt.internal.definecomponent.DefineComponentClasses;",
            "import javax.annotation.processing.Generated;",
            "",
            "/**",
            " * This class should only be referenced by generated code! This class aggregates "
                + "information across multiple compilations.",
            " */",
            "@DefineComponentClasses(",
            "    component = \"test.FooComponent\"",
            ")",
            "@Generated(\"dagger.hilt.processor.internal.definecomponent.DefineComponentProcessingStep\")",
            "public class _test_FooComponent {",
            "}");

    Source builderOutput =
        HiltCompilerTests.javaSource(
            "dagger.hilt.processor.internal.definecomponent.codegen._test_FooComponentBuilder",
            "package dagger.hilt.processor.internal.definecomponent.codegen;",
            "",
            "import dagger.hilt.internal.definecomponent.DefineComponentClasses;",
            "import javax.annotation.processing.Generated;",
            "",
            "/**",
            " * This class should only be referenced by generated code! This class aggregates "
                + "information across multiple compilations.",
            " */",
            "@DefineComponentClasses(",
            "    builder = \"test.FooComponentBuilder\"",
            ")",
            "@Generated(\"dagger.hilt.processor.internal.definecomponent.DefineComponentProcessingStep\")",
            "public class _test_FooComponentBuilder {",
            "}");

    HiltCompilerTests.hiltCompiler(component, builder)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(componentOutput);
              subject.generatedSource(builderOutput);
            });
  }

  @Test
  public void testDefineComponentClass_fails() {
    Source component =
        HiltCompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent( parent = SingletonComponent.class )",
            "abstract class FooComponent {}");

    HiltCompilerTests.hiltCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@DefineComponent is only allowed on interfaces. Found: test.FooComponent");
            });
  }

  @Test
  public void testDefineComponentWithTypeParameters_fails() {
    Source component =
        HiltCompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.components.SingletonComponent;",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent( parent = SingletonComponent.class )",
            "interface FooComponent<T> {}");

    HiltCompilerTests.hiltCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@DefineComponent test.FooComponent<T>, cannot have type parameters.");
            });
  }

  @Test
  public void testDefineComponentWithInvalidComponent_fails() {
    Source component =
        HiltCompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "import dagger.hilt.android.qualifiers.ApplicationContext;",
            "",
            "@DefineComponent( parent = ApplicationContext.class )",
            "interface FooComponent {}");

    HiltCompilerTests.hiltCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@DefineComponent test.FooComponent, references a type not annotated with "
                          + "@DefineComponent: dagger.hilt.android.qualifiers.ApplicationContext")
                  .onSource(component);
            });
  }

  @Test
  public void testDefineComponentExtendsInterface_fails() {
    Source component =
        HiltCompilerTests.javaSource(
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

    HiltCompilerTests.hiltCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@DefineComponent test.FooComponent, cannot extend a super class or interface."
                      + " Found: [test.Foo]");
            });
  }

  @Test
  public void testDefineComponentNonStaticMethod_fails() {
    Source component =
        HiltCompilerTests.javaSource(
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

    HiltCompilerTests.hiltCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@DefineComponent test.FooComponent, cannot have non-static methods. "
                      + "Found: [nonStaticMethod()]");
            });
  }

  @Test
  public void testDefineComponentDependencyCycle_fails() {
    Source component1 =
        HiltCompilerTests.javaSource(
            "test.Component1",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent(parent = Component2.class)",
            "interface Component1 {}");

    Source component2 =
        HiltCompilerTests.javaSource(
            "test.Component2",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent(parent = Component1.class)",
            "interface Component2 {}");

    HiltCompilerTests.hiltCompiler(component1, component2)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "@DefineComponent cycle: test.Component1 -> test.Component2 -> test.Component1");
              subject.hasErrorContaining(
                  "@DefineComponent cycle: test.Component2 -> test.Component1 -> test.Component2");
            });
  }

  @Test
  public void testDefineComponentNoParent_fails() {
    Source component =
        HiltCompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent",
            "interface FooComponent {}");

    HiltCompilerTests.hiltCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@DefineComponent test.FooComponent is missing a parent "
                  + "declaration.");
            });
  }

  @Test
  public void testDefineComponentBuilderClass_fails() {
    Source builder =
        HiltCompilerTests.javaSource(
            "test.FooComponentBuilder",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent.Builder",
            "abstract class FooComponentBuilder {}");

    HiltCompilerTests.hiltCompiler(builder)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@DefineComponent.Builder is only allowed on interfaces. "
                      + "Found: test.FooComponentBuilder");
            });
  }

  @Test
  public void testDefineComponentBuilderWithTypeParameters_fails() {
    Source builder =
        HiltCompilerTests.javaSource(
            "test.FooComponentBuilder",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent.Builder",
            "interface FooComponentBuilder<T> {}");

    HiltCompilerTests.hiltCompiler(builder)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@DefineComponent.Builder test.FooComponentBuilder<T>, cannot have type "
                      + "parameters.");
            });
  }

  @Test
  public void testDefineComponentBuilderExtendsInterface_fails() {
    Source builder =
        HiltCompilerTests.javaSource(
            "test.FooComponentBuilder",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "interface Foo {}",
            "",
            "@DefineComponent.Builder",
            "interface FooComponentBuilder extends Foo {}");

    HiltCompilerTests.hiltCompiler(builder)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@DefineComponent.Builder test.FooComponentBuilder, cannot extend a super class "
                      + "or interface. Found: [test.Foo]");
            });
  }

  @Test
  public void testDefineComponentBuilderNoBuilderMethod_fails() {
    Source component =
        HiltCompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent.Builder",
            "interface FooComponentBuilder {}");

    HiltCompilerTests.hiltCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@DefineComponent.Builder test.FooComponentBuilder, must have exactly 1 build "
                      + "method that takes no parameters. Found: []");
            });
  }

  @Test
  public void testDefineComponentBuilderPrimitiveReturnType_fails() {
    Source component =
        HiltCompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.hilt.DefineComponent;",
            "",
            "@DefineComponent.Builder",
            "interface FooComponentBuilder {",
            "  int nonStaticMethod();",
            "}");

    HiltCompilerTests.hiltCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@DefineComponent.Builder method, test.FooComponentBuilder#nonStaticMethod(), "
                      + "must return a @DefineComponent type. Found: int");
            });
  }

  @Test
  public void testDefineComponentBuilderWrongReturnType_fails() {
    Source component =
        HiltCompilerTests.javaSource(
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

    HiltCompilerTests.hiltCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@DefineComponent.Builder method, test.FooComponentBuilder#build(), must return "
                      + "a @DefineComponent type. Found: test.Foo");
            });
  }
}
