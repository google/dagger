/*
 * Copyright (C) 2020 The Dagger Authors.
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
public class ComponentDependenciesTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ComponentDependenciesTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void dependenciesWithTwoOfSameMethodOnDifferentInterfaces_fail() {
    Source interfaceOne =
        CompilerTests.javaSource(
            "test.One",
            "package test;",
            "",
            "interface One {",
            "  String getOne();",
            "}");
    Source interfaceTwo =
        CompilerTests.javaSource(
            "test.Two",
            "package test;",
            "",
            "interface Two {",
            "  String getTwo();",
            "}");
    Source mergedInterface =
        CompilerTests.javaSource(
            "test.Merged",
            "package test;",
            "",
            "interface Merged extends One, Two {}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = Merged.class)",
            "interface TestComponent {",
            "  String getString();",
            "}");
    CompilerTests.daggerCompiler(interfaceOne, interfaceTwo, mergedInterface, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("DuplicateBindings");
            });
  }

  @Test
  public void dependenciesWithTwoOfSameMethodOnDifferentInterfaces_producers_fail() {
    Source interfaceOne =
        CompilerTests.javaSource(
            "test.One",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "",
            "interface One {",
            "  ListenableFuture<String> getOne();",
            "}");
    Source interfaceTwo =
        CompilerTests.javaSource(
            "test.Two",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "",
            "interface Two {",
            "  ListenableFuture<String> getTwo();",
            "}");
    Source mergedInterface =
        CompilerTests.javaSource(
            "test.Merged",
            "package test;",
            "",
            "interface Merged extends One, Two {}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProductionComponent;",
            "",
            "@ProductionComponent(dependencies = Merged.class)",
            "interface TestComponent {",
            "  ListenableFuture<String> getString();",
            "}");
    CompilerTests.daggerCompiler(interfaceOne, interfaceTwo, mergedInterface, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("DuplicateBindings");
            });
  }

  @Test
  public void dependenciesWithTwoOfSameMethodButDifferentNullability_fail() {
    Source interfaceOne =
        CompilerTests.javaSource(
            "test.One",
            "package test;",
            "",
            "interface One {",
            "  String getString();",
            "}");
    Source interfaceTwo =
        CompilerTests.javaSource(
            "test.Two",
            "package test;",
            "import javax.annotation.Nullable;",
            "",
            "interface Two {",
            "  @Nullable String getString();",
            "}");
    Source mergedInterface =
        CompilerTests.javaSource(
            "test.Merged",
            "package test;",
            "",
            "interface Merged extends One, Two {}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = Merged.class)",
            "interface TestComponent {",
            "  String getString();",
            "}");
    CompilerTests.daggerCompiler(interfaceOne, interfaceTwo, mergedInterface, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("DuplicateBindings");
            });
  }
}
