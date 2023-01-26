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

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConflictingEntryPointsTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ConflictingEntryPointsTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void covariantType() {
    Source base1 =
        CompilerTests.javaSource(
            "test.Base1", //
            "package test;",
            "",
            "interface Base1 {",
            "  Long foo();",
            "}");
    Source base2 =
        CompilerTests.javaSource(
            "test.Base2", //
            "package test;",
            "",
            "interface Base2 {",
            "  Number foo();",
            "}");
    Source component =
        CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(base1, base2, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "can only implement the method once. Found:",
                          "    Long test.Base1.foo()",
                          "    Number test.Base2.foo()"))
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void covariantTypeFromGenericSupertypes() {
    Source base1 =
        CompilerTests.javaSource(
            "test.Base1", //
            "package test;",
            "",
            "interface Base1<T> {",
            "  T foo();",
            "}");
    Source base2 =
        CompilerTests.javaSource(
            "test.Base2", //
            "package test;",
            "",
            "interface Base2<T> {",
            "  T foo();",
            "}");
    Source component =
        CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(base1, base2, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "can only implement the method once. Found:",
                          "    Long test.Base1.foo()",
                          "    Number test.Base2.foo()"))
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void differentQualifier() {
    Source base1 =
        CompilerTests.javaSource(
            "test.Base1", //
            "package test;",
            "",
            "interface Base1 {",
            "  Object foo();",
            "}");
    Source base2 =
        CompilerTests.javaSource(
            "test.Base2", //
            "package test;",
            "",
            "import javax.inject.Named;",
            "",
            "interface Base2 {",
            "  @Named(\"foo\") Object foo();",
            "}");
    Source component =
        CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(base1, base2, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "can only implement the method once. Found:",
                          "    Object test.Base1.foo()",
                          "    @Named(\"foo\") Object test.Base2.foo()"))
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void sameKey() {
    Source base1 =
        CompilerTests.javaSource(
            "test.Base1", //
            "package test;",
            "",
            "interface Base1 {",
            "  Object foo();",
            "}");
    Source base2 =
        CompilerTests.javaSource(
            "test.Base2", //
            "package test;",
            "",
            "interface Base2 {",
            "  Object foo();",
            "}");
    Source component =
        CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(base1, base2, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void sameQualifiedKey() {
    Source base1 =
        CompilerTests.javaSource(
            "test.Base1", //
            "package test;",
            "",
            "import javax.inject.Named;",
            "",
            "interface Base1 {",
            "  @Named(\"foo\") Object foo();",
            "}");
    Source base2 =
        CompilerTests.javaSource(
            "test.Base2", //
            "package test;",
            "",
            "import javax.inject.Named;",
            "",
            "interface Base2 {",
            "  @Named(\"foo\") Object foo();",
            "}");
    Source component =
        CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(base1, base2, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }
}
