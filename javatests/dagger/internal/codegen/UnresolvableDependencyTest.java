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

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static java.util.stream.Collectors.joining;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class UnresolvableDependencyTest {

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
    assertThat(compilation).hadErrorCount(3);
    assertThat(compilation).hadErrorContaining(
        "cannot find symbol"
            + "\n  symbol:   class UnresolvableDependency"
            + "\n  location: class test.Bar");
    String trace = "\n  "
        + "\n  Dependency trace:"
        + "\n      => element (CLASS): test.Bar"
        + "\n      => element (CONSTRUCTOR): Bar(UnresolvableDependency)"
        + "\n      => type (EXECUTABLE constructor): (UnresolvableDependency)void"
        + "\n      => type (ERROR parameter type): UnresolvableDependency";
    assertThat(compilation).hadErrorContaining(
        "InjectProcessingStep was unable to process 'Bar(UnresolvableDependency)' because "
            + "'UnresolvableDependency' could not be resolved." + trace);
    assertThat(compilation).hadErrorContaining(
        "ComponentProcessingStep was unable to process 'test.FooComponent' because "
            + "'UnresolvableDependency' could not be resolved." + trace);

    // Only include a minimal portion of the stacktrace to minimize breaking tests due to refactors.
    String stacktraceErrorMessage =
        "dagger.internal.codegen.base"
            + ".DaggerSuperficialValidation$ValidationException$KnownErrorType";

    // Check that the stacktrace is not included in the error message by default.
    assertThat(
            compilation.errors().stream()
                .map(error -> error.getMessage(null))
                .collect(joining("\n")))
        .doesNotContain(stacktraceErrorMessage);

    // Recompile with the option enabled and check that the stacktrace is now included
    compilation =
        compilerWithOptions("-Adagger.includeStacktraceWithDeferredErrorMessages=ENABLED")
            .compile(fooComponent, foo, bar);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(3);
    assertThat(compilation).hadErrorContaining(stacktraceErrorMessage);
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
    assertThat(compilation).hadErrorCount(3);
    assertThat(compilation).hadErrorContaining(
        "cannot find symbol"
            + "\n  symbol: class UnresolvableAnnotation");
    String trace = "\n  "
        + "\n  Dependency trace:"
        + "\n      => element (CLASS): test.Bar"
        + "\n      => annotation: @UnresolvableAnnotation"
        + "\n      => type (ERROR annotation type): UnresolvableAnnotation";
    assertThat(compilation).hadErrorContaining(
        "InjectProcessingStep was unable to process 'Bar(java.lang.String)' because "
            + "'UnresolvableAnnotation' could not be resolved." + trace);
    assertThat(compilation).hadErrorContaining(
        "ComponentProcessingStep was unable to process 'test.FooComponent' because "
            + "'UnresolvableAnnotation' could not be resolved." + trace);
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
    assertThat(compilation).hadErrorCount(3);
    assertThat(compilation).hadErrorContaining(
        "cannot find symbol"
            + "\n  symbol:   class UnresolvableAnnotation"
            + "\n  location: class test.Bar");
    String trace = "\n  "
        + "\n  Dependency trace:"
        + "\n      => element (CLASS): test.Bar"
        + "\n      => element (CONSTRUCTOR): Bar(java.lang.String)"
        + "\n      => element (PARAMETER): dep"
        + "\n      => annotation: @UnresolvableAnnotation"
        + "\n      => type (ERROR annotation type): UnresolvableAnnotation";
    assertThat(compilation).hadErrorContaining(
        "InjectProcessingStep was unable to process 'Bar(java.lang.String)' because "
            + "'UnresolvableAnnotation' could not be resolved." + trace);
    assertThat(compilation).hadErrorContaining(
        "ComponentProcessingStep was unable to process 'test.FooComponent' because "
            + "'UnresolvableAnnotation' could not be resolved." + trace);
  }
}
