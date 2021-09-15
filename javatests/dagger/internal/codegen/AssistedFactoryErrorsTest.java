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
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssistedFactoryErrorsTest {
  @Parameters(name = "{0}")
  public static ImmutableCollection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public AssistedFactoryErrorsTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void testFactoryNotAbstract() {
    JavaFileObject factory =
        JavaFileObjects.forSourceLines(
            "test.Factory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory class Factory {}");
    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(factory);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "The @AssistedFactory-annotated type must be either an abstract class or interface.");
  }

  @Test
  public void testNestedFactoryNotStatic() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import dagger.assisted.AssistedFactory;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted int i) {}",
            "",
            "  @AssistedFactory",
            "  abstract class Factory {",
            "    abstract Foo create(int i);",
            "  }",
            "}");
    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("Nested @AssistedFactory-annotated types must be static.");
  }

  @Test
  public void testFactoryMissingAbstractMethod() {
    JavaFileObject factory =
        JavaFileObjects.forSourceLines(
            "test.Factory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory interface Factory {}");
    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(factory);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "The @AssistedFactory-annotated type is missing an abstract, non-default method whose"
                + " return type matches the assisted injection type.");
  }

  @Test
  public void testFactoryReturnsNonDeclaredType() {
    JavaFileObject noInject =
        JavaFileObjects.forSourceLines(
            "test.NoInject", "package test;", "", "final class NoInject {}");
    JavaFileObject noAssistedParam =
        JavaFileObjects.forSourceLines(
            "test.NoAssistedParam",
            "package test;",
            "",
            "import dagger.assisted.AssistedInject;",
            "",
            "final class NoAssistedParam {",
            "  @AssistedInject NoAssistedParam() {}",
            "}");
    JavaFileObject factory =
        JavaFileObjects.forSourceLines(
            "test.Factory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface Factory<T> {",
            "  int createInt();", // Fails return type not @AssistedInject
            "",
            "  NoInject createNoInject();", // Fails return type not @AssistedInject
            "",
            "  NoAssistedParam createNoAssistedParam();", // Succeeds
            "",
            "  T createT();", // Fails return type not @AssistedInject
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(factory, noInject, noAssistedParam);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(4);

    assertThat(compilation)
        .hadErrorContaining(
            "The @AssistedFactory-annotated type should contain a single abstract, non-default "
                + "method but found multiple: ["
                + "createInt(), createNoInject(), createNoAssistedParam(), createT()]")
        .inFile(factory)
        .onLine(6);

    assertThat(compilation)
        .hadErrorContaining(
            "Invalid return type: int. "
                + "An assisted factory's abstract method must return a type with an "
                + "@AssistedInject-annotated constructor.")
        .inFile(factory)
        .onLine(7);

    assertThat(compilation)
        .hadErrorContaining(
            "Invalid return type: test.NoInject. "
                + "An assisted factory's abstract method must return a type with an "
                + "@AssistedInject-annotated constructor.")
        .inFile(factory)
        .onLine(9);

    assertThat(compilation)
        .hadErrorContaining(
            "Invalid return type: T. "
                + "An assisted factory's abstract method must return a type with an "
                + "@AssistedInject-annotated constructor.")
        .inFile(factory)
        .onLine(13);
  }

  @Test
  public void testFactoryMultipleAbstractMethods() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject Foo(@Assisted int i) {}",
            "}");

    JavaFileObject fooFactoryInterface =
        JavaFileObjects.forSourceLines(
            "test.FooFactoryInterface",
            "package test;",
            "",
            "interface FooFactoryInterface {",
            " Foo createFoo1(int i);",
            "}");

    JavaFileObject fooFactory =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory extends FooFactoryInterface {",
            " Foo createFoo2(int i);",
            "",
            " Foo createFoo3(int i);",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, fooFactory, fooFactoryInterface);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "The @AssistedFactory-annotated type should contain a single abstract, non-default "
                + "method but found multiple: [createFoo1(int), createFoo2(int), createFoo3(int)]");
  }

  @Test
  public void testFactoryMismatchingParameter() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject Foo(@Assisted int i) {}",
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
            " Foo create(String i);",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, fooFactory);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "The parameters in the factory method must match the @Assisted parameters in "
                + "test.Foo.\n"
                + "        Actual: test.FooFactory#create(java.lang.String)\n"
                + "      Expected: test.FooFactory#create(int)");
  }

  @Test
  public void testFactoryMismatchingGenericParameter() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo<T> {",
            "  @AssistedInject Foo(@Assisted T t) {}",
            "}");

    JavaFileObject fooFactory =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory<T> {",
            "  Foo<T> create(String str);",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, fooFactory);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "The parameters in the factory method must match the @Assisted parameters in "
                + "test.Foo<T>.\n"
                + "        Actual: test.FooFactory#create(java.lang.String)\n"
                + "      Expected: test.FooFactory#create(T)");
  }

  @Test
  public void testFactoryDuplicateGenericParameter() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo<T> {",
            "  @AssistedInject Foo(@Assisted String str, @Assisted T t) {}",
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
            "  Foo<String> create(String str1, String str2);",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, fooFactory);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@AssistedFactory method has duplicate @Assisted types: @Assisted java.lang.String");
  }

  @Test
  public void testAssistedInjectionRequest() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject Foo(@Assisted String str) {}",
            "}");

    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class Bar {",
            "  @Inject",
            "  Bar(Foo foo, Provider<Foo> fooProvider) {}",
            "}");

    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.FooModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Provider;",
            "",
            "@Module",
            "class FooModule {",
            "  @Provides",
            "  static int provideInt(Foo foo, Provider<Foo> fooProvider) {",
            "    return 0;",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface FooComponent {",
            "  Foo foo();",
            "",
            "  Provider<Foo> fooProvider();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, bar, module, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(6);

    String fooError =
        "Dagger does not support injecting @AssistedInject type, test.Foo. "
            + "Did you mean to inject its assisted factory type instead?";
    assertThat(compilation).hadErrorContaining(fooError).inFile(bar).onLine(8);
    assertThat(compilation).hadErrorContaining(fooError).inFile(module).onLine(10);
    assertThat(compilation).hadErrorContaining(fooError).inFile(component).onLine(8);

    String fooProviderError =
        "Dagger does not support injecting @AssistedInject type, javax.inject.Provider<test.Foo>. "
            + "Did you mean to inject its assisted factory type instead?";
    assertThat(compilation).hadErrorContaining(fooProviderError).inFile(bar).onLine(8);
    assertThat(compilation).hadErrorContaining(fooProviderError).inFile(module).onLine(10);
    assertThat(compilation).hadErrorContaining(fooProviderError).inFile(component).onLine(10);
  }

  @Test
  public void testProvidesAssistedBindings() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import dagger.assisted.AssistedFactory;",
            "",
            "class Foo {",
            "  @AssistedInject Foo(@Assisted int i) {}",
            "",
            "  @AssistedFactory",
            "  interface Factory {",
            "    Foo create(int i);",
            "  }",
            "}");

    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.FooModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Provider;",
            "",
            "@Module",
            "class FooModule {",
            "  @Provides",
            "  static Foo provideFoo() {",
            "    return null;",
            "  }",
            "",
            "  @Provides",
            "  static Foo.Factory provideFooFactory() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo, module);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(2);
    assertThat(compilation)
        .hadErrorContaining("[test.Foo] Dagger does not support providing @AssistedInject types.")
        .inFile(module)
        .onLine(10);
    assertThat(compilation)
        .hadErrorContaining(
            "[test.Foo.Factory] Dagger does not support providing @AssistedFactory types.")
        .inFile(module)
        .onLine(15);
  }

  @Test
  public void testProvidesAssistedBindingsAsFactoryBindsInstance() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import dagger.assisted.AssistedFactory;",
            "",
            "class Foo {",
            "  @AssistedInject Foo(@Assisted int i) {}",
            "",
            "  @AssistedFactory",
            "  interface Factory {",
            "    Foo create(int i);",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.BindsInstance;",
            "",
            "@Component",
            "interface FooComponent {",
            "  @Component.Factory",
            "  interface Factory {",
            "    FooComponent create(",
            "        @BindsInstance Foo foo,",
            "        @BindsInstance Foo.Factory fooFactory);",
            "  }",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(2);
    assertThat(compilation)
        .hadErrorContaining("[test.Foo] Dagger does not support providing @AssistedInject types.")
        .inFile(component)
        .onLine(11);
    assertThat(compilation)
        .hadErrorContaining(
            "[test.Foo.Factory] Dagger does not support providing @AssistedFactory types.")
        .inFile(component)
        .onLine(12);
  }

  @Test
  public void testProvidesAssistedBindingsAsBuilderBindsInstance() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import dagger.assisted.AssistedFactory;",
            "",
            "class Foo {",
            "  @AssistedInject Foo(@Assisted int i) {}",
            "",
            "  @AssistedFactory",
            "  interface Factory {",
            "    Foo create(int i);",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.BindsInstance;",
            "",
            "@Component",
            "interface FooComponent {",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder foo(Foo foo);",
            "    @BindsInstance Builder fooFactory(Foo.Factory fooFactory);",
            "    FooComponent build();",
            "  }",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(2);
    assertThat(compilation)
        .hadErrorContaining("[test.Foo] Dagger does not support providing @AssistedInject types.")
        .inFile(component)
        .onLine(10);
    assertThat(compilation)
        .hadErrorContaining(
            "[test.Foo.Factory] Dagger does not support providing @AssistedFactory types.")
        .inFile(component)
        .onLine(11);
  }

  @Test
  public void testProvidesAssistedBindingsAsOptional() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import dagger.assisted.AssistedFactory;",
            "",
            "class Foo {",
            "  @AssistedInject Foo() {}",
            "",
            "  @AssistedFactory",
            "  interface Factory {",
            "    Foo create();",
            "  }",
            "}");

    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.FooModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface FooModule {",
            "  @BindsOptionalOf Foo optionalFoo();",
            "",
            "  @BindsOptionalOf Foo.Factory optionalFooFactory();",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo, module);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(2);
    assertThat(compilation)
        .hadErrorContaining("[test.Foo] Dagger does not support providing @AssistedInject types.")
        .inFile(module)
        .onLine(9);
    assertThat(compilation)
        .hadErrorContaining(
            "[test.Foo.Factory] Dagger does not support providing @AssistedFactory types.")
        .inFile(module)
        .onLine(11);
  }

  @Test
  public void testInjectsLazyOfAssistedFactory() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import dagger.assisted.AssistedFactory;",
            "",
            "class Foo {",
            "  @AssistedInject Foo(@Assisted int i) {}",
            "",
            "  @AssistedFactory",
            "  interface Factory {",
            "    Foo create(int i);",
            "  }",
            "}");

    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject",
            "  Bar(Foo.Factory fooFactory, Lazy<Foo.Factory> fooFactoryLazy) {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo, bar);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "Dagger does not support injecting Lazy<T>, Producer<T>, or Produced<T> "
                + "when T is an @AssistedFactory-annotated type such as test.Foo.Factory")
        .inFile(bar)
        .onLine(8);
  }

  @Test
  public void testScopedAssistedInjection() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import dagger.assisted.AssistedFactory;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted int i) {}",
            "",
            "  @AssistedFactory",
            "  interface Factory {",
            "    Foo create(int i);",
            "  }",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("A type with an @AssistedInject-annotated constructor cannot be scoped")
        .inFile(foo)
        .onLine(8);
  }

  @Test
  public void testMultipleInjectAnnotations() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject",
            "  @AssistedInject",
            "  Foo(@Assisted int i) {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "Constructors cannot be annotated with both @Inject and @AssistedInject");
  }

  @Test
  public void testAssistedInjectNotOnConstructor() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  void someMethod() {}",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);

    // Note: this isn't actually a Dagger error, it's a javac error since @AssistedInject only
    // targets constructors. However, it's good to have this test in case that ever changes.
    assertThat(compilation)
        .hadErrorContaining("annotation type not applicable to this kind of declaration")
        .inFile(foo)
        .onLine(6);
  }

  @Test
  public void testAssistedInjectWithNoAssistedParametersIsNotInjectable() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject",
            "  Foo(Bar bar) {}",
            "}");

    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import dagger.assisted.AssistedInject;",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @AssistedInject",
            "  Bar() {}",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface FooComponent {",
            "  Foo foo();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, bar, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(2);
    assertThat(compilation)
        .hadErrorContaining(
            "Dagger does not support injecting @AssistedInject type, test.Bar. "
                + "Did you mean to inject its assisted factory type instead?")
        .inFile(foo)
        .onLine(7);
    assertThat(compilation)
        .hadErrorContaining(
            "\033[1;31m[Dagger/MissingBinding]\033[0m "
                + "Foo cannot be provided without an @Inject constructor or an @Provides-annotated "
                + "method.");
  }

  @Test
  public void testInaccessibleFoo() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.subpackage.InaccessibleFoo",
            "package test.subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class InaccessibleFoo {",
            "  @AssistedInject InaccessibleFoo(@Assisted int i) {}",
            "}");

    JavaFileObject fooFactory =
        JavaFileObjects.forSourceLines(
            "test.subpackage.InaccessibleFooFactory",
            "package test.subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface InaccessibleFooFactory {",
            "  InaccessibleFoo create(int i);",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.FooFactoryComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import test.subpackage.InaccessibleFooFactory;",
            "",
            "@Component",
            "interface FooFactoryComponent {",
            "  InaccessibleFooFactory inaccessibleFooFactory();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, fooFactory, component);

    if (compilerMode == CompilerMode.FAST_INIT_MODE) {
      // TODO(bcorso): Remove once we fix inaccessible assisted factory imlementation for fastInit.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining(
              "test.subpackage.InaccessibleFoo is not public in test.subpackage; cannot be "
                  + "accessed from outside package");
    } else {
      assertThat(compilation).succeeded();
    }
  }

  @Test
  public void testAssistedFactoryMethodWithTypeParametersFails() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.AssistedInject;",
            "import dagger.assisted.AssistedFactory;",
            "",
            "class Foo<T> {",
            "  @AssistedInject",
            "  Foo() {}",
            "",
            "  @AssistedFactory",
            "  interface FooFactory {",
            "    <T> Foo<T> create();",
            "  }",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts()).compile(foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "@AssistedFactory does not currently support type parameters in the creator method.")
        .inFile(foo)
        .onLine(12);
  }
}
