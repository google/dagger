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
import dagger.testing.golden.GoldenFileRule;
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
            "  Foo(@Assisted String str, Bar bar) {}",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory {",
            "  Foo create(String factoryStr);",
            "}");

    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, bar, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void testAssistedFactoryCycle() throws Exception {
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
            "  Foo(@Assisted String str, Bar bar) {}",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory {",
            "  Foo create(String factoryStr);",
            "}");

    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(FooFactory fooFactory) {}",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, bar, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void assistedParamConflictsWithComponentFieldName_successfulyDeduped() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import javax.inject.Provider;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted String testComponentImpl, Provider<Bar> bar) {}",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory {",
            "  Foo create(String factoryStr);",
            "}");

    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, bar, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void testFactoryGeneratorDuplicatedParamNames() throws Exception {
    Source component =
        CompilerTests.javaSource(
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

    Source fooFactory =
        CompilerTests.javaSource(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory {",
            "  Foo create(Integer arg);",
            "}");

    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "interface Bar {}");

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
            "  Foo(Bar arg, @Assisted Integer argProvider) {}",
            "}");

    CompilerTests.daggerCompiler(component, fooFactory, foo, bar)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/Foo_Factory"));
            });
  }

  @Test
  public void testParameterizedAssistParam() throws Exception {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory<String> getFooFactory();",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory<T> {",
            "  Foo<T> create(T arg);",
            "}");

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
            "  Foo(@Assisted T arg) {}",
            "}");

    CompilerTests.daggerCompiler(component, fooFactory, foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  // This is a regression test for b/305748522
  // The important thing for this test is that we have two assisted factories for the same assisted
  // injection class and that they are requested in different components.
  @Test
  public void testMultipleAssistedFactoryInDifferentComponents() throws Exception {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface MyComponent {",
            "  MyComponentAssistedFactory myComponentAssistedFactory();",
            "  MySubcomponent mySubcomponent();",
            "}");
    Source subcomponent =
        CompilerTests.javaSource(
            "test.MySubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface MySubcomponent {",
            "  MySubcomponentAssistedFactory mySubcomponentAssistedFactory();",
            "}");
    Source assistedClass =
        CompilerTests.javaSource(
            "test.MyAssistedClass",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "final class MyAssistedClass {",
            "  private final Foo foo;",
            "  private final Bar bar;",
            "",
            "  @AssistedInject",
            "  MyAssistedClass(@Assisted Foo foo, Baz baz, @Assisted Bar bar) {",
            "    this.foo = foo;",
            "    this.bar = bar;",
            "  }",
            "}");
    Source componentAssistedFactory =
        CompilerTests.javaSource(
            "test.MyComponentAssistedFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface MyComponentAssistedFactory {",
            "  MyAssistedClass create(Bar bar, Foo foo);",
            "}");
    Source subcomponentAssistedFactory =
        CompilerTests.javaSource(
            "test.MySubcomponentAssistedFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface MySubcomponentAssistedFactory {",
            "  MyAssistedClass create(Bar bar, Foo foo);",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "final class Foo {}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "final class Bar {}");
    Source baz =
        CompilerTests.javaSource(
            "test.Baz",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Baz {",
            "  @Inject Baz() {}",
            "}");

    CompilerTests.daggerCompiler(
            component,
            subcomponent,
            assistedClass,
            componentAssistedFactory,
            subcomponentAssistedFactory,
            foo,
            bar,
            baz)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerMyComponent"));
            });
  }
}
