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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.compilerWithOptions;

import com.google.common.collect.ImmutableCollection;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.testing.golden.GoldenFileRule;
import javax.tools.JavaFileObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssistedFactoryTest {
  @Parameters(name = "{0}")
  public static ImmutableCollection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public AssistedFactoryTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void testAssistedFactory() throws Exception {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted String str, Bar bar) {}",
            "}");
    JavaFileObject fooFactory =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory {",
            "  Foo create(String factoryStr);",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, bar, fooFactory, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void testAssistedFactoryCycle() throws Exception {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted String str, Bar bar) {}",
            "}");
    JavaFileObject fooFactory =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory {",
            "  Foo create(String factoryStr);",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(FooFactory fooFactory) {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, bar, fooFactory, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void assistedParamConflictsWithComponentFieldName_successfulyDeduped() throws Exception {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import javax.inject.Provider;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted String testComponent, Provider<Bar> bar) {}",
            "}");
    JavaFileObject fooFactory =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory {",
            "  Foo create(String factoryStr);",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, bar, fooFactory, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void testFactoryGeneratorDuplicatedParamNames() throws Exception {
    JavaFileObject componentSrc =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @Component.Factory",
            "  interface Factory {",
            "    TestComponent create(@BindsInstance Bar arg);",
            "}",
            "  FooFactory getFooFactory();",
            "}");
    JavaFileObject factorySrc =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory {",
            "  Foo create(Integer arg);",
            "}");
    JavaFileObject barSrc =
        JavaFileObjects.forSourceLines("test.Bar", "package test;", "", "interface Bar {}");
    JavaFileObject injectSrc =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(Bar arg, @Assisted Integer argProvider) {}",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(componentSrc, factorySrc, barSrc, injectSrc);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.Foo_Factory")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.Foo_Factory"));
  }

  @Test
  public void testParameterizedAssistParam() throws Exception {
    JavaFileObject componentSrc =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory<String> getFooFactory();",
            "}");
    JavaFileObject factorySrc =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory<T> {",
            "  Foo<T> create(T arg);",
            "}");
    JavaFileObject injectSrc =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo<T> {",
            "  @AssistedInject",
            "  Foo(@Assisted T arg) {}",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentSrc, factorySrc, injectSrc);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }
}
