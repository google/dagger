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
public class NullableBindingValidationTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public NullableBindingValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final Source NULLABLE =
        CompilerTests.javaSource(
            "test.Nullable", // force one-string-per-line format
            "package test;",
            "",
            "public @interface Nullable {}");

  @Test public void nullCheckForConstructorParameters() {
    Source a =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(String string) {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "final class TestModule {",
            "  @Nullable @Provides String provideString() { return null; }",
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
            "  A a();",
            "}");
    CompilerTests.daggerCompiler(NULLABLE, a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  nullableToNonNullable(
                      "String",
                      "@Nullable @Provides String TestModule.provideString()"));
            });

    // but if we disable the validation, then it compiles fine.
    CompilerTests.daggerCompiler(NULLABLE, a, module, component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.nullableValidation", "WARNING")
                .buildOrThrow())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test public void nullCheckForMembersInjectParam() {
    Source a =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A() {}",
            "  @Inject void register(String string) {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "final class TestModule {",
            "  @Nullable @Provides String provideString() { return null; }",
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
            "  A a();",
            "}");
    CompilerTests.daggerCompiler(NULLABLE, a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  nullableToNonNullable(
                      "String",
                      "@Nullable @Provides String TestModule.provideString()"));
            });

    // but if we disable the validation, then it compiles fine.
    CompilerTests.daggerCompiler(NULLABLE, a, module, component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.nullableValidation", "WARNING")
                .buildOrThrow())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test public void nullCheckForVariable() {
    Source a =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject String string;",
            "  @Inject A() {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "final class TestModule {",
            "  @Nullable @Provides String provideString() { return null; }",
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
            "  A a();",
            "}");
    CompilerTests.daggerCompiler(NULLABLE, a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  nullableToNonNullable(
                      "String",
                      "@Nullable @Provides String TestModule.provideString()"));
            });

    // but if we disable the validation, then it compiles fine.
    CompilerTests.daggerCompiler(NULLABLE, a, module, component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.nullableValidation", "WARNING")
                .buildOrThrow())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test public void nullCheckForComponentReturn() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "final class TestModule {",
            "  @Nullable @Provides String provideString() { return null; }",
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
    CompilerTests.daggerCompiler(NULLABLE, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  nullableToNonNullable(
                      "String",
                      "@Nullable @Provides String TestModule.provideString()"));
            });

    // but if we disable the validation, then it compiles fine.
    CompilerTests.daggerCompiler(NULLABLE, module, component)
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
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(Optional<String> optional) {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "abstract class TestModule {",
            "  @Nullable @Provides static String provideString() { return null; }",
            "  @BindsOptionalOf abstract String optionalString();",
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
            "  A a();",
            "}");
    CompilerTests.daggerCompiler(NULLABLE, a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  nullableToNonNullable(
                      "String",
                      "@Nullable @Provides String TestModule.provideString()"));
            });
  }

  @Test
  public void nullCheckForOptionalProvider() {
    Source a =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class A {",
            "  @Inject A(Optional<Provider<String>> optional) {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "abstract class TestModule {",
            "  @Nullable @Provides static String provideString() { return null; }",
            "  @BindsOptionalOf abstract String optionalString();",
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
            "  A a();",
            "}");
    CompilerTests.daggerCompiler(NULLABLE, a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void nullCheckForOptionalLazy() {
    Source a =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import dagger.Lazy;",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(Optional<Lazy<String>> optional) {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "abstract class TestModule {",
            "  @Nullable @Provides static String provideString() { return null; }",
            "  @BindsOptionalOf abstract String optionalString();",
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
            "  A a();",
            "}");
    CompilerTests.daggerCompiler(NULLABLE, a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void nullCheckForOptionalProviderOfLazy() {
    Source a =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import dagger.Lazy;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class A {",
            "  @Inject A(Optional<Provider<Lazy<String>>> optional) {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "abstract class TestModule {",
            "  @Nullable @Provides static String provideString() { return null; }",
            "  @BindsOptionalOf abstract String optionalString();",
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
            "  A a();",
            "}");
    CompilerTests.daggerCompiler(NULLABLE, a, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void moduleValidation() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides @Nullable static String nullableString() { return null; }",
            "  @Binds abstract Object object(String string);",
            "}");

    CompilerTests.daggerCompiler(NULLABLE, module)
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
                      "@Provides @Nullable String TestModule.nullableString()"));
            });
  }
}
