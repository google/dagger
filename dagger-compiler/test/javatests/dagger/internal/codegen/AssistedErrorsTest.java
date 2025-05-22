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
import com.google.common.collect.ImmutableCollection;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssistedErrorsTest {
  @Parameters(name = "{0}")
  public static ImmutableCollection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public AssistedErrorsTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void testAssistedNotWithAssistedInjectionConstructor() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "",
            "final class Foo {",
            "  Foo(",
            "      @Assisted String str",
            "  ) {}",
            "",
            "  void someMethod(",
            "      @Assisted int i",
            "  ) {}",
            "}");
    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                      "@Assisted parameters can only be used within an @AssistedInject-annotated "
                          + "constructor")
                  .onSource(foo)
                  .onLine(7);
            });
  }

  @Test
  public void testNestedFactoryNotStatic() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import javax.inject.Qualifier;",
            "",
            "class Foo {",
            "  @Qualifier @interface FooQualifier {}",
            "",
            "  @AssistedInject",
            "  Foo(",
            "      @FooQualifier @Assisted int i",
            "  ) {}",
            "}");
    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Qualifiers cannot be used with @Assisted parameters.")
                  .onSource(foo)
                  .onLine(12);
            });
  }
}
