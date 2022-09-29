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

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class UnresolvableDependencyTest {

  @Test
  public void referencesUnresolvableDependency() {
    Source fooComponent =
        CompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface FooComponent {",
            "  Foo foo();",
            "}");

    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject",
            "  Foo(Bar bar) {}",
            "}");

    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject",
            "  Bar(UnresolvableDependency dep) {}",
            "}");

    // Only include a minimal portion of the stacktrace to minimize breaking tests due to refactors.
    String stacktraceErrorMessage =
        "dagger.internal.codegen.base"
            + ".DaggerSuperficialValidation$ValidationException$KnownErrorType";
    CompilerTests.daggerCompiler(fooComponent, foo, bar)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case JAVAC:
                  subject.hasErrorCount(3);
                  subject.hasErrorContaining(
                      "cannot find symbol"
                          + "\n  symbol:   class UnresolvableDependency"
                          + "\n  location: class test.Bar");
                  break;
                case KSP:
                  subject.hasErrorCount(2);
                  break;
              }
              // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
              boolean isJavac = CompilerTests.backend(subject) == XProcessingEnv.Backend.JAVAC;
              String trace = "\n  "
                  + "\n  Dependency trace:"
                  + "\n      => element (CLASS): test.Bar"
                  + "\n      => element (CONSTRUCTOR): Bar(%1$s)"
                  + "\n      => type (EXECUTABLE constructor): (%1$s)void"
                  + "\n      => type (ERROR parameter type): %1$s";
              subject.hasErrorContaining(
                  String.format(
                      "InjectProcessingStep was unable to process 'Bar(%1$s)' because '%1$s' could "
                          + "not be resolved." + trace,
                      isJavac ? "UnresolvableDependency" : "error.NonExistentClass"));
              subject.hasErrorContaining(
                  String.format(
                      "ComponentProcessingStep was unable to process 'test.FooComponent' because "
                          + "'%1$s' could not be resolved." + trace,
                      isJavac ? "UnresolvableDependency" : "error.NonExistentClass"));

              // Check that the stacktrace is not included in the error message by default.
              assertThat(subject.getCompilationResult().rawOutput())
                  .doesNotContain(stacktraceErrorMessage);
            });


    CompilerTests.daggerCompiler(fooComponent, foo, bar)
        .withProcessingOptions(
            ImmutableMap.of("dagger.includeStacktraceWithDeferredErrorMessages", "ENABLED"))
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case JAVAC:
                  subject.hasErrorCount(3);
                  break;
                case KSP:
                  subject.hasErrorCount(2);
                  break;
              }
              subject.hasErrorContaining(stacktraceErrorMessage);
            });
  }

  @Test
  public void referencesUnresolvableAnnotationOnType() {
    Source fooComponent =
        CompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface FooComponent {",
            "  Foo foo();",
            "}");

    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject",
            "  Foo(Bar bar) {}",
            "}");

    Source bar =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(fooComponent, foo, bar)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case JAVAC:
                  subject.hasErrorCount(3);
                  subject.hasErrorContaining(
                      "cannot find symbol"
                          + "\n  symbol: class UnresolvableAnnotation");
                  break;
                case KSP:
                  subject.hasErrorCount(2);
                  break;
              }
              // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
              boolean isJavac = CompilerTests.backend(subject) == XProcessingEnv.Backend.JAVAC;
              String trace = "\n  "
                  + "\n  Dependency trace:"
                  + "\n      => element (CLASS): test.Bar"
                  + "\n      => annotation: @UnresolvableAnnotation"
                  + "\n      => type (ERROR annotation type): %1$s";
              subject.hasErrorContaining(
                  String.format(
                      "InjectProcessingStep was unable to process 'Bar(java.lang.String)' because "
                          + "'%1$s' could not be resolved." + trace,
                      isJavac ? "UnresolvableAnnotation" : "error.NonExistentClass"));
              subject.hasErrorContaining(
                  String.format(
                      "ComponentProcessingStep was unable to process 'test.FooComponent' because "
                          + "'%1$s' could not be resolved." + trace,
                      isJavac ? "UnresolvableAnnotation" : "error.NonExistentClass"));
            });
  }

  @Test
  public void referencesUnresolvableAnnotationOnTypeOnParameter() {
    Source fooComponent =
        CompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface FooComponent {",
            "  Foo foo();",
            "}");

    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject",
            "  Foo(Bar bar) {}",
            "}");

    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject",
            "  Bar(@UnresolvableAnnotation String dep) {}",
            "}");

    CompilerTests.daggerCompiler(fooComponent, foo, bar)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case JAVAC:
                  subject.hasErrorCount(3);
                  subject.hasErrorContaining(
                      "cannot find symbol"
                          + "\n  symbol:   class UnresolvableAnnotation"
                          + "\n  location: class test.Bar");
                  break;
                case KSP:
                  subject.hasErrorCount(2);
                  break;
              }
              // TODO(b/248552462): Javac and KSP should match once this bug is fixed.
              boolean isJavac = CompilerTests.backend(subject) == XProcessingEnv.Backend.JAVAC;
              String trace = "\n  "
                  + "\n  Dependency trace:"
                  + "\n      => element (CLASS): test.Bar"
                  + "\n      => element (CONSTRUCTOR): Bar(java.lang.String)"
                  + "\n      => element (PARAMETER): dep"
                  + "\n      => annotation: @UnresolvableAnnotation"
                  + "\n      => type (ERROR annotation type): %1$s";
              subject.hasErrorContaining(
                  String.format(
                      "InjectProcessingStep was unable to process 'Bar(java.lang.String)' because "
                          + "'%1$s' could not be resolved." + trace,
                      isJavac ? "UnresolvableAnnotation" : "error.NonExistentClass"));
              subject.hasErrorContaining(
                  String.format(
                      "ComponentProcessingStep was unable to process 'test.FooComponent' because "
                          + "'%1$s' could not be resolved." + trace,
                      isJavac ? "UnresolvableAnnotation" : "error.NonExistentClass"));
            });
  }
}
