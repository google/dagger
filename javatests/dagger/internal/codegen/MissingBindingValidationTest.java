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

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MissingBindingValidationTest {
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
                  "TestClass.A cannot be provided without an @Provides-annotated method.");
              subject.hasErrorContaining("    TestClass.A is injected at");
              subject.hasErrorContaining("        TestClass.B(a)");
              subject.hasErrorContaining("    TestClass.B is injected at");
              subject.hasErrorContaining("        TestClass.C.b");
              subject.hasErrorContaining("    TestClass.C is injected at");
              subject.hasErrorContaining("        TestClass.AComponent.injectC(TestClass.C)");
              subject.hasErrorContaining("The following other entry points also depend on it:");
              subject.hasErrorContaining("    TestClass.AComponent.getFoo()");
              subject.hasErrorContaining("    TestClass.AComponent.cProvider()");
              subject.hasErrorContaining("    TestClass.AComponent.lazyC()");
              subject.hasErrorContaining("    TestClass.AComponent.lazyCProvider()")
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
                  "String cannot be provided without an @Inject constructor or an "
                      + "@Provides-annotated method.");
              subject.hasErrorContaining("    String is injected at");
              subject.hasErrorContaining("        TestImplementation(missingBinding)");
              subject.hasErrorContaining("    TestImplementation is injected at");
              subject.hasErrorContaining("        TestModule.bindTestInterface(implementation)");
              subject.hasErrorContaining("    TestInterface is requested at");
              subject.hasErrorContaining("        TestComponent.testInterface()")
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
                  "List cannot be provided without an @Provides-annotated method.");
              subject.hasErrorContaining("    List is injected at");
              subject.hasErrorContaining("        TestClass(list)");
              subject.hasErrorContaining("    TestClass is injected at");
              subject.hasErrorContaining("        Generic(t)");
              subject.hasErrorContaining("    Generic<TestClass> is injected at");
              subject.hasErrorContaining("        UsesTest(genericTestClass)");
              subject.hasErrorContaining("    UsesTest is requested at");
              subject.hasErrorContaining("        TestComponent.usesTest()");
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
                  "List cannot be provided without an @Provides-annotated method.");
              subject.hasErrorContaining("    List is injected at");
              subject.hasErrorContaining("        TestClass(list)");
              subject.hasErrorContaining("    TestClass is injected at");
              subject.hasErrorContaining("        Generic.t");
              subject.hasErrorContaining("    Generic<TestClass> is injected at");
              subject.hasErrorContaining("        UsesTest(genericTestClass)");
              subject.hasErrorContaining("    UsesTest is requested at");
              subject.hasErrorContaining("        TestComponent.usesTest()");
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
                  "Double cannot be provided without an @Inject constructor or an "
                      + "@Provides-annotated method.");
              subject.hasErrorContaining("Double is injected at");
              subject.hasErrorContaining("    ParentModule.missingDependency(dub)");
              subject.hasErrorContaining("Integer is injected at");
              subject.hasErrorContaining("    ChildModule.contributesToSet(i)");
              subject.hasErrorContaining("Set<String> is injected at");
              subject.hasErrorContaining("    ParentModule.dependsOnSet(strings)");
              subject.hasErrorContaining("Object is requested at");
              subject.hasErrorContaining("    Grandchild.object() [Parent → Child → Grandchild]")
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
                  "\033[1;31m[Dagger/MissingBinding]\033[0m "
                      + "NotBound cannot be provided without an @Provides-annotated method.");
              subject.hasErrorContaining("    NotBound is injected at");
              subject.hasErrorContaining("        TestModule.object(notBound)");
              subject.hasErrorContaining("    Object is requested at");
              subject.hasErrorContaining("        TestComponent.object()");
              subject.hasErrorContaining("It is also requested at:");
              subject.hasErrorContaining("    TestModule.string(notBound, …)");
              subject.hasErrorContaining("The following other entry points also depend on it:");
              subject.hasErrorContaining("    TestComponent.string()")
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
                  "\033[1;31m[Dagger/MissingBinding]\033[0m String cannot be provided without an "
                      + "@Inject constructor or an @Provides-annotated method.");
              subject.hasErrorContaining("    String is requested at");
              subject.hasErrorContaining("        TestComponent.string()");
              subject.hasErrorContaining("It is also requested at:");
              subject.hasErrorContaining("    Foo(one, …)");
              subject.hasErrorContaining("    Foo(…, two, …)");
              subject.hasErrorContaining("    Foo(…, three, …)");
              subject.hasErrorContaining("    Foo(…, four, …)");
              subject.hasErrorContaining("    Foo(…, five, …)");
              subject.hasErrorContaining("    Foo(…, six, …)");
              subject.hasErrorContaining("    Foo(…, seven, …)");
              subject.hasErrorContaining("    Foo(…, eight, …)");
              subject.hasErrorContaining("    Foo(…, nine, …)");
              subject.hasErrorContaining("    Foo(…, ten, …)");
              subject.hasErrorContaining("    and 3 others")
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
                  "\033[1;31m[Dagger/MissingBinding]\033[0m String cannot be provided without an "
                      + "@Inject constructor or an @Provides-annotated method.");
              subject.hasErrorContaining("    String is requested at");
              subject.hasErrorContaining("        TestComponent.string1()");
              subject.hasErrorContaining("The following other entry points also depend on it:");
              subject.hasErrorContaining("    TestComponent.string2()");
              subject.hasErrorContaining("    TestComponent.string3()");
              subject.hasErrorContaining("    TestComponent.string4()");
              subject.hasErrorContaining("    TestComponent.string5()");
              subject.hasErrorContaining("    TestComponent.string6()");
              subject.hasErrorContaining("    TestComponent.string7()");
              subject.hasErrorContaining("    TestComponent.string8()");
              subject.hasErrorContaining("    TestComponent.string9()");
              subject.hasErrorContaining("    TestComponent.string10()");
              subject.hasErrorContaining("    TestComponent.string11()");
              subject.hasErrorContaining("    and 1 other")
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
                  "\033[1;31m[Dagger/MissingBinding]\033[0m Baz cannot be provided without an "
                      + "@Inject constructor or an @Provides-annotated method.");
              subject.hasErrorContaining("    Baz is injected at");
              subject.hasErrorContaining("        Bar(baz)");
              subject.hasErrorContaining("    Bar is requested at");
              subject.hasErrorContaining("        Parent.bar()");
              subject.hasErrorContaining("The following other entry points also depend on it:");
              subject.hasErrorContaining("    Parent.foo()");
              subject.hasErrorContaining("    Child.foo() [Parent → Child]");
              subject.hasErrorContaining("    Child.baz() [Parent → Child]")
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
                  "A binding for Object exists in [Parent → Child2 → RepeatedSub]:");
              subject.hasErrorContaining(
                  "[Parent → Child1 → RepeatedSub] RepeatedSub.getObject() [Parent → Child1 →"
                      + " RepeatedSub]");
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
              subject.hasErrorContaining("A binding for Object exists in bar.Sub:");
              subject.hasErrorContaining(
                  "[foo.Sub] foo.Sub.getObject() [Parent → Child1 → foo.Sub]");
            });
  }
}
