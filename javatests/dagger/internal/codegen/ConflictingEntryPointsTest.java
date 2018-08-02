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
import static dagger.internal.codegen.TestUtils.message;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ConflictingEntryPointsTest {

  @Test
  public void covariantType() {
    JavaFileObject base1 =
        JavaFileObjects.forSourceLines(
            "test.Base1", //
            "package test;",
            "",
            "interface Base1 {",
            "  Long foo();",
            "}");
    JavaFileObject base2 =
        JavaFileObjects.forSourceLines(
            "test.Base2", //
            "package test;",
            "",
            "interface Base2 {",
            "  Number foo();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent extends Base1, Base2 {",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder foo(Long foo);",
            "    @BindsInstance Builder foo(Number foo);",
            "    TestComponent build();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(base1, base2, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "conflicting entry point declarations:",
                "    Long test.Base1.foo()",
                "    Number test.Base2.foo()"))
        .inFile(component)
        .onLineContaining("interface TestComponent ");
  }

  @Test
  public void covariantTypeFromGenericSupertypes() {
    JavaFileObject base1 =
        JavaFileObjects.forSourceLines(
            "test.Base1", //
            "package test;",
            "",
            "interface Base1<T> {",
            "  T foo();",
            "}");
    JavaFileObject base2 =
        JavaFileObjects.forSourceLines(
            "test.Base2", //
            "package test;",
            "",
            "interface Base2<T> {",
            "  T foo();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent extends Base1<Long>, Base2<Number> {",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder foo(Long foo);",
            "    @BindsInstance Builder foo(Number foo);",
            "    TestComponent build();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(base1, base2, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "conflicting entry point declarations:",
                "    Long test.Base1.foo()",
                "    Number test.Base2.foo()"))
        .inFile(component)
        .onLineContaining("interface TestComponent ");
  }

  @Test
  public void differentQualifier() {
    JavaFileObject base1 =
        JavaFileObjects.forSourceLines(
            "test.Base1", //
            "package test;",
            "",
            "interface Base1 {",
            "  Object foo();",
            "}");
    JavaFileObject base2 =
        JavaFileObjects.forSourceLines(
            "test.Base2", //
            "package test;",
            "",
            "import javax.inject.Named;",
            "",
            "interface Base2 {",
            "  @Named(\"foo\") Object foo();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "import javax.inject.Named;",
            "",
            "@Component",
            "interface TestComponent extends Base1, Base2 {",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder foo(Object foo);",
            "    @BindsInstance Builder namedFoo(@Named(\"foo\") Object foo);",
            "    TestComponent build();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(base1, base2, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "conflicting entry point declarations:",
                "    Object test.Base1.foo()",
                "    @Named(\"foo\") Object test.Base2.foo()"))
        .inFile(component)
        .onLineContaining("interface TestComponent ");
  }

  @Test
  public void sameKey() {
    JavaFileObject base1 =
        JavaFileObjects.forSourceLines(
            "test.Base1", //
            "package test;",
            "",
            "interface Base1 {",
            "  Object foo();",
            "}");
    JavaFileObject base2 =
        JavaFileObjects.forSourceLines(
            "test.Base2", //
            "package test;",
            "",
            "interface Base2 {",
            "  Object foo();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent extends Base1, Base2 {",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder foo(Object foo);",
            "    TestComponent build();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(base1, base2, component);
    assertThat(compilation).succeeded();
  }

  @Test
  public void sameQualifiedKey() {
    JavaFileObject base1 =
        JavaFileObjects.forSourceLines(
            "test.Base1", //
            "package test;",
            "",
            "import javax.inject.Named;",
            "",
            "interface Base1 {",
            "  @Named(\"foo\") Object foo();",
            "}");
    JavaFileObject base2 =
        JavaFileObjects.forSourceLines(
            "test.Base2", //
            "package test;",
            "",
            "import javax.inject.Named;",
            "",
            "interface Base2 {",
            "  @Named(\"foo\") Object foo();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "import javax.inject.Named;",
            "",
            "@Component",
            "interface TestComponent extends Base1, Base2 {",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder foo(@Named(\"foo\") Object foo);",
            "    TestComponent build();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(base1, base2, component);
    assertThat(compilation).succeeded();
  }
}
