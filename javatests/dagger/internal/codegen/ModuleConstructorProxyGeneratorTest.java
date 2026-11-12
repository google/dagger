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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.room3.compiler.processing.util.CompilationResultSubject;
import androidx.room3.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {@code ModuleProxies.ModuleConstructorProxyGenerator}. */
@RunWith(Parameterized.class)
public class ModuleConstructorProxyGeneratorTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public ModuleConstructorProxyGeneratorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private void assertSourceMatchesGolden(CompilationResultSubject subject, String goldenName) {
    Source source = goldenFileRule.goldenSource(goldenName);
    subject.generatedSource(source);
  }

  private static final Source COMPONENT =
      CompilerTests.javaSource(
          "test.TestComponent",
          "package test;",
          "",
          "import dagger.Component;",
          "import modules.TestModule;",
          "",
          "@Component(modules = TestModule.class)",
          "interface TestComponent {",
          "  String string();",
          "}");

  @Test
  public void javaPackagePrivateConstructor_usedFromOtherPackage() {
    Source module =
        CompilerTests.javaSource(
            "modules.TestModule",
            "package modules;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "public final class TestModule {",
            "  TestModule() {}",
            "",
            "  @Provides String string() { return \"\"; }",
            "}");
    CompilerTests.daggerCompiler(module, COMPONENT)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> {
          subject.hasErrorCount(0);
          assertSourceMatchesGolden(subject, "modules/TestModule_Proxy");
          subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
        });
  }

  @Test
  public void javaProtectedConstructor_usedFromOtherPackage() {
    Source module =
        CompilerTests.javaSource(
            "modules.TestModule",
            "package modules;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "public class TestModule {",
            "  protected TestModule() {}",
            "",
            "  @Provides String string() { return \"\"; }",
            "}");
    CompilerTests.daggerCompiler(module, COMPONENT)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              assertSourceMatchesGolden(subject, "modules/TestModule_Proxy");
            });
  }

  @Test
  public void javaPackagePrivateModuleClass() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides String string() { return \"\"; }",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  String string();",
            "}");
    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              assertSourceMatchesGolden(subject, "test/TestModule_Proxy");
            });
  }

  @Test
  public void kotlinInternalConstructor() {
    Source module =
        CompilerTests.kotlinSource(
            "modules/TestModule.kt",
            "package modules",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "class TestModule internal constructor() {",
            "  @Provides fun string(): String = \"\"",
            "}");
    CompilerTests.daggerCompiler(module, COMPONENT)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> {
          subject.hasErrorCount(0);
          // In Java codegen an internal constructor is treated as public, so no proxy is
          // needed and the component calls the constructor directly.
          if (compilerMode.isKotlinCodegenEnabled()) {
            assertSourceMatchesGolden(subject, "modules/TestModule_Proxy");
          } else {
            assertFileNotGenerated(subject, "modules/TestModule_Proxy.java");
          }
          subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
        });
  }

  @Test
  public void kotlinInternalModuleClass() {
    Source module =
        CompilerTests.kotlinSource(
            "test/TestModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "internal class TestModule {",
            "  @Provides fun string(): String = \"\"",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [TestModule::class])",
            "internal interface TestComponent {",
            "  fun string(): String",
            "}");
    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> {
          subject.hasErrorCount(0);
          // In Java codegen an internal class is treated as public, so no proxy is needed.
          if (compilerMode.isKotlinCodegenEnabled()) {
            // The proxy stays public, so newInstance() returns Any and the component casts it.
            assertSourceMatchesGolden(subject, "test/TestModule_Proxy");
          } else {
            assertFileNotGenerated(subject, "test/TestModule_Proxy.java");
          }
          subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
        });
  }

  @Test
  public void kotlinProtectedConstructor() {
    Source module =
        CompilerTests.kotlinSource(
            "modules/TestModule.kt",
            "package modules",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "open class TestModule protected constructor() {",
            "  @Provides fun string(): String = \"\"",
            "}");
    CompilerTests.daggerCompiler(module, COMPONENT)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> {
          subject.hasErrorCount(0);
          assertSourceMatchesGolden(subject, "modules/TestModule_Proxy");
          subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
        });
  }

  @Test
  public void kotlinModuleNestedInInternalClass() {
    Source module =
        CompilerTests.kotlinSource(
            "test/Outer.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "internal class Outer {",
            "  @Module",
            "  class TestModule {",
            "    @Provides fun string(): String = \"\"",
            "  }",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test/TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [Outer.TestModule::class])",
            "internal interface TestComponent {",
            "  fun string(): String",
            "}");
    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> {
          subject.hasErrorCount(0);
          // In Java codegen an internal class is treated as public, so no proxy is needed.
          if (compilerMode.isKotlinCodegenEnabled()) {
            // The module is effectively internal, so newInstance() returns Any.
            assertSourceMatchesGolden(subject, "test/Outer_TestModule_Proxy");
          } else {
            assertFileNotGenerated(subject, "test/Outer_TestModule_Proxy.java");
          }
          subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
        });
  }

  private static void assertFileNotGenerated(CompilationResultSubject subject, String filePath) {
    AssertionError error =
        assertThrows(AssertionError.class, () -> subject.generatedSourceFileWithPath(filePath));
    assertThat(error).hasMessageThat().contains("Didn't generate file");
  }
}
