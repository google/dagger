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
import static dagger.internal.codegen.ComponentCreatorAnnotation.SUBCOMPONENT_BUILDER;
import static dagger.internal.codegen.ErrorMessages.creatorMessagesFor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link dagger.Subcomponent.Builder} validation. */
@RunWith(JUnit4.class)
public class SubcomponentBuilderValidationTest {

  private static final ErrorMessages.ComponentCreatorMessages MSGS =
      creatorMessagesFor(SUBCOMPONENT_BUILDER);

  @Test
  public void testMoreThanOneArgFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    Builder set(String s, Integer i);",
        "    Builder set(Number n, Double d);",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.setterMethodsMustTakeOneArg())
        .inFile(childComponentFile)
        .onLine(10);
    assertThat(compilation)
        .hadErrorContaining(MSGS.setterMethodsMustTakeOneArg())
        .inFile(childComponentFile)
        .onLine(11);
  }

  @Test
  public void testInheritedMoreThanOneArgFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    Builder set1(String s, Integer i);",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");
    Compilation compilation = daggerCompiler().compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.inheritedSetterMethodsMustTakeOneArg(),
                "set1(java.lang.String,java.lang.Integer)"))
        .inFile(childComponentFile)
        .onLine(13);
  }

  @Test
  public void testSetterReturningNonVoidOrBuilderFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    String set(Integer i);",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.setterMethodsMustReturnVoidOrBuilder())
        .inFile(childComponentFile)
        .onLine(10);
  }

  @Test
  public void testInheritedSetterReturningNonVoidOrBuilderFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    String set(Integer i);",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");
    Compilation compilation = daggerCompiler().compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.inheritedSetterMethodsMustReturnVoidOrBuilder(), "set(java.lang.Integer)"))
        .inFile(childComponentFile)
        .onLine(13);
  }

  @Test
  public void testGenericsOnSetterMethodFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    <T> Builder set(T t);",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.methodsMayNotHaveTypeParameters())
        .inFile(childComponentFile)
        .onLine(10);
  }

  @Test
  public void testGenericsOnInheritedSetterMethodFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    <T> Builder set(T t);",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");
    Compilation compilation = daggerCompiler().compile(childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(MSGS.inheritedMethodsMayNotHaveTypeParameters(), "<T>set(T)"))
        .inFile(childComponentFile)
        .onLine(13);
  }
}
