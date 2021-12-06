/*
 * Copyright (C) 2021 The Dagger Authors.
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

@RunWith(JUnit4.class)
public class RawTypeInjectionTest {
  @Test
  public void rawEntryPointTest() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Foo foo();",  // Fail: requesting raw type
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo<T> {",
            "  @Inject Foo() {}",
            "}");

    Compilation compilation = daggerCompiler().compile(component, foo);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Foo cannot be provided without an @Provides-annotated method.")
        .inFile(component)
        .onLine(6);
  }

  @Test
  public void rawProvidesRequestTest() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  int integer();",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo<T> {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class TestModule {",
            "  @Provides",
            "  int provideFoo(Foo foo) {", // Fail: requesting raw type
            "    return 0;",
            "  }",
            "}");


    Compilation compilation = daggerCompiler().compile(component, foo, module);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Foo cannot be provided without an @Provides-annotated method.")
        .inFile(component)
        .onLine(6);
  }

  @Test
  public void rawInjectConstructorRequestTest() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Foo foo();",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo<T> {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(Foo foo) {}", // Fail: requesting raw type
            "}");


    Compilation compilation = daggerCompiler().compile(component, foo, bar);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Foo cannot be provided without an @Provides-annotated method.")
        .inFile(component)
        .onLine(6);
  }

  @Test
  public void rawProvidesReturnTest() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            // Test that we can request the raw type if it's provided by a module.
            "  Foo foo();",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo<T> {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class TestModule {",
            // Test that Foo<T> can still be requested and is independent of Foo (otherwise we'd
            // get a cyclic dependency error).
            "  @Provides",
            "  Foo provideFoo(Foo<Integer> fooInteger) {",
            "    return fooInteger;",
            "  }",
            "",
            "  @Provides",
            "  int provideInt() {",
            "    return 0;",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component, foo, module);
    assertThat(compilation).succeeded();
  }
}
