/*
 * Copyright (C) 2026 The Dagger Authors.
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

import androidx.room3.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Regression test for b/539661501.
@RunWith(Parameterized.class)
public class PrivateTopLevelClassTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public PrivateTopLevelClassTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void testPrivateKotlinClass() {
    Source src =
        CompilerTests.kotlinSource(
            "test.Test.kt",
            "@file:Suppress(\"EXPOSED_PARAMETER_TYPE\", \"EXPOSED_FUNCTION_RETURN_TYPE\")",
            "package test",
            "",
            "import javax.inject.Inject",
            "import dagger.Component",
            "",
            "private class Foo @Inject constructor()",
            "",
            "@Component",
            "interface TestComponent {",
            "  fun getFoo(): Foo",
            "}");

    CompilerTests.daggerCompiler(src)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case JAVAC:
                  // TODO: b/539661501 - This should fail once this bug is fixed.
                  subject.hasErrorCount(0);
                  break;
                case KSP:
                  subject.hasErrorCount(2);
                  subject
                      .hasErrorContaining("Dagger does not support injection into private classes")
                      .onSource(src)
                      .onLineContaining("private class Foo");
                  subject
                      .hasErrorContaining(
                          "Foo cannot be provided without an @Inject constructor or an"
                              + " @Provides-annotated method")
                      .onSource(src)
                      .onLineContaining("interface TestComponent");
                  break;
              }
            });
  }
}
