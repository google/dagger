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
    assertThat(compilation)
        .hadErrorContaining("test.Bar cannot be provided without an @Provides-annotated method.")
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
    assertThat(compilation)
        .hadErrorContaining(
            "[Dagger/MissingBinding] test.TestClass.A cannot be provided "
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
    assertThat(compilation)
        .hadErrorContaining(
            "[Dagger/MissingBinding] @test.TestClass.Q test.TestClass.A cannot be provided "
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
    assertThat(compilation)
        .hadErrorContaining(
            "test.TestClass.A cannot be provided without an @Inject constructor or an "
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
    assertThat(compilation)
        .hadErrorContaining(
            "test.TestClass.B cannot be provided without an @Inject constructor or an "
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
    assertThat(compilation)
        .hadErrorContaining("test.Self cannot be provided without an @Inject constructor")
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
    assertThat(compilation)
        .hadErrorContaining(
            "test.Foo<? extends java.lang.Number> cannot be provided "
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
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.TestClass.A cannot be provided without an @Provides-annotated method.",
                "    test.TestClass.A is injected at",
                "        test.TestClass.B(a)",
                "    test.TestClass.B is injected at",
                "        test.TestClass.C.b",
                "    test.TestClass.C is injected at",
                "        test.TestClass.AComponent.injectC(test.TestClass.C)",
                "The following other entry points also depend on it:",
                "    test.TestClass.AComponent.getFoo()",
                "    test.TestClass.AComponent.cProvider()",
                "    test.TestClass.AComponent.lazyC()",
                "    test.TestClass.AComponent.lazyCProvider()"))
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
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.lang.String cannot be provided without an @Inject constructor or an "
                    + "@Provides-annotated method.",
                "    java.lang.String is injected at",
                "        TestImplementation(missingBinding)",
                "    TestImplementation is injected at",
                "        TestModule.bindTestInterface(implementation)",
                "    TestInterface is provided at",
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
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.List cannot be provided without an @Provides-annotated method.",
                "    java.util.List is injected at",
                "        test.TestClass(list)",
                "    test.TestClass is injected at",
                "        test.Generic(t)",
                "    test.Generic<test.TestClass> is injected at",
                "        test.UsesTest(genericTestClass)",
                "    test.UsesTest is provided at",
                "        test.TestComponent.usesTest()"));
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
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.List cannot be provided without an @Provides-annotated method.",
                "    java.util.List is injected at",
                "        test.TestClass(list)",
                "    test.TestClass is injected at",
                "        test.Generic.t",
                "    test.Generic<test.TestClass> is injected at",
                "        test.UsesTest(genericTestClass)",
                "    test.UsesTest is provided at",
                "        test.TestComponent.usesTest()"));
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
    assertThat(compilation)
        .hadErrorContainingMatch(
            "(?s)\\Qjava.lang.String cannot be provided\\E.*\\QChild.needsString()\\E")
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
    assertThat(compilation)
        .hadErrorContainingMatch(
            "(?s)\\Qjava.lang.Double cannot be provided\\E.*"
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
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "[Dagger/MissingBinding] "
                    + "test.NotBound cannot be provided without an @Provides-annotated method.",
                "    test.NotBound is injected at",
                "        test.TestModule.object(notBound)",
                "    java.lang.Object is provided at",
                "        test.TestComponent.object()",
                "It is also requested at:",
                "    test.TestModule.string(notBound, …)",
                "The following other entry points also depend on it:",
                "    test.TestComponent.string()"))
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
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "[Dagger/MissingBinding] java.lang.String cannot be provided without an @Inject "
                    + "constructor or an @Provides-annotated method.",
                "    java.lang.String is provided at",
                "        test.TestComponent.string()",
                "It is also requested at:",
                "    test.Foo(one, …)",
                "    test.Foo(…, two, …)",
                "    test.Foo(…, three, …)",
                "    test.Foo(…, four, …)",
                "    test.Foo(…, five, …)",
                "    test.Foo(…, six, …)",
                "    test.Foo(…, seven, …)",
                "    test.Foo(…, eight, …)",
                "    test.Foo(…, nine, …)",
                "    test.Foo(…, ten, …)",
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
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "[Dagger/MissingBinding] java.lang.String cannot be provided without an @Inject "
                    + "constructor or an @Provides-annotated method.",
                "    java.lang.String is provided at",
                "        test.TestComponent.string1()",
                "The following other entry points also depend on it:",
                "    test.TestComponent.string2()",
                "    test.TestComponent.string3()",
                "    test.TestComponent.string4()",
                "    test.TestComponent.string5()",
                "    test.TestComponent.string6()",
                "    test.TestComponent.string7()",
                "    test.TestComponent.string8()",
                "    test.TestComponent.string9()",
                "    test.TestComponent.string10()",
                "    test.TestComponent.string11()",
                "    and 1 other"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }
}
