/*
 * Copyright (C) 2025 The Dagger Authors.
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
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KeywordValidatorTest {
  @Test
  public void javaKeywordAsComponentCreatorMethodName_failsWithExpectedError() {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component",
            "interface TestComponent {",
            "  fun myInt(): Int",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    fun int(@BindsInstance param: Int): Builder",
            "    fun build(): TestComponent",
            "  }",
            "}");

    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case KSP:
                  subject
                      .hasErrorContaining("The name 'int' cannot be used because")
                      .onSource(componentSrc)
                      .onLineContaining("fun int(@BindsInstance param: Int): Builder");
                  break;
                case JAVAC:
                  subject
                      .hasErrorCount(1)
                      .hasErrorContaining("Integer cannot be provided without an @Inject");
                  break;
              }
            });
  }

  @Test
  public void javaKeywordAsComponentMemberMethodName_failsWithExpectedError() {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "import dagger.Provides",
            "",
            "@Component",
            "interface TestComponent {",
            "  fun int(): Integer", // Keyword parameter name "int"
            "}");
    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case KSP:
                  subject
                      .hasErrorContaining("The name 'int' cannot be used because")
                      .onSource(componentSrc)
                      .onLineContaining("fun int(): Integer");
                  break;
                case JAVAC:
                  subject
                      .hasErrorCount(1)
                      .hasErrorContaining("is not abstract and does not override abstract method");
                  break;
              }
            });
  }

  @Test
  public void javaKeywordAsParameterName_doesNotFail() {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component",
            "interface TestComponent {",
            "  fun myInt(int: Integer): Integer", // Keyword parameter name "int"
            "}");
    CompilerTests.daggerCompiler(componentSrc).compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void javaKeywordAsProvidesMethodName_failsWithExpectedError() throws Exception {
    Source moduleSrc =
        CompilerTests.kotlinSource(
            "test/MyModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "class MyModule {",
            "  @Provides fun int(): Int = 3", // Offending function name
            "}");
    CompilerTests.daggerCompiler(moduleSrc)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case KSP:
                  subject
                      .hasErrorContaining("The name 'int' cannot be used because")
                      .onSource(moduleSrc);
                  break;
                case JAVAC:
                  subject.hasErrorCount(0);
                  break;
              }
            });
  }

  @Test
  public void javaKeywordAsProvidesMethodParameterName_doesNotFail() {
    Source moduleSrc =
        CompilerTests.kotlinSource(
            "test/MyModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "class MyModule {",
            "  @Provides fun myInt(int: Int): Int = ",
            "    if (int == 3) 3 else 4", // Offending parameter name
            "}");
    CompilerTests.daggerCompiler(moduleSrc).compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void javaKeywordAsComponentName_failsWithExpectedError() {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "test/MyComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component",
            "interface default {}", // "default" is a Java keyword
            "");

    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case KSP:
                  subject
                      .hasErrorContaining("The name 'default' cannot be used because")
                      .onSource(componentSrc)
                      .onLineContaining("interface default {}");
                  break;
                case JAVAC:
                  // JAVAC does not generate stubs for this case, thus no error is reported.
                  subject.hasErrorCount(0);
                  break;
              }
            });
  }

  @Test
  public void javaKeywordAsDependencyKeyType_failsWithExpectedError() {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class MyClass @Inject constructor(def: `goto`) {}", // "goto" is a Java keyword
            "",
            "class `goto` {}",
            "");

    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case KSP:
                  subject
                      .hasErrorContaining("The name 'goto' cannot be used because")
                      .onSource(componentSrc)
                      .onLineContaining("class MyClass @Inject constructor(def: `goto`) {}");
                  break;
                case JAVAC:
                  // JAVAC does not generate stubs for this case, thus no error is reported.
                  subject.hasErrorCount(0);
                  break;
              }
            });
  }

  @Test
  public void javaKeywordAsInjectFieldName_failsWithExpectedError() {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class MyClass {",
            "  @Inject lateinit var `volatile`: String", // "volatile" is a Java keyword"
            "}",
            "");

    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case KSP:
                  subject
                      .hasErrorContaining("The name 'volatile' cannot be used because")
                      .onSource(componentSrc)
                      .onLineContaining("@Inject lateinit var `volatile`: String");
                  break;
                case JAVAC:
                  // JAVAC does not generate stubs for this case, thus no error is reported.
                  subject.hasErrorCount(0);
                  break;
              }
            });
  }

  @Test
  public void javaKeywordAsInjectFieldTypeName_failsWithExpectedError() {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class MyClass {",
            "  @Inject lateinit var myVar: volatile",
            "}",
            "class volatile {}", // "volatile" is a Java keyword"
            "");

    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case KSP:
                  subject
                      .hasErrorContaining("The name 'volatile' cannot be used because")
                      .onSource(componentSrc)
                      .onLineContaining("@Inject lateinit var myVar: volatile");
                  break;
                case JAVAC:
                  // JAVAC does not generate stubs for this case, thus no error is reported.
                  subject.hasErrorCount(0);
                  break;
              }
            });
  }

  @Test
  public void javaKeywordAsModuleName_failsWithExpectedError() {
    Source moduleSrc =
        CompilerTests.kotlinSource(
            "test/MyModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "",
            "@Module",
            "class `finally` {}", // "finally" is a Java keyword
            "");

    CompilerTests.daggerCompiler(moduleSrc)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case KSP:
                  subject
                      .hasErrorContaining("The name 'finally' cannot be used because")
                      .onSource(moduleSrc)
                      .onLineContaining("class `finally` {}");
                  break;
                case JAVAC:
                  // JAVAC does not generate stubs for this case, thus no error is reported.
                  subject.hasErrorCount(0);
                  break;
              }
            });
  }

  @Test
  public void javaKeywordAsInjectMethodName_failsWithExpectedError() {
    Source classSrc =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class MyClass {",
            "  @Inject fun `transient`() {}", // Keyword method name
            "}");

    CompilerTests.daggerCompiler(classSrc)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case KSP:
                  subject
                      .hasErrorContaining("The name 'transient' cannot be used because")
                      .onSource(classSrc)
                      .onLineContaining("fun `transient`() {}");
                  break;
                case JAVAC:
                  // JAVAC does not generate stubs for this case, thus no error is reported.
                  subject.hasErrorCount(0);
                  break;
              }
            });
  }

  @Test
  public void javaKeywordAsInjectMethodParameterName_doesNotFail() {
    Source classSrc =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class MyClass {",
            "  @Inject fun myTransient(transient: Boolean) {}", // Keyword transient parameter name
            "}");

    CompilerTests.daggerCompiler(classSrc).compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void javaKeywordAsAssistedFactoryParameterName_doesNotFail() {
    Source source =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test",
            "",
            "import dagger.BindsInstance",
            "import dagger.Component",
            "import dagger.assisted.AssistedFactory",
            "import dagger.assisted.Assisted",
            "import dagger.assisted.AssistedInject",
            "",
            "@Component",
            "interface MyComponent {",
            "  fun myAssistedFactory(): MyAssistedFactory",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance fun addInteger(int: Int): Builder",
            "    fun build(): MyComponent",
            "  }",
            "}",
            "",
            "data class MyAssistedClass @AssistedInject constructor(",
            "  val int: Int,",
            "  @Assisted val string: String,",
            "  @Assisted val long: Long",
            ")",
            "",
            "@AssistedFactory",
            "interface MyAssistedFactory {",
            "  fun create(long: Long, string: String): MyAssistedClass",
            "}");
    CompilerTests.daggerCompiler(source).compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void javaKeywordAsPackageName_failsWithExpectedError() {
    Source componentSrc =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test.default",
            "",
            "import dagger.Component",
            "",
            "@Component",
            "interface TestComponent {}" // "default" is a Java keyword
            );
    CompilerTests.daggerCompiler(componentSrc)
        .compile(
            subject -> {
              switch (CompilerTests.backend(subject)) {
                case KSP:
                  subject
                      .hasErrorContaining(
                          "The name 'default' cannot be used as a package name because")
                      .onSource(componentSrc);
                  break;
                case JAVAC:
                  // JAVAC does not generate stubs for this case, thus no error is reported.
                  subject.hasErrorCount(0);
                  break;
              }
            });
  }
}
