/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.internal.codegen.bindinggraphvalidation;

import static dagger.internal.codegen.bindinggraphvalidation.NullableBindingValidator.nullableToNonNullable;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.internal.codegen.CompilerMode;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NullableBindingValidationKotlinTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public NullableBindingValidationKotlinTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void nullCheckForConstructorParameters() {
    Source a =
        CompilerTests.kotlinSource(
            "test.A.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class A @Inject constructor(string: String)");
    Source module =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "class TestModule {",
            "  @Provides fun provideString(): String? = null",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [TestModule::class])",
            "interface TestComponent {",
            "  fun a(): A",
            "}");
    CompilerTests.daggerCompiler(a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  nullableToNonNullable(
                      "String",
                      "@Provides @Nullable String TestModule.provideString()"));
            });

    // but if we disable the validation, then it compiles fine.
    CompilerTests.daggerCompiler(a, module, component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.nullableValidation", "WARNING")
                .buildOrThrow())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void nullCheckForMembersInjectParam() {
    Source a =
        CompilerTests.kotlinSource(
            "test.A.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class A @Inject constructor() {",
            "  @Inject fun register(string: String) {}",
            "}");
    Source module =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "class TestModule {",
            "  @Provides fun provideString(): String? = null",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [TestModule::class])",
            "interface TestComponent {",
            "  fun a(): A",
            "}");
    CompilerTests.daggerCompiler(a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  nullableToNonNullable(
                      "String",
                      "@Provides @Nullable String TestModule.provideString()"));
            });

    // but if we disable the validation, then it compiles fine.
    CompilerTests.daggerCompiler(a, module, component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.nullableValidation", "WARNING")
                .buildOrThrow())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void nullCheckForVariable() {
    Source a =
        CompilerTests.kotlinSource(
            "test.A.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class A @Inject constructor() {",
            "  @Inject lateinit var string: String",
            "}");
    Source module =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "class TestModule {",
            "  @Provides fun provideString():String? = null",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [TestModule::class])",
            "interface TestComponent {",
            "  fun a(): A",
            "}");
    CompilerTests.daggerCompiler(a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  nullableToNonNullable(
                      "String",
                      "@Provides @Nullable String TestModule.provideString()"));
            });

    // but if we disable the validation, then it compiles fine.
    CompilerTests.daggerCompiler(a, module, component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.nullableValidation", "WARNING")
                .buildOrThrow())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test public void nullCheckForComponentReturn() {
    Source module =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "class TestModule {",
            "  @Provides fun provideString():String? = null",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [TestModule::class])",
            "interface TestComponent {",
            "  fun string(): String",
            "}");
    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  nullableToNonNullable(
                      "String",
                      "@Provides @Nullable String TestModule.provideString()"));
            });

    // but if we disable the validation, then it compiles fine.
    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.nullableValidation", "WARNING")
                .buildOrThrow())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void nullCheckForOptionalInstance() {
    Source a =
        CompilerTests.kotlinSource(
            "test.A.kt",
            "package test",
            "",
            "import com.google.common.base.Optional",
            "import javax.inject.Inject",
            "",
            "class A @Inject constructor(optional: Optional<String>)");
    Source module =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.BindsOptionalOf",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @BindsOptionalOf abstract fun optionalString(): String",
            "",
            "  companion object {",
            "    @Provides fun provideString():String? = null",
            "  }",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [TestModule::class])",
            "interface TestComponent {",
            "  fun a(): A",
            "}");
    CompilerTests.daggerCompiler(a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  nullableToNonNullable(
                      "String",
                      "@Provides @Nullable String TestModule.Companion.provideString()"));
            });
  }

  @Test
  public void nullCheckForOptionalProvider() {
    Source a =
        CompilerTests.kotlinSource(
            "test.A.kt",
            "package test",
            "",
            "import com.google.common.base.Optional",
            "import javax.inject.Inject",
            "import javax.inject.Provider",
            "",
            "class A @Inject constructor(optional: Optional<Provider<String>>)");
    Source module =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.BindsOptionalOf",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @BindsOptionalOf abstract fun optionalString(): String",
            "",
            "  companion object {",
            "    @Provides fun provideString():String? = null",
            "  }",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [TestModule::class])",
            "interface TestComponent {",
            "  fun a(): A",
            "}");
    CompilerTests.daggerCompiler(a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void nullCheckForOptionalLazy() {
    Source a =
        CompilerTests.kotlinSource(
            "test.A.kt",
            "package test",
            "",
            "import com.google.common.base.Optional",
            "import dagger.Lazy",
            "import javax.inject.Inject",
            "",
            "class A @Inject constructor(optional: Optional<Lazy<String>>)");
    Source module =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.BindsOptionalOf",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @BindsOptionalOf abstract fun optionalString(): String",
            "",
            "  companion object {",
            "    @Provides fun provideString():String? = null",
            "  }",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [TestModule::class])",
            "interface TestComponent {",
            "  fun a(): A",
            "}");
    CompilerTests.daggerCompiler(a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void nullCheckForOptionalProviderOfLazy() {
    Source a =
        CompilerTests.kotlinSource(
            "test.A.kt",
            "package test",
            "",
            "import com.google.common.base.Optional",
            "import dagger.Lazy",
            "import javax.inject.Inject",
            "import javax.inject.Provider",
            "",
            "class A @Inject constructor(optional: Optional<Provider<Lazy<String>>>)");
    Source module =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.BindsOptionalOf",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @BindsOptionalOf abstract fun optionalString(): String",
            "",
            "  companion object {",
            "    @Provides fun provideString():String? = null",
            "  }",
            "}");
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [TestModule::class])",
            "interface TestComponent {",
            "  fun a(): A",
            "}");
    CompilerTests.daggerCompiler(a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void moduleValidation() {
    Source module =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.Binds",
            "import dagger.Module",
            "import dagger.Provides",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Binds abstract fun bindObject(string: String): Object",
            "",
            "  companion object {",
            "    @Provides fun nullableString():String? = null",
            "  }",
            "}");
    CompilerTests.daggerCompiler(module)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.fullBindingGraphValidation", "ERROR")
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  nullableToNonNullable(
                      "String",
                      "@Provides @Nullable String TestModule.Companion.nullableString()"));
            });
  }
}
