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
public class AssistedInjectErrorsTest {
  @Parameters(name = "{0}")
  public static ImmutableCollection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public AssistedInjectErrorsTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void testAssistedInjectWithDuplicateTypesFails() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted String str1, @Assisted String str2) {}",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@AssistedInject constructor has duplicate @Assisted type: @Assisted "
                          + "java.lang.String")
                  .onSource(foo)
                  .onLine(8);
            });
  }

  @Test
  public void testAssistedInjectWithDuplicateTypesEmptyQualifierFails() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted(\"\") String str1, @Assisted String str2) {}",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@AssistedInject constructor has duplicate @Assisted type: @Assisted "
                          + "java.lang.String")
                  .onSource(foo)
                  .onLine(8);
            });
  }

  @Test
  public void testAssistedInjectWithDuplicateQualifiedTypesFails() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo<T> {",
            "  @AssistedInject",
            "  Foo(@Assisted(\"MyQualfier\") String s1, @Assisted(\"MyQualfier\") String s2) {}",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@AssistedInject constructor has duplicate @Assisted type: "
                          + "@Assisted(\"MyQualfier\") java.lang.String")
                  .onSource(foo)
                  .onLine(8);
            });
  }

  @Test
  public void testAssistedInjectWithDuplicateGenericTypesFails() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import java.util.List;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted List<String> list1, @Assisted List<String> list2) {}",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@AssistedInject constructor has duplicate @Assisted type: "
                          + "@Assisted java.util.List<java.lang.String>")
                  .onSource(foo)
                  .onLine(9);
            });
  }

  @Test
  public void testAssistedInjectWithDuplicateParameterizedTypesFails() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo<T> {",
            "  @AssistedInject",
            "  Foo(@Assisted T t1, @Assisted T t2) {}",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@AssistedInject constructor has duplicate @Assisted type: @Assisted T")
                  .onSource(foo)
                  .onLine(8);
            });
  }

  @Test
  public void testAssistedInjectWithUniqueParameterizedTypesPasses() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import java.util.List;",
            "",
            "class Foo<T1, T2> {",
            "  @AssistedInject",
            "  Foo(@Assisted T1 t1, @Assisted T2 t2) {}",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testAssistedInjectWithUniqueGenericTypesPasses() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import java.util.List;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted List<String> list1, @Assisted List<Integer> list2) {}",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testAssistedInjectWithUniqueQualifiedTypesPasses() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import java.util.List;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(",
            "      @Assisted(\"1\") Integer i1,",
            "      @Assisted(\"1\") String s1,",
            "      @Assisted(\"2\") String s2,",
            "      @Assisted String s3) {}",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testMultipleInjectedConstructors() {
    Source foo =
        CompilerTests.kotlinSource(
            "test.Foo.kt",
            "package test;",
            "",
            "import dagger.assisted.Assisted",
            "import dagger.assisted.AssistedInject",
            "import dagger.assisted.AssistedFactory",
            "import javax.inject.Inject",
            "",
            "class Foo @AssistedInject constructor(@Assisted i: Int) {",
            "",
            "  @Inject",
            "  constructor(s: String, @Assisted i: Int): this(i) {}",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "Type test.Foo may only contain one injected constructor."
                      + " Found: [@Inject test.Foo(String, int),"
                      + " @dagger.assisted.AssistedInject test.Foo(int)]");
              subject.hasErrorContaining(
                  "@Assisted parameters can only be used within an"
                      + " @AssistedInject-annotated constructor.");
            });
  }

  @Test
  public void testMultipleAssistedInjectedConstructors() {
    Source foo =
        CompilerTests.kotlinSource(
            "test.Foo.kt",
            "package test;",
            "",
            "import dagger.assisted.Assisted",
            "import dagger.assisted.AssistedInject",
            "import dagger.assisted.AssistedFactory",
            "",
            "class Foo @AssistedInject constructor(@Assisted i: Int) {",
            "",
            "  @AssistedInject",
            "  constructor(s: String, @Assisted i: Int): this(i) {}",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Type test.Foo may only contain one injected constructor."
                      + " Found: [@dagger.assisted.AssistedInject test.Foo(int),"
                      + " @dagger.assisted.AssistedInject test.Foo(String, int)]");
            });
  }
}
