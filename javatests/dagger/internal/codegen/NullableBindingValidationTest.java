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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.NullableBindingValidator.nullableToNonNullable;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NullableBindingValidationTest {
  private static final JavaFileObject NULLABLE =
      JavaFileObjects.forSourceLines(
          "test.Nullable", // force one-string-per-line format
          "package test;",
          "",
          "public @interface Nullable {}");

  @Test public void nullCheckForConstructorParameters() {
    JavaFileObject a = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A(String string) {}",
        "}");
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "final class TestModule {",
        "  @Nullable @Provides String provideString() { return null; }",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  A a();",
        "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    Compilation compilation2 =
        javac()
            .withOptions("-Adagger.nullableValidation=WARNING")
            .withProcessors(new ComponentProcessor())
            .compile(NULLABLE, a, module, component);
    assertThat(compilation2).succeeded();
  }

  @Test public void nullCheckForMembersInjectParam() {
    JavaFileObject a = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A() {}",
        "  @Inject void register(String string) {}",
        "}");
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "final class TestModule {",
        "  @Nullable @Provides String provideString() { return null; }",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  A a();",
        "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    Compilation compilation2 =
        javac()
            .withOptions("-Adagger.nullableValidation=WARNING")
            .withProcessors(new ComponentProcessor())
            .compile(NULLABLE, a, module, component);
    assertThat(compilation2).succeeded();
  }

  @Test public void nullCheckForVariable() {
    JavaFileObject a = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject String string;",
        "  @Inject A() {}",
        "}");
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "final class TestModule {",
        "  @Nullable @Provides String provideString() { return null; }",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  A a();",
        "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    Compilation compilation2 =
        javac()
            .withOptions("-Adagger.nullableValidation=WARNING")
            .withProcessors(new ComponentProcessor())
            .compile(NULLABLE, a, module, component);
    assertThat(compilation2).succeeded();
  }

  @Test public void nullCheckForComponentReturn() {
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "final class TestModule {",
        "  @Nullable @Provides String provideString() { return null; }",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  String string();",
        "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    Compilation compilation2 =
        javac()
            .withOptions("-Adagger.nullableValidation=WARNING")
            .withProcessors(new ComponentProcessor())
            .compile(NULLABLE, module, component);
    assertThat(compilation2).succeeded();
  }

  @Test
  public void nullCheckForOptionalInstance() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(Optional<String> optional) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));
  }

  @Test
  public void nullCheckForOptionalProvider() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).succeeded();
  }

  @Test
  public void nullCheckForOptionalLazy() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).succeeded();
  }

  @Test
  public void nullCheckForOptionalProviderOfLazy() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).succeeded();
  }

  @Test
  public void moduleValidation() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(module, NULLABLE);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@Provides @test.Nullable String test.TestModule.nullableString()"));
  }
}
