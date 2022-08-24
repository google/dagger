/*
 * Copyright (C) 2022 The Dagger Authors.
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

// Tests an invalid inject constructor that avoids validation in its own library by using
// a dependency on jsr330 rather than Dagger gets validated when used in a component.
@RunWith(Parameterized.class)
public class InvalidInjectConstructorTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public InvalidInjectConstructorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void usedInvalidConstructorFails() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.InvalidInjectConstructor;",
            "",
            "@Component",
            "interface TestComponent {",
            "  InvalidInjectConstructor invalidInjectConstructor();",
            "}");
    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              // subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "Type dagger.internal.codegen.InvalidInjectConstructor may only contain one "
                      + "injected constructor. Found: ["
                      + "@Inject dagger.internal.codegen.InvalidInjectConstructor(), "
                      + "@Inject dagger.internal.codegen.InvalidInjectConstructor(String)"
                      + "]");
              // TODO(b/215620949): Avoid reporting missing bindings on a type that has errors.
              subject.hasErrorContaining(
                  "InvalidInjectConstructor cannot be provided without an @Inject constructor or "
                      + "an @Provides-annotated method.");
            });
  }

  @Test
  public void unusedInvalidConstructorFails() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.InvalidInjectConstructor;",
            "",
            "@Component",
            "interface TestComponent {",
            // Here we're only using the members injection, but we're testing that we still validate
            // the constructors
            "  void inject(InvalidInjectConstructor instance);",
            "}");
    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              // subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "Type dagger.internal.codegen.InvalidInjectConstructor may only contain one "
                      + "injected constructor. Found: ["
                      + "@Inject dagger.internal.codegen.InvalidInjectConstructor(), "
                      + "@Inject dagger.internal.codegen.InvalidInjectConstructor(String)"
                      + "]");
              // TODO(b/215620949): Avoid reporting missing bindings on a type that has errors.
              subject.hasErrorContaining(
                  "InvalidInjectConstructor cannot be provided without an @Inject constructor or "
                      + "an @Provides-annotated method.");
            });
  }
}
