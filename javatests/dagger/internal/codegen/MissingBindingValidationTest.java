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
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.TestUtils.message;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MissingBindingValidationTest {
  @Test
  public void dependOnInterface() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.MyComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface MyComponent {",
        "  Foo getFoo();",
        "}");
    JavaFileObject injectable = JavaFileObjects.forSourceLines("test.Foo",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class Foo {",
        "  @Inject Foo(Bar bar) {}",
        "}");
    JavaFileObject nonInjectable = JavaFileObjects.forSourceLines("test.Bar",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "interface Bar {}");
    Compilation compilation = daggerCompiler().compile(component, injectable, nonInjectable);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("Bar cannot be provided without an @Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface MyComponent");
  }

  @Test
  public void entryPointDependsOnInterface() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "\033[1;31m[Dagger/MissingBinding]\033[0m TestClass.A cannot be provided "
                + "without an @Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  public void entryPointDependsOnQualifiedInterface() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "\033[1;31m[Dagger/MissingBinding]\033[0m @TestClass.Q TestClass.A cannot be provided "
                + "without an @Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test public void constructorInjectionWithoutAnnotation() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
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

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "TestClass.A cannot be provided without an @Inject constructor or an "
                + "@Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test public void membersInjectWithoutProvision() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
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

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "TestClass.B cannot be provided without an @Inject constructor or an "
                + "@Provides-annotated method. This type supports members injection but cannot be "
                + "implicitly provided.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  public void missingBindingWithSameKeyAsMembersInjectionMethod() {
    JavaFileObject self =
        JavaFileObjects.forSourceLines(
            "test.Self",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class Self {",
            "  @Inject Provider<Self> selfProvider;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.SelfComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SelfComponent {",
            "  void inject(Self target);",
            "}");

    Compilation compilation = daggerCompiler().compile(self, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("Self cannot be provided without an @Inject constructor")
        .inFile(component)
        .onLineContaining("interface SelfComponent");
  }

  @Test
  public void genericInjectClassWithWildcardDependencies() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Foo<? extends Number> foo();",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Foo<T> {",
            "  @Inject Foo(T t) {}",
            "}");
    Compilation compilation = daggerCompiler().compile(component, foo);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            "Foo<? extends Number> cannot be provided "
                + "without an @Provides-annotated method");
  }

  @Test public void longChainOfDependencies() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "TestClass.A cannot be provided without an @Provides-annotated method.",
                "    TestClass.A is injected at",
                "        TestClass.B(a)",
                "    TestClass.B is injected at",
                "        TestClass.C.b",
                "    TestClass.C is injected at",
                "        TestClass.AComponent.injectC(TestClass.C)",
                "The following other entry points also depend on it:",
                "    TestClass.AComponent.getFoo()",
                "    TestClass.AComponent.cProvider()",
                "    TestClass.AComponent.lazyC()",
                "    TestClass.AComponent.lazyCProvider()"))
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  public void bindsMethodAppearsInTrace() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "TestComponent",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestInterface testInterface();",
            "}");
    JavaFileObject interfaceFile =
        JavaFileObjects.forSourceLines("TestInterface", "interface TestInterface {}");
    JavaFileObject implementationFile =
        JavaFileObjects.forSourceLines(
            "TestImplementation",
            "import javax.inject.Inject;",
            "",
            "final class TestImplementation implements TestInterface {",
            "  @Inject TestImplementation(String missingBinding) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "TestModule",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds abstract TestInterface bindTestInterface(TestImplementation implementation);",
            "}");

    Compilation compilation =
        daggerCompiler().compile(component, module, interfaceFile, implementationFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "String cannot be provided without an @Inject constructor or an "
                    + "@Provides-annotated method.",
                "    String is injected at",
                "        TestImplementation(missingBinding)",
                "    TestImplementation is injected at",
                "        TestModule.bindTestInterface(implementation)",
                "    TestInterface is requested at",
                "        TestComponent.testInterface()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test public void resolvedParametersInDependencyTrace() {
    JavaFileObject generic = JavaFileObjects.forSourceLines("test.Generic",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class Generic<T> {",
        "  @Inject Generic(T t) {}",
        "}");
    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import java.util.List;",
        "",
        "final class TestClass {",
        "  @Inject TestClass(List list) {}",
        "}");
    JavaFileObject usesTest = JavaFileObjects.forSourceLines("test.UsesTest",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class UsesTest {",
        "  @Inject UsesTest(Generic<TestClass> genericTestClass) {}",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  UsesTest usesTest();",
        "}");

    Compilation compilation = daggerCompiler().compile(generic, testClass, usesTest, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "List cannot be provided without an @Provides-annotated method.",
                "    List is injected at",
                "        TestClass(list)",
                "    TestClass is injected at",
                "        Generic(t)",
                "    Generic<TestClass> is injected at",
                "        UsesTest(genericTestClass)",
                "    UsesTest is requested at",
                "        TestComponent.usesTest()"));
  }

  @Test public void resolvedVariablesInDependencyTrace() {
    JavaFileObject generic = JavaFileObjects.forSourceLines("test.Generic",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class Generic<T> {",
        "  @Inject T t;",
        "  @Inject Generic() {}",
        "}");
    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import java.util.List;",
        "",
        "final class TestClass {",
        "  @Inject TestClass(List list) {}",
        "}");
    JavaFileObject usesTest = JavaFileObjects.forSourceLines("test.UsesTest",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class UsesTest {",
        "  @Inject UsesTest(Generic<TestClass> genericTestClass) {}",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  UsesTest usesTest();",
        "}");

    Compilation compilation = daggerCompiler().compile(generic, testClass, usesTest, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "List cannot be provided without an @Provides-annotated method.",
                "    List is injected at",
                "        TestClass(list)",
                "    TestClass is injected at",
                "        Generic.t",
                "    Generic<TestClass> is injected at",
                "        UsesTest(genericTestClass)",
                "    UsesTest is requested at",
                "        TestComponent.usesTest()"));
  }

  @Test
  public void bindingUsedOnlyInSubcomponentDependsOnBindingOnlyInSubcomponent() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  String string();",
            "  Object needsString();",
            "}");
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = daggerCompiler().compile(parent, parentModule, child, childModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContainingMatch(
            "(?s)\\QString cannot be provided\\E.*\\Q[Child] Child.needsString()\\E")
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  @Test
  public void multibindingContributionBetweenAncestorComponentAndEntrypointComponent() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Grandchild grandchild();",
            "}");
    JavaFileObject grandchild =
        JavaFileObjects.forSourceLines(
            "Grandchild",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Grandchild {",
            "  Object object();",
            "}");

    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().compile(parent, parentModule, child, childModule, grandchild);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContainingMatch(
            "(?s)\\QDouble cannot be provided\\E.*"
                + "\\QGrandchild.object() [Parent → Child → Grandchild]\\E$")
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  @Test
  public void manyDependencies() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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
            "  @Binds abstract Object object(NotBound notBound);",
            "",
            "  @Provides static String string(NotBound notBound, Object object) {",
            "    return notBound.toString();",
            "  }",
            "}");
    JavaFileObject notBound =
        JavaFileObjects.forSourceLines(
            "test.NotBound", //
            "package test;",
            "",
            "interface NotBound {}");
    Compilation compilation = daggerCompiler().compile(component, module, notBound);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "\033[1;31m[Dagger/MissingBinding]\033[0m "
                    + "NotBound cannot be provided without an @Provides-annotated method.",
                "    NotBound is injected at",
                "        TestModule.object(notBound)",
                "    Object is requested at",
                "        TestComponent.object()",
                "It is also requested at:",
                "    TestModule.string(notBound, …)",
                "The following other entry points also depend on it:",
                "    TestComponent.string()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  public void tooManyRequests() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = daggerCompiler().compile(foo, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "\033[1;31m[Dagger/MissingBinding]\033[0m String cannot be provided without an "
                    + "@Inject constructor or an @Provides-annotated method.",
                "    String is requested at",
                "        TestComponent.string()",
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
                "    and 3 others"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void tooManyEntryPoints() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "\033[1;31m[Dagger/MissingBinding]\033[0m String cannot be provided without an "
                    + "@Inject constructor or an @Provides-annotated method.",
                "    String is requested at",
                "        TestComponent.string1()",
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
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void missingBindingInAllComponentsAndEntryPoints() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Foo foo();",
            "  Bar bar();",
            "  Child child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  Foo foo();",
            "  Baz baz();",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "Foo",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "Bar",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(Baz baz) {}",
            "}");
    JavaFileObject baz = JavaFileObjects.forSourceLines("Baz", "class Baz {}");

    Compilation compilation = daggerCompiler().compile(parent, child, foo, bar, baz);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "\033[1;31m[Dagger/MissingBinding]\033[0m Baz cannot be provided without an "
                    + "@Inject constructor or an @Provides-annotated method.",
                "    Baz is injected at",
                "        Bar(baz)",
                "    Bar is requested at",
                "        Parent.bar()",
                "The following other entry points also depend on it:",
                "    Parent.foo()",
                "    Child.foo() [Parent → Child]",
                "    Child.baz() [Parent → Child]"))
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  // Regression test for b/147423208 where if the same subcomponent was used
  // in two different parts of the hierarchy and only one side had a missing binding
  // incorrect caching during binding graph conversion might cause validation to pass
  // incorrectly.
  @Test
  public void sameSubcomponentUsedInDifferentHierarchies() {
    JavaFileObject parent = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface Parent {",
        "  Child1 getChild1();",
        "  Child2 getChild2();",
        "}");
    JavaFileObject child1 = JavaFileObjects.forSourceLines("test.Child1",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = LongModule.class)",
        "interface Child1 {",
        "  RepeatedSub getSub();",
        "}");
    JavaFileObject child2 = JavaFileObjects.forSourceLines("test.Child2",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface Child2 {",
        "  RepeatedSub getSub();",
        "}");
    JavaFileObject repeatedSub = JavaFileObjects.forSourceLines("test.RepeatedSub",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface RepeatedSub {",
        "  Foo getFoo();",
        "}");
    JavaFileObject injectable = JavaFileObjects.forSourceLines("test.Foo",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class Foo {",
        "  @Inject Foo(Long value) {}",
        "}");
    JavaFileObject module = JavaFileObjects.forSourceLines("test.LongModule",
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
    Compilation compilation = daggerCompiler().compile(
        parent, child1, child2, repeatedSub, injectable, module);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("Long cannot be provided without an @Inject constructor")
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  @Test
  public void sameSubcomponentUsedInDifferentHierarchiesMissingBindingFromOneSide() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject child1 =
        JavaFileObjects.forSourceLines(
            "test.Child1",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child1Module.class)",
            "interface Child1 {",
            "  RepeatedSub getSub();",
            "}");
    JavaFileObject child2 =
        JavaFileObjects.forSourceLines(
            "test.Child2",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child2Module.class)",
            "interface Child2 {",
            "  RepeatedSub getSub();",
            "}");
    JavaFileObject repeatedSub =
        JavaFileObjects.forSourceLines(
            "test.RepeatedSub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = RepeatedSubModule.class)",
            "interface RepeatedSub {",
            "  Object getObject();",
            "}");
    JavaFileObject child1Module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject child2Module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject repeatedSubModule =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .compile(
                parent, child1, child2, repeatedSub, child1Module, child2Module, repeatedSubModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("A binding for Object exists in [Parent → Child2 → RepeatedSub]:");
    assertThat(compilation)
        .hadErrorContaining(
            "[Parent → Child1 → RepeatedSub] RepeatedSub.getObject() [Parent → Child1 →"
                + " RepeatedSub]");
  }

  @Test
  public void differentComponentPkgSameSimpleNameMissingBinding() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject child1 =
        JavaFileObjects.forSourceLines(
            "test.Child1",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child1Module.class)",
            "interface Child1 {",
            "  foo.Sub getSub();",
            "}");
    JavaFileObject child2 =
        JavaFileObjects.forSourceLines(
            "test.Child2",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child2Module.class)",
            "interface Child2 {",
            "  bar.Sub getSub();",
            "}");
    JavaFileObject sub1 =
        JavaFileObjects.forSourceLines(
            "foo.Sub",
            "package foo;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = test.RepeatedSubModule.class)",
            "public interface Sub {",
            "  Object getObject();",
            "}");
    JavaFileObject sub2 =
        JavaFileObjects.forSourceLines(
            "bar.Sub",
            "package bar;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = test.RepeatedSubModule.class)",
            "public interface Sub {",
            "  Object getObject();",
            "}");
    JavaFileObject child1Module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject child2Module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject repeatedSubModule =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .compile(
                parent, child1, child2, sub1, sub2, child1Module, child2Module, repeatedSubModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation).hadErrorContaining("A binding for Object exists in bar.Sub:");
    assertThat(compilation)
        .hadErrorContaining("[foo.Sub] foo.Sub.getObject() [Parent → Child1 → foo.Sub]");
  }
}
