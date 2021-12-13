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
public final class UnresolvableDependencyTest {
  private static final String DEFERRED_ERROR_MESSAGE =
      "dagger.internal.codegen.ComponentProcessor was unable to process 'test.FooComponent' "
          + "because not all of its dependencies could be resolved. Check for compilation errors "
          + "or a circular dependency with generated code.";

  @Test
  public void referencesUnresolvableDependency() {
    JavaFileObject fooComponent =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface FooComponent {",
            "  Foo foo();",
            "}");

    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject",
            "  Foo(Bar bar) {}",
            "}");

    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject",
            "  Bar(UnresolvableDependency dep) {}",
            "}");

    Compilation compilation = daggerCompiler().compile(fooComponent, foo, bar);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(DEFERRED_ERROR_MESSAGE);
  }

  @Test
  public void referencesUnresolvableAnnotationOnType() {
    JavaFileObject fooComponent =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface FooComponent {",
            "  Foo foo();",
            "}");

    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject",
            "  Foo(Bar bar) {}",
            "}");

    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "@UnresolvableAnnotation",
            "class Bar {",
            "  @Inject",
            "  Bar(String dep) {}",
            "}");

    Compilation compilation = daggerCompiler().compile(fooComponent, foo, bar);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(DEFERRED_ERROR_MESSAGE);
  }

  @Test
  public void referencesUnresolvableAnnotationOnTypeOnParameter() {
    JavaFileObject fooComponent =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface FooComponent {",
            "  Foo foo();",
            "}");

    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject",
            "  Foo(Bar bar) {}",
            "}");

    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject",
            "  Bar(@UnresolvableAnnotation String dep) {}",
            "}");

    Compilation compilation = daggerCompiler().compile(fooComponent, foo, bar);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(DEFERRED_ERROR_MESSAGE);
  }
}
