/*
 * Copyright (C) 2014 The Dagger Authors.
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

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests that errors are reported correctly when a {@code @Binds} method's delegate (the type of its
 * parameter) is missing.
 */
@RunWith(Parameterized.class)
public class BindsMissingDelegateValidationTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public BindsMissingDelegateValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void bindsMissingDelegate() {
    Source component =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "",
            "@Component(modules = C.TestModule.class)",
            "interface C {",
            "  Object object();",
            "",
            "  static class NotBound {}",
            "",
            "  @Module",
            "  abstract static class TestModule {",
            "    @Binds abstract Object bindObject(NotBound notBound);",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("test.C.NotBound cannot be provided")
                  .onSource(component)
                  .onLineContaining("interface C");
            });
  }

  @Test
  public void bindsMissingDelegate_duplicateBinding() {
    Source component =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Component(modules = C.TestModule.class)",
            "interface C {",
            "  Object object();",
            "",
            "  static class NotBound {}",
            "",
            "  @Module",
            "  abstract static class TestModule {",
            "    @Binds abstract Object bindObject(NotBound notBound);",
            "    @Provides static Object provideObject() { return new Object(); }",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // Some versions of javacs report only the first error for each source line so we
              // allow 1 of the assertions below to fail.
              // TODO(bcorso): Add CompilationResultSubject#hasErrorContainingMatch() to do this
              // more elegantly (see CL/469765892).
              java.util.List<Error> errors = new java.util.ArrayList<>();
              try {
                subject.hasErrorContaining("test.C.NotBound cannot be provided")
                    .onSource(component)
                    .onLineContaining("interface C");
              } catch (Error e) {
                errors.add(e);
              }
              try {
                subject.hasErrorContaining("Object is bound multiple times:")
                    .onSource(component)
                    .onLineContaining("interface C");
                subject.hasErrorContaining(
                    "@Binds Object test.C.TestModule.bindObject(test.C.NotBound)");
                subject.hasErrorContaining("@Provides Object test.C.TestModule.provideObject()");
              } catch (Error e) {
                errors.add(e);
              }
              com.google.common.truth.Truth.assertThat(errors.size()).isAtMost(1);
            });
  }

  @Test
  public void bindsMissingDelegate_setBinding() {
    Source component =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "",
            "@Component(modules = C.TestModule.class)",
            "interface C {",
            "  Set<Object> objects();",
            "",
            "  static class NotBound {}",
            "",
            "  @Module",
            "  abstract static class TestModule {",
            "    @Binds @IntoSet abstract Object bindObject(NotBound notBound);",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("test.C.NotBound cannot be provided")
                  .onSource(component)
                  .onLineContaining("interface C");
            });
  }

  @Test
  public void bindsMissingDelegate_mapBinding() {
    Source component =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Component(modules = C.TestModule.class)",
            "interface C {",
            "  Map<String, Object> objects();",
            "",
            "  static class NotBound {}",
            "",
            "  @Module",
            "  abstract static class TestModule {",
            "    @Binds @IntoMap @StringKey(\"key\")",
            "    abstract Object bindObject(NotBound notBound);",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("test.C.NotBound cannot be provided")
                  .onSource(component)
                  .onLineContaining("interface C");
            });
  }

  @Test
  public void bindsMissingDelegate_mapBinding_sameKey() {
    Source component =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Component(modules = C.TestModule.class)",
            "interface C {",
            "  Map<String, Object> objects();",
            "",
            "  static class NotBound {}",
            "",
            "  @Module",
            "  abstract static class TestModule {",
            "    @Binds @IntoMap @StringKey(\"key\")",
            "    abstract Object bindObject(NotBound notBound);",
            "",
            "    @Provides @IntoMap @StringKey(\"key\")",
            "    static Object provideObject() { return new Object(); }",
            "  }",
            "}");


    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // Some versions of javacs report only the first error for each source line so we
              // allow 1 of the assertions below to fail.
              // TODO(bcorso): Add CompilationResultSubject#hasErrorContainingMatch() to do this
              // more elegantly (see CL/469765892).
              java.util.List<Error> errors = new java.util.ArrayList<>();
              try {
                subject.hasErrorContaining("test.C.NotBound cannot be provided")
                    .onSource(component)
                    .onLineContaining("interface C");
              } catch (Error e) {
                errors.add(e);
              }
              try {
                subject.hasErrorContaining("same map key is bound more than once")
                    .onSource(component)
                    .onLineContaining("interface C");
              } catch (Error e) {
                errors.add(e);
              }
              com.google.common.truth.Truth.assertThat(errors.size()).isAtMost(1);
            });
  }
}
