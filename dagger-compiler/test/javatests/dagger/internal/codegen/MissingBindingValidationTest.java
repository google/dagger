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

import static com.google.common.truth.Truth.assertThat;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.util.DiagnosticMessage;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import java.util.List;
import javax.tools.Diagnostic;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MissingBindingValidationTest {
  private static final String JVM_SUPPRESS_WILDCARDS_MESSAGE =
      "(For Kotlin sources, you may need to use '@JvmSuppressWildcards' or '@JvmWildcard' if you "
          + "need to explicitly control the wildcards at a particular usage site.)";

  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public MissingBindingValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void dependOnInterface() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface MyComponent {",
            "  Foo getFoo();",
            "}");
    Source injectable =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    Source nonInjectable =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "interface Bar {}");
    CompilerTests.daggerCompiler(component, injectable, nonInjectable)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Bar cannot be provided without an @Provides-annotated method.")
                  .onSource(component)
                  .onLineContaining("interface MyComponent");
            });
  }

  @Test
  public void entryPointDependsOnInterface() {
    Source component =
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "final class TestClass {",
            "  interface A {}",
            "",
            "  @Component()",
            "  interface AComponent {",
            "    A getA();",
            "  }",
            "}");
    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "\033[1;31m[Dagger/MissingBinding]\033[0m TestClass.A cannot be provided "
                          + "without an @Provides-annotated method.")
                  .onSource(component)
                  .onLineContaining("interface AComponent");
            });
  }

  @Test
  public void entryPointDependsOnQualifiedInterface() {
    Source component =
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Qualifier;",
            "",
            "final class TestClass {",
            "  @Qualifier @interface Q {}",
            "  interface A {}",
            "",
            "  @Component()",
            "  interface AComponent {",
            "    @Q A qualifiedA();",
            "  }",
            "}");
    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "\033[1;31m[Dagger/MissingBinding]\033[0m @TestClass.Q TestClass.A cannot be "
                          + "provided without an @Provides-annotated method.")
                  .onSource(component)
                  .onLineContaining("interface AComponent");
            });
  }

  @Test public void constructorInjectionWithoutAnnotation() {
    Source component =
        CompilerTests.javaSource("test.TestClass",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "final class TestClass {",
        "  static class A {",
        "    A() {}",
        "  }",
        "",
        "  @Component()",
        "  interface AComponent {",
        "    A getA();",
        "  }",
        "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "TestClass.A cannot be provided without an @Inject constructor or an "
                          + "@Provides-annotated method.")
                  .onSource(component)
                  .onLineContaining("interface AComponent");
            });
  }

  @Test public void membersInjectWithoutProvision() {
    Source component =
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "final class TestClass {",
            "  static class A {",
            "    @Inject A() {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject A a;",
            "  }",
            "",
            "  @Component()",
            "  interface AComponent {",
            "    B getB();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "TestClass.B cannot be provided without an @Inject constructor or an "
                          + "@Provides-annotated method. This type supports members injection but "
                          + "cannot be implicitly provided.")
                  .onSource(component)
                  .onLineContaining("interface AComponent");
            });
  }

  @Test
  public void missingBindingWithSameKeyAsMembersInjectionMethod() {
    Source self =
        CompilerTests.javaSource(
            "test.Self",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class Self {",
            "  @Inject Provider<Self> selfProvider;",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.SelfComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SelfComponent {",
            "  void inject(Self target);",
            "}");

    CompilerTests.daggerCompiler(self, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Self cannot be provided without an @Inject constructor")
                  .onSource(component)
                  .onLineContaining("interface SelfComponent");
            });
  }

  @Test
  public void genericInjectClassWithWildcardDependencies() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Foo<? extends Number> foo();",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Foo<T> {",
            "  @Inject Foo(T t) {}",
            "}");
    CompilerTests.daggerCompiler(component, foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Foo<? extends Number> cannot be provided "
                      + "without an @Provides-annotated method");
            });
  }

  @Test
  public void requestSimilarKey_withDifferentVariance() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Foo<Bar<String>> getFooBarString();",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static Foo<? extends Bar<? extends String>> provideFooBarString() {",
            "    return null;",
            "  }",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "interface Foo<T> {}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "interface Bar<T> {}");
    CompilerTests.daggerCompiler(component, module, foo, bar)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "Foo<Bar<String>> cannot be provided without an @Provides-annotated method.",
                      "",
                      "    Foo<Bar<String>> is requested at",
                      "        [TestComponent] TestComponent.getFooBarString()",
                      "",
                      "Note: A similar binding is provided in the following other components:",
                      "    Foo<? extends Bar<? extends String>> is provided at:",
                      "        [TestComponent] TestModule.provideFooBarString()",
                      JVM_SUPPRESS_WILDCARDS_MESSAGE,
                      "",
                      "======================"));
            });
  }

  @Test
  public void requestSimilarKey_withRawTypeArgument() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Foo<Bar<Baz>> getFooBarBaz();",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static Foo<Bar<Baz<String>>> provideFooBarBazString() {",
            "    return null;",
            "  }",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "interface Foo<T> {}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "interface Bar<T> {}");
    Source baz =
        CompilerTests.javaSource(
            "test.Baz",
            "package test;",
            "",
            "interface Baz<T> {}");
    CompilerTests.daggerCompiler(component, module, foo, bar, baz)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "Foo<Bar<Baz>> cannot be provided without an @Provides-annotated method.",
                      "",
                      "    Foo<Bar<Baz>> is requested at",
                      "        [TestComponent] TestComponent.getFooBarBaz()",
                      "",
                      "Note: A similar binding is provided in the following other components:",
                      "    Foo<Bar<Baz<String>>> is provided at:",
                      "        [TestComponent] TestModule.provideFooBarBazString()",
                      JVM_SUPPRESS_WILDCARDS_MESSAGE,
                      "",
                      "======================"));
            });
  }

  @Test
  public void requestSimilarKey_complexRawType() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.List;",
            "import java.util.Map;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Map<List, Map<List, Map>> getRawComplex();",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.List;",
            "import java.util.Map;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static Map<List<String>, Map<List<String>, Map<String, String>>> provideComplex() {",
            "    return null;",
            "  }",
            "}");
    CompilerTests.daggerCompiler(component, module)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "Map<List,Map<List,Map>> cannot be provided without an @Provides-annotated "
                          + "method.",
                      "",
                      "    Map<List,Map<List,Map>> is requested at",
                      "        [TestComponent] TestComponent.getRawComplex()",
                      "",
                      "Note: A similar binding is provided in the following other components:",
                      "    Map<List<String>,Map<List<String>,Map<String,String>>> is provided at:",
                      "        [TestComponent] TestModule.provideComplex()",
                      JVM_SUPPRESS_WILDCARDS_MESSAGE,
                      "",
                      "======================"));
            });
  }

  @Test
  public void noSimilarKey_withRawTypeArgument() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Foo<Bar<Baz>> getFooBarBaz();",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static Foo<Bar<Bar>> provideFooBarBar() {",
            "    return null;",
            "  }",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "interface Foo<T> {}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "interface Bar<T> {}");
    Source baz =
        CompilerTests.javaSource(
            "test.Baz",
            "package test;",
            "",
            "interface Baz<T> {}");
    CompilerTests.daggerCompiler(component, module, foo, bar, baz)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "Foo<Bar<Baz>> cannot be provided without an @Provides-annotated method.",
                      "",
                      "    Foo<Bar<Baz>> is requested at",
                      "        [TestComponent] TestComponent.getFooBarBaz()",
                      "",
                      "======================"));
            });
  }

  @Test
  public void requestSimilarKey_differentQualifier() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Named;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  @Named(\"requested\") Foo getNamedRequestedFoo();",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Named;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static @Named(\"provided\") Foo provideNamedProvidedFoo() {",
            "    return null;",
            "  }",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "interface Foo {}");
    CompilerTests.daggerCompiler(component, module, foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "@Named(\"requested\") Foo cannot be provided without an @Provides-annotated "
                          + "method.",
                      "",
                      "    @Named(\"requested\") Foo is requested at",
                      "        [TestComponent] TestComponent.getNamedRequestedFoo()",
                      "",
                      "Note: A similar binding is provided in the following other components:",
                      "    @Named(\"provided\") Foo is provided at:",
                      "        [TestComponent] TestModule.provideNamedProvidedFoo()",
                      JVM_SUPPRESS_WILDCARDS_MESSAGE,
                      "",
                      "======================"));
            });
  }

  @Test
  public void requestSimilarKey_withoutQualifier() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Foo getFoo();",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Named;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static @Named(\"provided\") Foo provideNamedProvidedFoo() {",
            "    return null;",
            "  }",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "interface Foo {}");
    CompilerTests.daggerCompiler(component, module, foo)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "Foo cannot be provided without an @Provides-annotated method.",
                      "",
                      "    Foo is requested at",
                      "        [TestComponent] TestComponent.getFoo()",
                      "",
                      "Note: A similar binding is provided in the following other components:",
                      "    @Named(\"provided\") Foo is provided at:",
                      "        [TestComponent] TestModule.provideNamedProvidedFoo()",
                      JVM_SUPPRESS_WILDCARDS_MESSAGE,
                      "",
                      "======================"));
            });
  }

  @Test public void longChainOfDependencies() {
    Source component =
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Lazy;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "import javax.inject.Named;",
            "import javax.inject.Provider;",
            "",
            "final class TestClass {",
            "  interface A {}",
            "",
            "  static class B {",
            "    @Inject B(A a) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject B b;",
            "    @Inject C(X x) {}",
            "  }",
            "",
            "  interface D { }",
            "",
            "  static class DImpl implements D {",
            "    @Inject DImpl(C c, B b) {}",
            "  }",
            "",
            "  static class X {",
            "    @Inject X() {}",
            "  }",
            "",
            "  @Module",
            "  static class DModule {",
            "    @Provides @Named(\"slim shady\") D d(X x1, DImpl impl, X x2) { return impl; }",
            "  }",
            "",
            "  @Component(modules = { DModule.class })",
            "  interface AComponent {",
            "    @Named(\"slim shady\") D getFoo();",
            "    C injectC(C c);",
            "    Provider<C> cProvider();",
            "    Lazy<C> lazyC();",
            "    Provider<Lazy<C>> lazyCProvider();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "TestClass.A cannot be provided without an @Provides-annotated method.",
                      "",
                      "    TestClass.A is injected at",
                      "        [TestClass.AComponent] TestClass.B(a)",
                      "    TestClass.B is injected at",
                      "        [TestClass.AComponent] TestClass.C.b",
                      "    TestClass.C is injected at",
                      "        [TestClass.AComponent] TestClass.AComponent.injectC(TestClass.C)",
                      "The following other entry points also depend on it:",
                      "    TestClass.AComponent.getFoo()",
                      "    TestClass.AComponent.cProvider()",
                      "    TestClass.AComponent.lazyC()",
                      "    TestClass.AComponent.lazyCProvider()"))
                  .onSource(component)
                  .onLineContaining("interface AComponent");
            });
  }

  @Test
  public void bindsMethodAppearsInTrace() {
    Source component =
        CompilerTests.javaSource(
            "TestComponent",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestInterface testInterface();",
            "}");
    Source interfaceFile =
        CompilerTests.javaSource("TestInterface", "interface TestInterface {}");
    Source implementationFile =
        CompilerTests.javaSource(
            "TestImplementation",
            "import javax.inject.Inject;",
            "",
            "final class TestImplementation implements TestInterface {",
            "  @Inject TestImplementation(String missingBinding) {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "TestModule",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds abstract TestInterface bindTestInterface(TestImplementation implementation);",
            "}");

    CompilerTests.daggerCompiler(component, module, interfaceFile, implementationFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "String cannot be provided without an @Inject constructor or an "
                          + "@Provides-annotated method.",
                      "",
                      "    String is injected at",
                      "        [TestComponent] TestImplementation(missingBinding)",
                      "    TestImplementation is injected at",
                      "        [TestComponent] TestModule.bindTestInterface(implementation)",
                      "    TestInterface is requested at",
                      "        [TestComponent] TestComponent.testInterface()"))
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test public void resolvedParametersInDependencyTrace() {
    Source generic =
        CompilerTests.javaSource("test.Generic",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class Generic<T> {",
        "  @Inject Generic(T t) {}",
        "}");
    Source testClass =
        CompilerTests.javaSource("test.TestClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import java.util.List;",
        "",
        "final class TestClass {",
        "  @Inject TestClass(List list) {}",
        "}");
    Source usesTest =
        CompilerTests.javaSource("test.UsesTest",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class UsesTest {",
        "  @Inject UsesTest(Generic<TestClass> genericTestClass) {}",
        "}");
    Source component =
        CompilerTests.javaSource("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  UsesTest usesTest();",
        "}");

    CompilerTests.daggerCompiler(generic, testClass, usesTest, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "List cannot be provided without an @Provides-annotated method.",
                      "",
                      "    List is injected at",
                      "        [TestComponent] TestClass(list)",
                      "    TestClass is injected at",
                      "        [TestComponent] Generic(t)",
                      "    Generic<TestClass> is injected at",
                      "        [TestComponent] UsesTest(genericTestClass)",
                      "    UsesTest is requested at",
                      "        [TestComponent] TestComponent.usesTest()"));
            });
  }

  @Test public void resolvedVariablesInDependencyTrace() {
    Source generic =
        CompilerTests.javaSource(
            "test.Generic",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class Generic<T> {",
            "  @Inject T t;",
            "  @Inject Generic() {}",
            "}");
    Source testClass =
        CompilerTests.javaSource(
            "test.TestClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import java.util.List;",
            "",
            "final class TestClass {",
            "  @Inject TestClass(List list) {}",
            "}");
    Source usesTest =
        CompilerTests.javaSource(
            "test.UsesTest",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class UsesTest {",
            "  @Inject UsesTest(Generic<TestClass> genericTestClass) {}",
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
            "  UsesTest usesTest();",
            "}");

    CompilerTests.daggerCompiler(generic, testClass, usesTest, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "List cannot be provided without an @Provides-annotated method.",
                      "",
                      "    List is injected at",
                      "        [TestComponent] TestClass(list)",
                      "    TestClass is injected at",
                      "        [TestComponent] Generic.t",
                      "    Generic<TestClass> is injected at",
                      "        [TestComponent] UsesTest(genericTestClass)",
                      "    UsesTest is requested at",
                      "        [TestComponent] TestComponent.usesTest()"));
            });
  }

  @Test
  public void bindingUsedOnlyInSubcomponentDependsOnBindingOnlyInSubcomponent() {
    Source parent =
        CompilerTests.javaSource(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    Source parentModule =
        CompilerTests.javaSource(
            "ParentModule",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides static Object needsString(String string) {",
            "    return \"needs string: \" + string;",
            "  }",
            "}");
    Source child =
        CompilerTests.javaSource(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  String string();",
            "  Object needsString();",
            "}");
    Source childModule =
        CompilerTests.javaSource(
            "ChildModule",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides static String string() {",
            "    return \"child string\";",
            "  }",
            "}");

    CompilerTests.daggerCompiler(parent, parentModule, child, childModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("String cannot be provided");
              subject.hasErrorContaining("[Child] Child.needsString()")
                  .onSource(parent)
                  .onLineContaining("interface Parent");
            });
  }

  @Test
  public void multibindingContributionBetweenAncestorComponentAndEntrypointComponent() {
    Source parent =
        CompilerTests.javaSource(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    Source child =
        CompilerTests.javaSource(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Grandchild grandchild();",
            "}");
    Source grandchild =
        CompilerTests.javaSource(
            "Grandchild",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Grandchild {",
            "  Object object();",
            "}");

    Source parentModule =
        CompilerTests.javaSource(
            "ParentModule",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides static Object dependsOnSet(Set<String> strings) {",
            "    return \"needs strings: \" + strings;",
            "  }",
            "",
            "  @Provides @IntoSet static String contributesToSet() {",
            "    return \"parent string\";",
            "  }",
            "",
            "  @Provides int missingDependency(double dub) {",
            "    return 4;",
            "  }",
            "}");
    Source childModule =
        CompilerTests.javaSource(
            "ChildModule",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides @IntoSet static String contributesToSet(int i) {",
            "    return \"\" + i;",
            "  }",
            "}");
    CompilerTests.daggerCompiler(parent, parentModule, child, childModule, grandchild)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // TODO(b/243720787): Replace with CompilationResultSubject#hasErrorContainingMatch()
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "Double cannot be provided without an @Inject constructor or an "
                          + "@Provides-annotated method.",
                      "",
                      "    Double is injected at",
                      "        [Parent] ParentModule.missingDependency(dub)",
                      "    Integer is injected at",
                      "        [Child] ChildModule.contributesToSet(i)",
                      "    Set<String> is injected at",
                      "        [Child] ParentModule.dependsOnSet(strings)",
                      "    Object is requested at",
                      "        [Grandchild] Grandchild.object() [Parent → Child → Grandchild]"))
                  .onSource(parent)
                  .onLineContaining("interface Parent");
            });
  }

  @Test
  public void manyDependencies() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Object object();",
            "  String string();",
            "}");
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
            "  @Binds abstract Object object(NotBound notBound);",
            "",
            "  @Provides static String string(NotBound notBound, Object object) {",
            "    return notBound.toString();",
            "  }",
            "}");
    Source notBound =
        CompilerTests.javaSource(
            "test.NotBound", //
            "package test;",
            "",
            "interface NotBound {}");
    CompilerTests.daggerCompiler(component, module, notBound)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "NotBound cannot be provided without an @Provides-annotated method.",
                      "",
                      "    NotBound is injected at",
                      "        [TestComponent] TestModule.object(notBound)",
                      "    Object is requested at",
                      "        [TestComponent] TestComponent.object()",
                      "It is also requested at:",
                      "    TestModule.string(notBound, …)",
                      "The following other entry points also depend on it:",
                      "    TestComponent.string()"))
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void tooManyRequests() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Foo {",
            "  @Inject Foo(",
            "      String one,",
            "      String two,",
            "      String three,",
            "      String four,",
            "      String five,",
            "      String six,",
            "      String seven,",
            "      String eight,",
            "      String nine,",
            "      String ten,",
            "      String eleven,",
            "      String twelve,",
            "      String thirteen) {",
            "  }",
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
            "  String string();",
            "  Foo foo();",
            "}");

    CompilerTests.daggerCompiler(foo, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "String cannot be provided without an @Inject constructor or an "
                          + "@Provides-annotated method.",
                      "",
                      "    String is requested at",
                      "        [TestComponent] TestComponent.string()",
                      "It is also requested at:",
                      "    Foo(one, …)",
                      "    Foo(…, two, …)",
                      "    Foo(…, three, …)",
                      "    Foo(…, four, …)",
                      "    Foo(…, five, …)",
                      "    Foo(…, six, …)",
                      "    Foo(…, seven, …)",
                      "    Foo(…, eight, …)",
                      "    Foo(…, nine, …)",
                      "    Foo(…, ten, …)",
                      "    and 3 others",
                      "The following other entry points also depend on it:",
                      "    TestComponent.foo()"))
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void tooManyEntryPoints() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  String string1();",
            "  String string2();",
            "  String string3();",
            "  String string4();",
            "  String string5();",
            "  String string6();",
            "  String string7();",
            "  String string8();",
            "  String string9();",
            "  String string10();",
            "  String string11();",
            "  String string12();",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "String cannot be provided without an @Inject constructor or an "
                          + "@Provides-annotated method.",
                      "",
                      "    String is requested at",
                      "        [TestComponent] TestComponent.string1()",
                      "The following other entry points also depend on it:",
                      "    TestComponent.string2()",
                      "    TestComponent.string3()",
                      "    TestComponent.string4()",
                      "    TestComponent.string5()",
                      "    TestComponent.string6()",
                      "    TestComponent.string7()",
                      "    TestComponent.string8()",
                      "    TestComponent.string9()",
                      "    TestComponent.string10()",
                      "    TestComponent.string11()",
                      "    and 1 other"))
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void missingBindingInAllComponentsAndEntryPoints() {
    Source parent =
        CompilerTests.javaSource(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Foo foo();",
            "  Bar bar();",
            "  Child child();",
            "}");
    Source child =
        CompilerTests.javaSource(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  Foo foo();",
            "  Baz baz();",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "Foo",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "Bar",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(Baz baz) {}",
            "}");
    Source baz =
        CompilerTests.javaSource("Baz", "class Baz {}");

    CompilerTests.daggerCompiler(parent, child, foo, bar, baz)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "Baz cannot be provided without an @Inject constructor or an "
                          + "@Provides-annotated method.",
                      "",
                      "    Baz is injected at",
                      "        [Parent] Bar(baz)",
                      "    Bar is requested at",
                      "        [Parent] Parent.bar()",
                      "The following other entry points also depend on it:",
                      "    Parent.foo()",
                      "    Child.foo() [Parent → Child]",
                      "    Child.baz() [Parent → Child]"))
                  .onSource(parent)
                  .onLineContaining("interface Parent");
            });
  }

  // Regression test for b/147423208 where if the same subcomponent was used
  // in two different parts of the hierarchy and only one side had a missing binding
  // incorrect caching during binding graph conversion might cause validation to pass
  // incorrectly.
  @Test
  public void sameSubcomponentUsedInDifferentHierarchies() {
    Source parent =
        CompilerTests.javaSource("test.Parent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface Parent {",
        "  Child1 getChild1();",
        "  Child2 getChild2();",
        "}");
    Source child1 =
        CompilerTests.javaSource("test.Child1",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = LongModule.class)",
        "interface Child1 {",
        "  RepeatedSub getSub();",
        "}");
    Source child2 =
        CompilerTests.javaSource("test.Child2",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface Child2 {",
        "  RepeatedSub getSub();",
        "}");
    Source repeatedSub =
        CompilerTests.javaSource("test.RepeatedSub",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface RepeatedSub {",
        "  Foo getFoo();",
        "}");
    Source injectable =
        CompilerTests.javaSource("test.Foo",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class Foo {",
        "  @Inject Foo(Long value) {}",
        "}");
    Source module =
        CompilerTests.javaSource("test.LongModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "interface LongModule {",
        "  @Provides static Long provideLong() {",
        "    return 0L;",
        "  }",
        "}");
    CompilerTests.daggerCompiler(parent, child1, child2, repeatedSub, injectable, module)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Long cannot be provided without an @Inject constructor")
                  .onSource(parent)
                  .onLineContaining("interface Parent");
            });
  }

  @Test
  public void requestUnusedBindingInDifferentComponent() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child1 getChild1();",
            "  Child2 getChild2();",
            "}");
    Source child1 =
        CompilerTests.javaSource(
            "test.Child1",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child1 {",
            "  Object getObject();",
            "}");
    Source child2 =
        CompilerTests.javaSource(
            "test.Child2",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child2Module.class)",
            "interface Child2 {}");
    Source child2Module =
        CompilerTests.javaSource(
            "test.Child2Module",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface Child2Module {",
            "  @Provides",
            "  static Object provideObject() {",
            "    return new Object();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(parent, child1, child2, child2Module)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "Object cannot be provided without an @Inject constructor or an "
                          + "@Provides-annotated method.",
                      "",
                      "    Object is requested at",
                      "        [Child1] Child1.getObject() [Parent → Child1]",
                      "",
                      "Note: Object is provided in the following other components:",
                      "    [Child2] Child2Module.provideObject()",
                      "",
                      "======================"));
            });
  }

  @Test
  public void sameSubcomponentUsedInDifferentHierarchiesMissingBindingFromOneSide() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child1 getChild1();",
            "  Child2 getChild2();",
            "}");
    Source child1 =
        CompilerTests.javaSource(
            "test.Child1",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child1Module.class)",
            "interface Child1 {",
            "  RepeatedSub getSub();",
            "}");
    Source child2 =
        CompilerTests.javaSource(
            "test.Child2",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child2Module.class)",
            "interface Child2 {",
            "  RepeatedSub getSub();",
            "}");
    Source repeatedSub =
        CompilerTests.javaSource(
            "test.RepeatedSub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = RepeatedSubModule.class)",
            "interface RepeatedSub {",
            "  Object getObject();",
            "}");
    Source child1Module =
        CompilerTests.javaSource(
            "test.Child1Module",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "import dagger.multibindings.Multibinds;",
            "",
            "@Module",
            "interface Child1Module {",
            "  @Multibinds Set<Integer> multibindIntegerSet();",
            "}");
    Source child2Module =
        CompilerTests.javaSource(
            "test.Child2Module",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "import dagger.multibindings.Multibinds;",
            "",
            "@Module",
            "interface Child2Module {",
            "  @Multibinds Set<Integer> multibindIntegerSet();",
            "",
            "  @Provides",
            "  static Object provideObject(Set<Integer> intSet) {",
            "    return new Object();",
            "  }",
            "}");
    Source repeatedSubModule =
        CompilerTests.javaSource(
            "test.RepeatedSubModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "import dagger.multibindings.Multibinds;",
            "",
            "@Module",
            "interface RepeatedSubModule {",
            "  @Provides",
            "  @IntoSet",
            "  static Integer provideInt() {",
            "    return 9;",
            "  }",
            "}");

    CompilerTests.daggerCompiler(
            parent, child1, child2, repeatedSub, child1Module, child2Module, repeatedSubModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "Object cannot be provided without an @Inject constructor or an "
                          + "@Provides-annotated method.",
                      "",
                      "    Object is requested at",
                      "        [RepeatedSub] RepeatedSub.getObject() "
                          + "[Parent → Child1 → RepeatedSub]",
                      "",
                      "Note: Object is provided in the following other components:",
                      "    [Child2] Child2Module.provideObject(…)",
                      "",
                      "======================"));
            });
  }

  @Test
  public void differentComponentPkgSameSimpleNameMissingBinding() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child1 getChild1();",
            "  Child2 getChild2();",
            "}");
    Source child1 =
        CompilerTests.javaSource(
            "test.Child1",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child1Module.class)",
            "interface Child1 {",
            "  foo.Sub getSub();",
            "}");
    Source child2 =
        CompilerTests.javaSource(
            "test.Child2",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child2Module.class)",
            "interface Child2 {",
            "  bar.Sub getSub();",
            "}");
    Source sub1 =
        CompilerTests.javaSource(
            "foo.Sub",
            "package foo;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = test.RepeatedSubModule.class)",
            "public interface Sub {",
            "  Object getObject();",
            "}");
    Source sub2 =
        CompilerTests.javaSource(
            "bar.Sub",
            "package bar;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = test.RepeatedSubModule.class)",
            "public interface Sub {",
            "  Object getObject();",
            "}");
    Source child1Module =
        CompilerTests.javaSource(
            "test.Child1Module",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "import dagger.multibindings.Multibinds;",
            "",
            "@Module",
            "interface Child1Module {",
            "  @Multibinds Set<Integer> multibindIntegerSet();",
            "}");
    Source child2Module =
        CompilerTests.javaSource(
            "test.Child2Module",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "import dagger.multibindings.Multibinds;",
            "",
            "@Module",
            "interface Child2Module {",
            "  @Multibinds Set<Integer> multibindIntegerSet();",
            "",
            "  @Provides",
            "  static Object provideObject(Set<Integer> intSet) {",
            "    return new Object();",
            "  }",
            "}");
    Source repeatedSubModule =
        CompilerTests.javaSource(
            "test.RepeatedSubModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "import dagger.multibindings.Multibinds;",
            "",
            "@Module",
            "public interface RepeatedSubModule {",
            "  @Provides",
            "  @IntoSet",
            "  static Integer provideInt() {",
            "    return 9;",
            "  }",
            "}");

    CompilerTests.daggerCompiler(
            parent, child1, child2, sub1, sub2, child1Module, child2Module, repeatedSubModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "Object cannot be provided without an @Inject constructor or an "
                          + "@Provides-annotated method.",
                      "",
                      "    Object is requested at",
                      "        [Sub] Sub.getObject() [Parent → Child1 → Sub]",
                      "",
                      "Note: Object is provided in the following other components:",
                      "    [Child2] Child2Module.provideObject(…)",
                      "",
                      "======================"));
            });
  }

  @Test
  public void requestWildcardTypeWithNonWildcardTypeBindingProvided_failsWithMissingBinding() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface MyComponent {",
            "  Foo getFoo();",
            "  Child getChild();",
            "}");
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {}");
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "import java.util.HashSet;",
            "",
            "@Module",
            "interface ChildModule {",
            "  @Provides",
            "  static Set<? extends Bar> provideBar() {",
            "    return new HashSet<Bar>();",
            "  }",
            "}");
    Source fooSrc =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import java.util.Set;",
            "",
            "class Foo {",
            "  @Inject Foo(Set<? extends Bar> bar) {}",
            "}");
    Source barSrc =
        CompilerTests.javaSource("test.Bar", "package test;", "", "public interface Bar {}");
    Source moduleSrc =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import java.util.Set;",
            "import java.util.HashSet;",
            "",
            "@Module",
            "public class TestModule {",
            "   @ElementsIntoSet",
            "   @Provides",
            "   Set<Bar> provideBars() {",
            "     return new HashSet<Bar>();",
            "   }",
            "}");

    CompilerTests.daggerCompiler(component, child, childModule, fooSrc, barSrc, moduleSrc)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      String.join(
                          "\n",
                          "Set<? extends Bar> cannot be provided without an @Provides-annotated "
                              + "method.",
                          "",
                          "    Set<? extends Bar> is injected at",
                          "        [MyComponent] Foo(bar)",
                          "    Foo is requested at",
                          "        [MyComponent] MyComponent.getFoo()",
                          "",
                          "Note: Set<? extends Bar> is provided in the following other components:",
                          "    [Child] ChildModule.provideBar()",
                          "",
                          "Note: A similar binding is provided in the following other components:",
                          "    Set<Bar> is provided at:",
                          "        [MyComponent] Dagger-generated binding for Set<Bar>",
                          JVM_SUPPRESS_WILDCARDS_MESSAGE,
                          "",
                          "======================"))
                  .onSource(component)
                  .onLineContaining("interface MyComponent");
            });
  }

  @Test
  public void
      injectParameterDoesNotSuppressWildcardGeneration_conflictsWithNonWildcardTypeBinding() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface MyComponent {",
            "  Foo getFoo();",
            "}");
    Source fooSrc =
        CompilerTests.kotlinSource(
            "Foo.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class Foo @Inject constructor(val bar: Set<Bar>) {}");
    Source barSrc =
        CompilerTests.javaSource("test.Bar", "package test;", "", "public interface Bar {}");
    Source moduleSrc =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import java.util.Set;",
            "import java.util.HashSet;",
            "",
            "@Module",
            "public class TestModule {",
            "   @ElementsIntoSet",
            "   @Provides",
            "   Set<Bar> provideBars() {",
            "     return new HashSet<Bar>();",
            "   }",
            "}");

    CompilerTests.daggerCompiler(component, fooSrc, barSrc, moduleSrc)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      String.join(
                          "\n",
                          "Set<? extends Bar> cannot be provided without an @Provides-annotated "
                              + "method.",
                          "",
                          "    Set<? extends Bar> is injected at",
                          "        [MyComponent] Foo(bar)",
                          "    Foo is requested at",
                          "        [MyComponent] MyComponent.getFoo()",
                          "",
                          "Note: A similar binding is provided in the following other components:",
                          "    Set<Bar> is provided at:",
                          "        [MyComponent] Dagger-generated binding for Set<Bar>",
                          JVM_SUPPRESS_WILDCARDS_MESSAGE,
                          "",
                          "======================"))
                  .onSource(component)
                  .onLineContaining("interface MyComponent");
            });
  }

  @Test
  public void injectWildcardTypeWithNonWildcardTypeBindingProvided_failsWithMissingBinding() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface MyComponent {",
            "  Foo getFoo();",
            "}");
    Source fooSrc =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import java.util.Set;",
            "",
            "class Foo {",
            "  @Inject Set<? extends Bar> bar;",
            "  @Inject Foo() {}",
            "}");
    Source barSrc =
        CompilerTests.javaSource("test.Bar", "package test;", "", "public interface Bar {}");
    Source moduleSrc =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import java.util.Set;",
            "import java.util.HashSet;",
            "",
            "@Module",
            "public class TestModule {",
            "   @ElementsIntoSet",
            "   @Provides",
            "   Set<Bar> provideBars() {",
            "     return new HashSet<Bar>();",
            "   }",
            "}");

    CompilerTests.daggerCompiler(component, fooSrc, barSrc, moduleSrc)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      String.join(
                          "\n",
                          "Set<? extends Bar> cannot be provided without an @Provides-annotated "
                              + "method.",
                          "",
                          "    Set<? extends Bar> is injected at",
                          "        [MyComponent] Foo.bar",
                          "    Foo is requested at",
                          "        [MyComponent] MyComponent.getFoo()",
                          "",
                          "Note: A similar binding is provided in the following other components:",
                          "    Set<Bar> is provided at:",
                          "        [MyComponent] Dagger-generated binding for Set<Bar>",
                          JVM_SUPPRESS_WILDCARDS_MESSAGE,
                          "",
                          "======================"))
                  .onSource(component)
                  .onLineContaining("interface MyComponent");
            });
  }

  @Test
  public void requestFinalClassWithWildcardAnnotation_missingWildcardTypeBinding() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface MyComponent {",
            "  Foo getFoo();",
            "}");
    Source fooSrc =
        CompilerTests.kotlinSource(
            "test.Foo.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class Foo @Inject constructor(val bar: List<Bar>) {}");
    Source barSrc =
        CompilerTests.javaSource("test.Bar", "package test;", "", "public final class Bar {}");
    Source moduleSrc =
        CompilerTests.kotlinSource(
            "test.TestModule.kt",
            "package test",
            "",
            "import dagger.Module",
            "import dagger.Provides",
            "import dagger.multibindings.ElementsIntoSet",
            "",
            "@Module",
            "object TestModule {",
            "   @Provides fun provideBars(): List<@JvmWildcard Bar> = setOf()",
            "}");

    CompilerTests.daggerCompiler(component, fooSrc, barSrc, moduleSrc)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      String.join(
                          "\n",
                          "List<Bar> cannot be provided without an @Provides-annotated method.",
                          "",
                          "    List<Bar> is injected at",
                          "        [MyComponent] Foo(bar)",
                          "    Foo is requested at",
                          "        [MyComponent] MyComponent.getFoo()",
                          "",
                          "Note: A similar binding is provided in the following other components:",
                          "    List<? extends Bar> is provided at:",
                          "        [MyComponent] TestModule.provideBars()",
                          JVM_SUPPRESS_WILDCARDS_MESSAGE,
                          "",
                          "======================"))
                  .onSource(component)
                  .onLineContaining("interface MyComponent");
            });
  }

  @Test
  public void multipleTypeParameters_notSuppressWildcardType_failsWithMissingBinding() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface MyComponent {",
            "  Foo getFoo();",
            "}");
    Source fooSrc =
        CompilerTests.kotlinSource(
            "test.Foo.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class Foo @Inject constructor(val bar: Bar<Baz, Baz, Set<Baz>>) {}");
    Source barSrc =
        CompilerTests.kotlinSource(
            "test.Bar.kt", "package test", "", "class Bar<out T1, T2, T3> {}");

    Source bazSrc =
        CompilerTests.javaSource("test.Baz", "package test;", "", "public interface Baz {}");

    Source moduleSrc =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "",
            "@Module",
            "public class TestModule {",
            "   @Provides",
            "   Bar<Baz, Baz, Set<Baz>> provideBar() {",
            "     return new Bar<Baz, Baz, Set<Baz>>();",
            "   }",
            "}");

    CompilerTests.daggerCompiler(component, fooSrc, barSrc, bazSrc, moduleSrc)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      String.join(
                          "\n",
                          // TODO(b/324325095): Align KSP and KAPT error message.
                          CompilerTests.backend(subject) == XProcessingEnv.Backend.KSP
                              ? "Bar<? extends Baz,Baz,Set<Baz>> cannot be provided without an "
                                  + "@Inject constructor or an @Provides-annotated method."
                              : "Bar<? extends Baz,Baz,Set<Baz>> cannot be provided without an "
                                  + "@Provides-annotated method.",
                          "",
                          "    Bar<? extends Baz,Baz,Set<Baz>> is injected at",
                          "        [MyComponent] Foo(bar)",
                          "    Foo is requested at",
                          "        [MyComponent] MyComponent.getFoo()",
                          "",
                          "Note: A similar binding is provided in the following other components:",
                          "    Bar<Baz,Baz,Set<Baz>> is provided at:",
                          "        [MyComponent] TestModule.provideBar()",
                          JVM_SUPPRESS_WILDCARDS_MESSAGE,
                          "",
                          "======================"))
                  .onSource(component)
                  .onLineContaining("interface MyComponent");
            });
  }

  @Test
  public void missingBindingWithoutQualifier_warnAboutSimilarTypeWithQualifierExists() {
    Source qualifierSrc =
        CompilerTests.javaSource(
            "test.MyQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier",
            "@interface MyQualifier {}");
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface MyComponent {",
            "  Foo getFoo();",
            "}");
    Source fooSrc =
        CompilerTests.kotlinSource(
            "Foo.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class Foo @Inject constructor(val bar: Set<Bar>) {}");
    Source barSrc =
        CompilerTests.javaSource("test.Bar", "package test;", "", "public interface Bar {}");
    Source moduleSrc =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import java.util.Set;",
            "import java.util.HashSet;",
            "",
            "@Module",
            "public class TestModule {",
            "   @ElementsIntoSet",
            "   @Provides",
            "   @MyQualifier",
            "   Set<Bar> provideBars() {",
            "     return new HashSet<Bar>();",
            "   }",
            "}");

    CompilerTests.daggerCompiler(qualifierSrc, component, fooSrc, barSrc, moduleSrc)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("MissingBinding");
              List<DiagnosticMessage> diagnostics =
                  subject.getCompilationResult().getDiagnostics().get(Diagnostic.Kind.ERROR);
              assertThat(diagnostics).hasSize(1);
              assertThat(diagnostics.get(0).getMsg())
                  .doesNotContain("bindings with similar types exists in the graph");
            });
  }

  @Test
  public void missingWildcardTypeWithObjectBound_providedRawType_warnAboutSimilarTypeExists() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface MyComponent {",
            "  Foo getFoo();",
            "}");
    Source fooSrc =
        CompilerTests.kotlinSource(
            "test.Foo.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class Foo @Inject constructor(val bar: Bar<Object>) {}");
    Source barSrc =
        CompilerTests.kotlinSource("test.Bar.kt", "package test", "", "class Bar<out T1> {}");
    Source moduleSrc =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "",
            "@Module",
            "public class TestModule {",
            "   @Provides",
            "   Bar provideBar() {",
            "     return new Bar<Object>();",
            "   }",
            "}");

    CompilerTests.daggerCompiler(component, fooSrc, barSrc, moduleSrc)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      // TODO(b/324325095): Align KSP and KAPT error message.
                      CompilerTests.backend(subject) == XProcessingEnv.Backend.KSP
                          ? "Bar<?> cannot be provided without an @Inject constructor or an "
                              + "@Provides-annotated method."
                          : "Bar<?> cannot be provided without an @Provides-annotated method.",
                      "",
                      "    Bar<?> is injected at",
                      "        [MyComponent] Foo(bar)",
                      "    Foo is requested at",
                      "        [MyComponent] MyComponent.getFoo()",
                      "",
                      "Note: A similar binding is provided in the following other components:",
                      "    Bar is provided at:",
                      "        [MyComponent] TestModule.provideBar()",
                      JVM_SUPPRESS_WILDCARDS_MESSAGE,
                      "",
                      "======================"));
            });
  }

  @Test
  public void missingWildcardType_providedRawType_warnAboutSimilarTypeExists() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface MyComponent {",
            "  Foo getFoo();",
            "}");
    Source fooSrc =
        CompilerTests.kotlinSource(
            "test.Foo.kt",
            "package test",
            "",
            "import javax.inject.Inject",
            "",
            "class Foo @Inject constructor(val bar: Bar<String>) {}");
    Source barSrc =
        CompilerTests.kotlinSource("test.Bar.kt", "package test", "", "class Bar<out T1> {}");
    Source moduleSrc =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "",
            "@Module",
            "public class TestModule {",
            "   @Provides",
            "   Bar provideBar() {",
            "     return new Bar<Object>();",
            "   }",
            "}");

    CompilerTests.daggerCompiler(component, fooSrc, barSrc, moduleSrc)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("MissingBinding");
              List<DiagnosticMessage> diagnostics =
                  subject.getCompilationResult().getDiagnostics().get(Diagnostic.Kind.ERROR);
              assertThat(diagnostics).hasSize(1);
              assertThat(diagnostics.get(0).getMsg())
                  .doesNotContain("bindings with similar types exists in the graph");
            });
  }

  // Regression test for b/367426609
  @Test
  public void failsWithMissingBindingInGrandchild() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Grandchild grandchild();",
            "}");
    Source grandchild =
        CompilerTests.javaSource(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules=GrandchildModule.class)",
            "interface Grandchild {",
            // Note: it's important that Qux is first to reproduce the error in b/367426609.
            "  Qux getQux();",
            "  Foo getFoo();",
            "}");
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "interface ParentModule {",
            "  @BindsOptionalOf",
            "  String optionalString();",
            "",
            // depend on an @BindsOptionalOf to force re-resolution in subcomponents.
            "  @Provides",
            "  static Foo provideFoo(Optional<String> str, Qux qux) { return null; }",
            "}");
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface ChildModule {",
            "  @Provides",
            "  static Qux provideQux() { return null; }",
            "}");
    Source grandchildModule =
        CompilerTests.javaSource(
            "test.GrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface GrandchildModule {",
            "  @Provides",
            "  static String provideString() { return null; }",
            "}");
    Source foo =
        CompilerTests.javaSource( // force one-string-per-line format
            "test.Foo",
            "package test;",
            "",
            "interface Foo {}");
    Source bar =
        CompilerTests.javaSource( // force one-string-per-line format
            "test.Bar",
            "package test;",
            "",
            "interface Bar {}");
    Source qux =
        CompilerTests.javaSource( // force one-string-per-line format
            "test.Qux",
            "package test;",
            "",
            "interface Qux {}");

    CompilerTests.daggerCompiler(
            parent, child, grandchild, parentModule, childModule, grandchildModule, foo, bar, qux)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  // Regression test for b/367426609
  @Test
  public void failsWithMissingBindingInGrandchild_dependencyTracePresent() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Grandchild grandchild();",
            "}");
    Source grandchild =
        CompilerTests.javaSource(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules=GrandchildModule.class)",
            "interface Grandchild {",
            "  Foo getFoo();",
            "}");
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "interface ParentModule {",
            "  @BindsOptionalOf",
            "  String optionalString();",
            "",
            // depend on an @BindsOptionalOf to force re-resolution in subcomponents.
            "  @Provides",
            "  static Foo provideFoo(Optional<String> str, Qux qux) { return null; }",
            "}");
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface ChildModule {",
            "  @Provides",
            "  static Qux provideQux() { return null; }",
            "}");
    Source grandchildModule =
        CompilerTests.javaSource(
            "test.GrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface GrandchildModule {",
            "  @Provides",
            "  static String provideString() { return null; }",
            "}");
    Source foo =
        CompilerTests.javaSource( // force one-string-per-line format
            "test.Foo",
            "package test;",
            "",
            "interface Foo {}");
    Source bar =
        CompilerTests.javaSource( // force one-string-per-line format
            "test.Bar",
            "package test;",
            "",
            "interface Bar {}");
    Source qux =
        CompilerTests.javaSource( // force one-string-per-line format
            "test.Qux",
            "package test;",
            "",
            "interface Qux {}");

    CompilerTests.daggerCompiler(
            parent, child, grandchild, parentModule, childModule, grandchildModule, foo, bar, qux)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }
}
