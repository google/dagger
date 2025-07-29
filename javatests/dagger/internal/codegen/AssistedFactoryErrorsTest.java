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
    Source foo =
        CompilerTests.javaSource(
            "test.Factory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory class Factory {}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The @AssistedFactory-annotated type must be either an abstract class or "
                      + "interface.");
            });
  }

  @Test
  public void testNestedFactoryNotStatic() {
    Source foo =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Nested @AssistedFactory-annotated types must be static.");
            });
  }

  @Test
  public void testFactoryMissingAbstractMethod() {
    Source factory =
        CompilerTests.javaSource(
            "test.Factory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory interface Factory {}");

    CompilerTests.daggerCompiler(factory)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The @AssistedFactory-annotated type is missing an abstract, non-default method "
                      + "whose return type matches the assisted injection type.");
            });
  }

  @Test
  public void testFactoryReturnsNonDeclaredType() {
    Source noInject =
        CompilerTests.javaSource(
            "test.NoInject",
            "package test;",
            "",
            "final class NoInject {}");

    Source noAssistedParam =
        CompilerTests.javaSource(
            "test.NoAssistedParam",
            "package test;",
            "",
            "import dagger.assisted.AssistedInject;",
            "",
            "final class NoAssistedParam {",
            "  @AssistedInject NoAssistedParam() {}",
            "}");

    Source factory =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(factory, noInject, noAssistedParam)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(4);
              subject.hasErrorContaining(
                      "The @AssistedFactory-annotated type should contain a single abstract, "
                          + "non-default method but found multiple: ["
                          + "test.Factory.createInt(), "
                          + "test.Factory.createNoInject(), "
                          + "test.Factory.createNoAssistedParam(), "
                          + "test.Factory.createT()"
                          + "]")
                  .onSource(factory)
                  .onLine(6);
              subject.hasErrorContaining(
                      "Invalid return type: int. "
                          + "An assisted factory's abstract method must return a type with an "
                          + "@AssistedInject-annotated constructor.")
                  .onSource(factory)
                  .onLine(7);
              subject.hasErrorContaining(
                      "Invalid return type: test.NoInject. "
                          + "An assisted factory's abstract method must return a type with an "
                          + "@AssistedInject-annotated constructor.")
                  .onSource(factory)
                  .onLine(9);
              subject.hasErrorContaining(
                      "Invalid return type: T. "
                          + "An assisted factory's abstract method must return a type with an "
                          + "@AssistedInject-annotated constructor.")
                  .onSource(factory)
                  .onLine(13);
            });
  }

  @Test
  public void testFactoryMultipleAbstractMethods() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject Foo(@Assisted int i) {}",
            "}");

    Source fooFactoryInterface =
        CompilerTests.javaSource(
            "test.FooFactoryInterface",
            "package test;",
            "",
            "interface FooFactoryInterface {",
            " Foo createFoo1(int i);",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo, fooFactory, fooFactoryInterface)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "The @AssistedFactory-annotated type should contain a single abstract, "
                          + "non-default method but found multiple: ["
                          + "test.FooFactoryInterface.createFoo1(int), "
                          + "test.FooFactory.createFoo2(int), "
                          + "test.FooFactory.createFoo3(int)"
                          + "]");
            });
  }

  @Test
  public void testFactoryMismatchingParameter() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject Foo(@Assisted int i) {}",
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
            " Foo create(String i);",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The parameters in the factory method must match the @Assisted parameters in "
                      + "test.Foo.");
              subject.hasErrorContaining("  Actual: test.FooFactory#create(java.lang.String)");
              subject.hasErrorContaining("Expected: test.FooFactory#create(int)");
            });
  }

  @Test
  public void testFactoryMismatchingGenericParameter() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo<T> {",
            "  @AssistedInject Foo(@Assisted T t) {}",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory<T> {",
            "  Foo<T> create(String str);",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The parameters in the factory method must match the @Assisted parameters in "
                      + "test.Foo<T>.");
              subject.hasErrorContaining("  Actual: test.FooFactory#create(java.lang.String)");
              subject.hasErrorContaining("Expected: test.FooFactory#create(T)");
            });
  }

  @Test
  public void testFactoryDuplicateGenericParameter() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo<T> {",
            "  @AssistedInject Foo(@Assisted String str, @Assisted T t) {}",
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
            "  Foo<String> create(String str1, String str2);",
            "}");

    CompilerTests.daggerCompiler(foo, fooFactory)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@AssistedFactory method has duplicate @Assisted types: "
                      + "@Assisted java.lang.String");
            });
  }

  @Test
  public void testAssistedInjectionRequest() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject Foo(@Assisted String str) {}",
            "}");

    Source bar =
        CompilerTests.javaSource(
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

    Source module =
        CompilerTests.javaSource(
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

    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo, bar, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(6);

              String fooError =
                  "Dagger does not support injecting @AssistedInject type, test.Foo. "
                      + "Did you mean to inject its assisted factory type instead?";
              subject.hasErrorContaining(fooError).onSource(bar).onLine(8);
              subject.hasErrorContaining(fooError).onSource(module).onLine(10);
              subject.hasErrorContaining(fooError).onSource(component).onLine(8);

              String fooProviderError =
                  "Dagger does not support injecting @AssistedInject type, "
                      + "javax.inject.Provider<test.Foo>. "
                      + "Did you mean to inject its assisted factory type instead?";
              subject.hasErrorContaining(fooProviderError).onSource(bar).onLine(8);
              subject.hasErrorContaining(fooProviderError).onSource(module).onLine(10);
              subject.hasErrorContaining(fooProviderError).onSource(component).onLine(10);
            });
  }

  @Test
  public void testProvidesAssistedBindings() {
    Source foo =
        CompilerTests.javaSource(
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

    Source module =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo, module)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject
                  .hasErrorContaining(
                      "[test.Foo] Dagger does not support providing @AssistedInject types without a"
                          + " qualifier.")
                  .onSource(module)
                  .onLine(10);
              subject
                  .hasErrorContaining(
                      "[test.Foo.Factory] Dagger does not support providing @AssistedFactory "
                          + "types.")
                  .onSource(module)
                  .onLine(15);
            });
  }

  @Test
  public void testProvidesAssistedBindingsAsFactoryBindsInstance() {
    Source foo =
        CompilerTests.javaSource(
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

    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject
                  .hasErrorContaining(
                      "[test.Foo] Dagger does not support providing @AssistedInject types without a"
                          + " qualifier.")
                  .onSource(component)
                  .onLine(11);
              subject
                  .hasErrorContaining(
                      "[test.Foo.Factory] Dagger does not support providing @AssistedFactory "
                          + "types.")
                  .onSource(component)
                  .onLine(12);
            });
  }

  @Test
  public void testProvidesAssistedBindingsAsBuilderBindsInstance() {
    Source foo =
        CompilerTests.javaSource(
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

    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject
                  .hasErrorContaining(
                      "[test.Foo] Dagger does not support providing @AssistedInject types without a"
                          + " qualifier.")
                  .onSource(component)
                  .onLine(10);
              subject
                  .hasErrorContaining(
                      "[test.Foo.Factory] Dagger does not support providing @AssistedFactory "
                          + "types.")
                  .onSource(component)
                  .onLine(11);
            });
  }

  @Test
  public void testProvidesAssistedBindingsAsOptional() {
    Source foo =
        CompilerTests.javaSource(
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

    Source module =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo, module)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject
                  .hasErrorContaining(
                      "[test.Foo] Dagger does not support providing @AssistedInject types without a"
                          + " qualifier.")
                  .onSource(module)
                  .onLine(9);
              subject
                  .hasErrorContaining(
                      "[test.Foo.Factory] Dagger does not support providing @AssistedFactory "
                          + "types.")
                  .onSource(module)
                  .onLine(11);
            });
  }

  @Test
  public void testInjectsLazyOfAssistedFactory() {
    Source foo =
        CompilerTests.javaSource(
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

    Source bar =
        CompilerTests.javaSource(
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


    CompilerTests.daggerCompiler(foo, bar)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Dagger does not support injecting Lazy<T>, Producer<T>, or Produced<T> "
                          + "when T is an @AssistedFactory-annotated type such as test.Foo.Factory")
                  .onSource(bar)
                  .onLine(8);
            });
  }

  @Test
  public void testScopedAssistedInjection() {
    Source foo =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject ->
                // Don't assert on the number of errors as Foo_Factory_Impl can also be created
                // and have errors from the missing Foo_Factory.
                // TODO(erichang): don't generate the factory impls if there are errors with the
                // assisted type
                subject
                    .hasErrorContaining(
                        "A type with an @AssistedInject-annotated constructor cannot be scoped")
                    .onSource(foo)
                    .onLine(8));
  }

  @Test
  public void testMultipleInjectAnnotations() {
    Source foo =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Constructors cannot be annotated with both @Inject and @AssistedInject");
            });
  }

  @Test
  public void testInjectWithAssistedAnnotations() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject",
            "  Foo(@Assisted int i) {}",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@Assisted parameters can only be used within an @AssistedInject-annotated "
                      + "constructor");
            });
  }

  @Test
  public void testAssistedInjectWithNoAssistedParametersIsNotInjectable() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject",
            "  Foo(Bar bar) {}",
            "}");

    Source bar =
        CompilerTests.javaSource(
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

    Source component =
        CompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface FooComponent {",
            "  Foo foo();",
            "}");


    CompilerTests.daggerCompiler(foo, bar, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                      "Dagger does not support injecting @AssistedInject type, test.Bar. "
                          + "Did you mean to inject its assisted factory type instead?")
                  .onSource(foo)
                  .onLine(7);
              subject.hasErrorContaining(
                  "\033[1;31m[Dagger/MissingBinding]\033[0m "
                      + "Foo cannot be provided without an @Inject constructor or an "
                      + "@Provides-annotated method.");
            });
  }

  @Test
  public void testInaccessibleFoo() {
    Source foo =
        CompilerTests.javaSource(
            "test.subpackage.InaccessibleFoo",
            "package test.subpackage;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class InaccessibleFoo {",
            "  @AssistedInject InaccessibleFoo(@Assisted int i) {}",
            "}");

    Source fooFactory =
        CompilerTests.javaSource(
            "test.subpackage.InaccessibleFooFactory",
            "package test.subpackage;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface InaccessibleFooFactory {",
            "  InaccessibleFoo create(int i);",
            "}");

    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.DaggerCompiler daggerCompiler =
        CompilerTests.daggerCompiler(foo, fooFactory, component)
            .withProcessingOptions(compilerMode.processorOptions());

      if (compilerMode == CompilerMode.FAST_INIT_MODE) {
      // TODO(bcorso): Remove once we fix inaccessible assisted factory implementation for fastInit.
      daggerCompiler.compile(
          subject ->
              // TODO(bcorso): We don't report the error count here because javac reports
              // the error once, whereas ksp reports the error twice.
              subject.hasErrorContaining(
                  "test.subpackage.InaccessibleFoo is not public in test.subpackage; cannot be "
                      + "accessed from outside package"));
    } else {
      daggerCompiler.compile(subject -> subject.hasErrorCount(0));
    }
  }

  @Test
  public void testAssistedFactoryMethodWithTypeParametersFails() {
    Source foo =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@AssistedFactory does not currently support type parameters in the creator "
                          + "method.")
                  .onSource(foo)
                  .onLine(12);
            });
  }

  @Test
  public void testAssistedFactoryMethod_withNullableReturnType_withJavaSource_succeeds() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.AssistedInject;",
            "import dagger.assisted.AssistedFactory;",
            "import javax.annotation.Nullable;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo() {}",
            "",
            "  @AssistedFactory",
            "  interface FooFactory {",
            "    @Nullable Foo create();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void testAssistedFactoryMethod_withNullableReturnType_withKotlinSource_succeeds() {
    Source foo =
        CompilerTests.kotlinSource(
            "test.Foo.kt",
            "package test",
            "",
            "import dagger.assisted.AssistedInject",
            "import dagger.assisted.AssistedFactory",
            "",
            "class Foo @AssistedInject constructor() {",
            "  @AssistedFactory",
            "  interface FooFactory {",
            "    fun create(): Foo?",
            "  }",
            "}");

    CompilerTests.daggerCompiler(foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }
}
