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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.room3.compiler.processing.util.CompilationResultSubject;
import androidx.room3.compiler.processing.util.Source;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.io.File;
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
  public void testAssistedFactory_nullableAssistedParam() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import javax.annotation.Nullable;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted @Nullable String str) {}",
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

    CompilerTests.daggerCompiler(foo, fooFactory, component)
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

    Source bar = CompilerTests.javaSource("test.Bar", "package test;", "", "interface Bar {}");

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
              subject.generatedSource(
                  goldenFileRule.goldenSource(
                      "test/Foo_Factory", compilerMode.isKotlinCodegenEnabled()));
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
    Source foo = CompilerTests.javaSource("test.Foo", "package test;", "final class Foo {}");
    Source bar = CompilerTests.javaSource("test.Bar", "package test;", "final class Bar {}");
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

  // Verify that Dagger does not support consuming assisted factories from libraries
  // compiled without Dagger's annotation processor.
  @Test
  public void assistedFactoryFromLibraryWithoutProcessor_strictValidationDisabled() {
    Source libraryTarget =
        CompilerTests.javaSource(
            "test.LibraryTarget",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedFactory;",
            "import dagger.assisted.AssistedInject;",
            "",
            "public final class LibraryTarget {",
            "  private final Dep dep;",
            "  private final String assistedDep;",
            "",
            "  public static class Dep {",
            "    @javax.inject.Inject",
            "    public Dep() {}",
            "  }",
            "",
            "  @AssistedInject",
            "  LibraryTarget(Dep dep, @Assisted String assistedDep) {",
            "    this.dep = dep;",
            "    this.assistedDep = assistedDep;",
            "  }",
            "",
            "  @AssistedFactory",
            "  public interface Factory {",
            "    LibraryTarget create(String assistedDep);",
            "  }",
            "}");

    // Compile the library without Dagger's annotation processor. Because we use libraryCompiler()
    // without registering Dagger's processor, no Dagger codegen (like the _Impl classes) is
    // performed.
    ImmutableList<File> libraryClasspath = CompilerTests.libraryCompiler(libraryTarget).compile();

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  LibraryTarget.Factory getFactory();",
            "}");

    ImmutableMap<String, String> options =
        ImmutableMap.<String, String>builder()
            .putAll(compilerMode.processorOptions())
            .put("dagger.strictAssistedInjectValidation", "disabled")
            .build();

    CompilerTests.DaggerCompiler daggerCompiler =
        CompilerTests.daggerCompiler(component)
            .withAdditionalClasspath(libraryClasspath)
            .withProcessingOptions(options);

    if (compilerMode.isFastInitEnabled()) {
      // In fastInit mode, by mistake Dagger allowed consuming assisted factories from
      // libraries compiled without the processor by generating the factories on-the-fly.
      // Now this is allowed when the strict validation flag is disabled.
      daggerCompiler.compile(
          subject -> {
            subject.hasErrorCount(0);
          });
    } else {
      daggerCompiler.compile(
          subject -> {
            subject.hasErrorContaining("LibraryTarget_Factory_Impl");
          });
    }
  }

  @Test
  public void testPublicGenericAssistedFactoryWithPackagePrivateTypeArguments() {
    Source foo =
        CompilerTests.javaSource(
            "pkg.a.Foo",
            "package pkg.a;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedFactory;",
            "import dagger.assisted.AssistedInject;",
            "",
            "public class Foo<T> {",
            "  @AssistedInject",
            "  Foo(@Assisted T arg) {}",
            "",
            "  @AssistedFactory",
            "  public interface Factory<T> {",
            "    Foo<T> create(T arg);",
            "  }",
            "}");

    Source bar =
        CompilerTests.javaSource(
            "pkg.b.PackagePrivateBar", "package pkg.b;", "", "class PackagePrivateBar {}");

    Source client =
        CompilerTests.javaSource(
            "pkg.b.Client",
            "package pkg.b;",
            "",
            "import javax.inject.Inject;",
            "import pkg.a.Foo;",
            "",
            "public class Client {",
            "  @Inject",
            "  Client(Foo.Factory<PackagePrivateBar> factory) {}",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import pkg.b.Client;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Client getClient();",
            "}");

    CompilerTests.DaggerCompiler daggerCompiler =
        CompilerTests.daggerCompiler(foo, bar, client, component)
            .withProcessingOptions(compilerMode.processorOptions());

    if (compilerMode.isFastInitEnabled()) {
      daggerCompiler.compile(subject -> subject.hasErrorCount(0));
    } else {
      // TODO(b/532992111): Fix javac visibility errors when using generic @AssistedFactory
      // interfaces across packages with package-private type arguments.
      daggerCompiler.compile(subject -> subject.hasErrorContaining("PackagePrivateBar"));
    }
  }

  @Test
  public void testInaccessibleAssistedFactory() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import java.util.List;",
            "",
            "class InaccessibleFoo {",
            "  @AssistedInject",
            "  InaccessibleFoo(",
            "      @Assisted String str, @Assisted int i, @Assisted List<String> list) {}",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "import java.util.List;",
            "",
            "@AssistedFactory",
            "public interface InaccessibleFooFactory {",
            "  InaccessibleFoo create(String str, int i, List<String> list);",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.InaccessibleFooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  InaccessibleFooFactory fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("subpackage/InaccessibleFooFactory_Proxy"));
            });
  }

  @Test
  public void testInaccessibleAssistedFactory_withPublicReturnType() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.PublicFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "public class PublicFoo {",
            "  @AssistedInject",
            "  PublicFoo(@Assisted InaccessibleBar bar) {}",
            "}");

    Source bar =
        CompilerTests.javaSource(
            "subpackage.InaccessibleBar", "package subpackage;", "", "class InaccessibleBar {}");

    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.FooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory {",
            "  PublicFoo create(InaccessibleBar bar);",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.FooFactory;",
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
              subject.generatedSource(goldenFileRule.goldenSource("subpackage/FooFactory_Proxy"));
            });
  }

  @Test
  public void testInaccessibleAssistedFactory_withTypeParameters() {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class InaccessibleFoo<T> {",
            "  @AssistedInject",
            "  InaccessibleFoo(@Assisted T arg) {}",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface InaccessibleFooFactory<T> {",
            "  InaccessibleFoo<T> create(T arg);",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.InaccessibleFooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  InaccessibleFooFactory<String> fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testInaccessibleAssistedFactory_withSelfReferencingTypeParameters() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class InaccessibleFoo<T extends Comparable<T>> {",
            "  @AssistedInject",
            "  InaccessibleFoo(@Assisted String str) {}",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface InaccessibleFooFactory<T extends Comparable<T>> {",
            "  InaccessibleFoo<T> create(String str);",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.InaccessibleFooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  InaccessibleFooFactory<Integer> fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("subpackage/InaccessibleFooFactory_Proxy"));
            });
  }

  @Test
  public void testInaccessibleAssistedFactory_withMutuallyRecursiveTypeParameters() {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class InaccessibleFoo<T1 extends Comparable<T2>, T2 extends Comparable<T1>> {",
            "  @AssistedInject",
            "  InaccessibleFoo(@Assisted String str) {}",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface InaccessibleFooFactory"
                + "<T1 extends Comparable<T2>, T2 extends Comparable<T1>> {",
            "  InaccessibleFoo<T1, T2> create(String str);",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.InaccessibleFooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  InaccessibleFooFactory<Integer, Integer> fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testInaccessibleAssistedFactory_withIntersectionTypeParameters() {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import java.io.Serializable;",
            "",
            "class InaccessibleFoo<T extends Number & Serializable> {",
            "  @AssistedInject",
            "  InaccessibleFoo(@Assisted String str) {}",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "import java.io.Serializable;",
            "",
            "@AssistedFactory",
            "public interface InaccessibleFooFactory<T extends Number & Serializable> {",
            "  InaccessibleFoo<T> create(String str);",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.InaccessibleFooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  InaccessibleFooFactory<Integer> fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testInaccessibleAssistedFactory_withParameterNamedProxy() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class InaccessibleFoo {",
            "  @AssistedInject",
            "  InaccessibleFoo(@Assisted String proxy) {}",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface InaccessibleFooFactory {",
            "  InaccessibleFoo create(String proxy);",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.InaccessibleFooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  InaccessibleFooFactory fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("subpackage/InaccessibleFooFactory_Proxy"));
            });
  }

  @Test
  public void testInaccessibleTypeArgumentInParameter() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.PublicFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import java.util.List;",
            "",
            "public class PublicFoo {",
            "  @AssistedInject",
            "  PublicFoo(@Assisted List<InaccessibleBar> bars) {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "subpackage.InaccessibleBar", "package subpackage;", "", "class InaccessibleBar {}");
    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.FooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "import java.util.List;",
            "",
            "@AssistedFactory",
            "public interface FooFactory {",
            "  PublicFoo create(List<InaccessibleBar> bars);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.FooFactory;",
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
              subject.generatedSource(goldenFileRule.goldenSource("subpackage/FooFactory_Proxy"));
            });
  }

  @Test
  public void testInaccessibleTypeArgumentInReturnType() {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.PublicFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "public class PublicFoo<T> {",
            "  @AssistedInject",
            "  PublicFoo(@Assisted String str) {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "subpackage.InaccessibleBar", "package subpackage;", "", "class InaccessibleBar {}");
    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.FooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory {",
            "  PublicFoo<InaccessibleBar> create(String str);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.FooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");

    CompilerTests.DaggerCompiler daggerCompiler =
        CompilerTests.daggerCompiler(foo, bar, fooFactory, component)
            .withProcessingOptions(compilerMode.processorOptions());

    if (compilerMode.isFastInitEnabled()) {
      daggerCompiler.compile(subject -> subject.hasErrorCount(0));
    } else {
      // TODO(b/532992111): Fix javac visibility errors in the component's assisted injection
      // factory field (i.e. PublicFoo_Factory<InaccessibleBar>) by falling back to the raw type.
      daggerCompiler.compile(subject -> subject.hasErrorContaining("InaccessibleBar"));
    }
  }

  @Test
  public void testInaccessibleAssistedFactoryMethod() {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.PublicFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "public class PublicFoo {",
            "  @AssistedInject",
            "  PublicFoo(@Assisted String str) {}",
            "}");
    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.FooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public abstract class FooFactory {",
            "  abstract PublicFoo create(String str);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.FooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testProtectedAssistedFactoryMethod() {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.PublicFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "public class PublicFoo {",
            "  @AssistedInject",
            "  PublicFoo(@Assisted String str) {}",
            "}");
    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.FooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public abstract class FooFactory {",
            "  protected abstract PublicFoo create(String str);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.FooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              assertFileNotGenerated(subject, "subpackage/FooFactory_Proxy.java");
              subject
                  .generatedSourceFileWithPath("test/DaggerTestComponent.java")
                  .doesNotContain("FooFactory_Proxy");
              if (compilerMode.isFastInitEnabled()) {
                subject
                    .generatedSourceFileWithPath("test/DaggerTestComponent.java")
                    .contains("new FooFactory()");
              }
            });
  }

  @Test
  public void testParameterizedProtectedAssistedFactoryMethod_withPublicTypeArgument() {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.PublicFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "public class PublicFoo<T> {",
            "  @AssistedInject",
            "  PublicFoo(@Assisted T arg) {}",
            "}");
    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.FooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public abstract class FooFactory<T> {",
            "  protected abstract PublicFoo<T> create(T arg);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.FooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory<String> fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject
                  .generatedSourceFileWithPath("test/DaggerTestComponent.java")
                  .doesNotContain("FooFactory_Proxy");
              if (compilerMode.isFastInitEnabled()) {
                subject
                    .generatedSourceFileWithPath("test/DaggerTestComponent.java")
                    .contains("new FooFactory<String>()");
              }
            });
  }

  @Test
  public void testInaccessibleAssistedFactoryConstructor() {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.PublicFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "public class PublicFoo {",
            "  @AssistedInject",
            "  PublicFoo(@Assisted String str) {}",
            "}");
    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.FooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public abstract class FooFactory {",
            "  FooFactory() {}",
            "",
            "  public abstract PublicFoo create(String str);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.FooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSourceFileWithPath("subpackage/FooFactory_Proxy.java");
              if (compilerMode.isFastInitEnabled()) {
                subject
                    .generatedSourceFileWithPath("test/DaggerTestComponent.java")
                    .contains("FooFactory_Proxy.createFactory");
              }
            });
  }

  @Test
  public void testPublicAssistedFactoryInheritingFromPackagePrivateSupertype() {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.PublicFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "public class PublicFoo {",
            "  @AssistedInject",
            "  PublicFoo(@Assisted String str) {}",
            "}");
    Source baseFactory =
        CompilerTests.javaSource(
            "subpackage.BaseFactory",
            "package subpackage;",
            "",
            "interface BaseFactory {",
            "  PublicFoo create(String str);",
            "}");
    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.FooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory extends BaseFactory {}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.FooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, baseFactory, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              assertFileNotGenerated(subject, "subpackage/FooFactory_Proxy.java");
              subject
                  .generatedSourceFileWithPath("test/DaggerTestComponent.java")
                  .doesNotContain("FooFactory_Proxy");
              if (compilerMode.isFastInitEnabled()) {
                subject
                    .generatedSourceFileWithPath("test/DaggerTestComponent.java")
                    .contains("new FooFactory()");
              }
            });
  }

  private static void assertFileNotGenerated(CompilationResultSubject subject, String filePath) {
    AssertionError error =
        assertThrows(AssertionError.class, () -> subject.generatedSourceFileWithPath(filePath));
    assertThat(error).hasMessageThat().contains("Didn't generate file");
  }

  @Test
  public void testAssistedFactoryWithInheritedGenericMethodAndInaccessibleTypes() {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class InaccessibleFoo {",
            "  @AssistedInject",
            "  InaccessibleFoo(@Assisted InaccessibleBar bar) {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "subpackage.InaccessibleBar", "package subpackage;", "", "class InaccessibleBar {}");
    Source baseFactory =
        CompilerTests.javaSource(
            "subpackage.BaseFactory",
            "package subpackage;",
            "",
            "public interface BaseFactory<F, B> {",
            "  F create(B bar);",
            "}");
    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.FooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory extends BaseFactory<InaccessibleFoo, InaccessibleBar> {}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.FooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");

    CompilerTests.daggerCompiler(foo, bar, baseFactory, fooFactory, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testInaccessibleAssistedFactory_withBoundedTypeParameterInParameter() {
    Source foo =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFoo",
            "package subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class InaccessibleFoo<T extends Number> {",
            "  @AssistedInject",
            "  InaccessibleFoo(@Assisted T arg) {}",
            "}");
    Source fooFactory =
        CompilerTests.javaSource(
            "subpackage.InaccessibleFooFactory",
            "package subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface InaccessibleFooFactory<T extends Number> {",
            "  InaccessibleFoo<T> create(T arg);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import subpackage.InaccessibleFooFactory;",
            "",
            "@Component",
            "interface TestComponent {",
            "  InaccessibleFooFactory<Integer> fooFactory();",
            "}");

    CompilerTests.DaggerCompiler daggerCompiler =
        CompilerTests.daggerCompiler(foo, fooFactory, component)
            .withProcessingOptions(compilerMode.processorOptions());

    if (compilerMode == CompilerMode.FAST_INIT_MODE) {
      // TODO(b/578079423): Align proxy parameter types with *_Factory#newInstance parameter
      // types when type parameters are used.
      daggerCompiler.compile(
          subject ->
              subject.hasErrorContaining(
                  "method newInstance in class subpackage.InaccessibleFoo_Factory<T> cannot be"
                      + " applied to given types"));
    } else {
      daggerCompiler.compile(subject -> subject.hasErrorCount(0));
    }
  }

  @Test
  public void assistedFactoryFromLibraryWithoutProcessor_strictValidationEnabled() {
    Source libraryTarget =
        CompilerTests.javaSource(
            "test.LibraryTarget",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedFactory;",
            "import dagger.assisted.AssistedInject;",
            "",
            "public final class LibraryTarget {",
            "  private final Dep dep;",
            "  private final String assistedDep;",
            "",
            "  public static class Dep {",
            "    @javax.inject.Inject",
            "    public Dep() {}",
            "  }",
            "",
            "  @AssistedInject",
            "  LibraryTarget(Dep dep, @Assisted String assistedDep) {",
            "    this.dep = dep;",
            "    this.assistedDep = assistedDep;",
            "  }",
            "",
            "  @AssistedFactory",
            "  public interface Factory {",
            "    LibraryTarget create(String assistedDep);",
            "  }",
            "}");

    ImmutableList<File> libraryClasspath = CompilerTests.libraryCompiler(libraryTarget).compile();

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  LibraryTarget.Factory getFactory();",
            "}");

    ImmutableMap<String, String> options =
        ImmutableMap.<String, String>builder()
            .putAll(compilerMode.processorOptions())
            .put("dagger.strictAssistedInjectValidation", "enabled")
            .build();

    CompilerTests.daggerCompiler(component)
        .withAdditionalClasspath(libraryClasspath)
        .withProcessingOptions(options)
        .compile(
            subject -> {
              subject.hasErrorContaining(
                  "The @AssistedInject-annotated class test.LibraryTarget is in a dependency, "
                      + "but its factory test.LibraryTarget_Factory was not found. Make sure "
                      + "the Dagger annotation processor is applied to the library.");
            });
  }

  @Test
  public void assistedFactoryFromLibraryWithProcessor_strictValidationEnabled() {
    Source libraryTarget =
        CompilerTests.javaSource(
            "test.LibraryTarget",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedFactory;",
            "import dagger.assisted.AssistedInject;",
            "",
            "public final class LibraryTarget {",
            "  private final Dep dep;",
            "  private final String assistedDep;",
            "",
            "  public static class Dep {",
            "    @javax.inject.Inject",
            "    public Dep() {}",
            "  }",
            "",
            "  @AssistedInject",
            "  LibraryTarget(Dep dep, @Assisted String assistedDep) {",
            "    this.dep = dep;",
            "    this.assistedDep = assistedDep;",
            "  }",
            "",
            "  @AssistedFactory",
            "  public interface Factory {",
            "    LibraryTarget create(String assistedDep);",
            "  }",
            "}");

    ImmutableList<File> libraryClasspath =
        CompilerTests.daggerCompiler(libraryTarget).compileFilesWithJavac();

    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  LibraryTarget.Factory getFactory();",
            "}");

    ImmutableMap<String, String> options =
        ImmutableMap.<String, String>builder()
            .putAll(compilerMode.processorOptions())
            .put("dagger.strictAssistedInjectValidation", "enabled")
            .build();

    CompilerTests.daggerCompiler(component)
        .withAdditionalClasspath(libraryClasspath)
        .withProcessingOptions(options)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
            });
  }
}
