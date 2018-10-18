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

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that errors are reported for invalid members injection methods and {@link
 * dagger.MembersInjector} dependency requests.
 */
@RunWith(JUnit4.class)
public class MembersInjectionValidationTest {
  @Test
  public void membersInjectDependsOnUnboundedType() {
    JavaFileObject injectsUnboundedType =
        JavaFileObjects.forSourceLines(
            "test.InjectsUnboundedType",
            "package test;",
            "",
            "import dagger.MembersInjector;",
            "import java.util.ArrayList;",
            "import javax.inject.Inject;",
            "",
            "class InjectsUnboundedType {",
            "  @Inject MembersInjector<ArrayList<?>> listInjector;",
            "}");

    Compilation compilation = daggerCompiler().compile(injectsUnboundedType);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Cannot inject members into types with unbounded type arguments: "
                + "java.util.ArrayList<?>")
        .inFile(injectsUnboundedType)
        .onLineContaining("@Inject MembersInjector<ArrayList<?>> listInjector;");
  }

  @Test
  public void membersInjectPrimitive() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(int primitive);",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into int")
        .inFile(component)
        .onLineContaining("void inject(int primitive);");
  }

  @Test
  public void membersInjectArray() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(Object[] array);",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into java.lang.Object[]")
        .inFile(component)
        .onLineContaining("void inject(Object[] array);");
  }

  @Test
  public void membersInjectorOfArray() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "",
            "@Component",
            "interface TestComponent {",
            "  MembersInjector<Object[]> objectArrayInjector();",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into java.lang.Object[]")
        .inFile(component)
        .onLineContaining("objectArrayInjector();");
  }

  @Test
  public void membersInjectRawType() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(Set rawSet);",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Cannot inject members into raw type java.util.Set");
  }

  @Test
  public void qualifiedMembersInjector() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "import javax.inject.Named;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @Named(\"foo\") MembersInjector<Object> objectInjector();",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into qualified types")
        .inFile(component)
        .onLineContaining("objectInjector();");
  }

  @Test
  public void qualifiedMembersInjectionMethod() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "import javax.inject.Named;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @Named(\"foo\") void injectObject(Object object);",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into qualified types")
        .inFile(component)
        .onLineContaining("injectObject(Object object);");
  }

  @Test
  public void qualifiedMembersInjectionMethodParameter() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "import javax.inject.Named;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void injectObject(@Named(\"foo\") Object object);",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into qualified types")
        .inFile(component)
        .onLineContaining("injectObject(@Named(\"foo\") Object object);");
  }

  @Test
  public void staticFieldInjection() {
    JavaFileObject injected =
        JavaFileObjects.forSourceLines(
            "test.Injected",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Injected {",
            "  @Inject static Object object;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(Injected injected);",
            "}");
    Compilation compilation = daggerCompiler().compile(injected, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("static fields").inFile(injected).onLine(6);
  }
}
