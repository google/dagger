/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubject.assertThat;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.ErrorMessages.nullableToNonNullable;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GraphValidationTest {
  private static final JavaFileObject NULLABLE =
      JavaFileObjects.forSourceLines(
          "test.Nullable", // force one-string-per-line format
          "package test;",
          "",
          "public @interface Nullable {}");

  @Test public void componentOnConcreteClass() {
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
    assertAbout(javaSources()).that(Arrays.asList(component, injectable, nonInjectable))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("test.Bar cannot be provided without an @Provides-annotated method.")
            .in(component).onLine(7);
  }

  @Test public void componentProvisionWithNoDependencyChain() {
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
            "    A getA();",
            "    @Q A qualifiedA();",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "test.TestClass.A cannot be provided without an @Provides-annotated method.")
        .in(component)
        .onLine(12)
        .and()
        .withErrorContaining(
            "@test.TestClass.Q test.TestClass.A "
                + "cannot be provided without an @Provides-annotated method.")
        .in(component)
        .onLine(13);
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
    String expectedError = "test.TestClass.A cannot be provided without an "
        + "@Inject constructor or from an @Provides-annotated method.";
    assertAbout(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(15);
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
    String expectedError = "test.TestClass.B cannot be provided without an "
        + "@Inject constructor or from an @Provides-annotated method. "
        + "This type supports members injection but cannot be implicitly provided.";
    assertAbout(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(19);
  }

  @Test
  public void membersInjectDependsOnUnboundedType() {
    JavaFileObject injectsUnboundedType =
        JavaFileObjects.forSourceLines(
            "test.InjectsUnboundedType",
            "package test;",
            "",
            "import dagger.MembersInjector;",
            "import java.util.ArrayList;",
            "import javax.inject.Inject;",
            "",
            "class InjectsUnboundedType {",
            "  @Inject MembersInjector<ArrayList<?>> listInjector;",
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
            "  void injectsUnboundedType(InjectsUnboundedType injects);",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(injectsUnboundedType, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            Joiner.on('\n')
                .join(
                    "Type parameters must be bounded for members injection."
                        + " ? required by java.util.ArrayList<?>, via:",
                    "      dagger.MembersInjector<java.util.ArrayList<?>> is injected at",
                    "          test.InjectsUnboundedType.listInjector",
                    "      test.InjectsUnboundedType is injected at",
                    "          test.TestComponent.injectsUnboundedType(injects)"))
        .in(component)
        .onLine(7);
  }

  @Test
  public void invalidMembersInjection() {
    JavaFileObject injected =
        JavaFileObjects.forSourceLines(
            "test.Injected",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Injected {",
            "  @Inject static Object object;",
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
            "  void inject(Injected injected);",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(injected, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("static fields")
        .in(injected)
        .onLine(6);
  }

  @Test public void cyclicDependency() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "final class Outer {",
        "  static class A {",
        "    @Inject A(C cParam) {}",
        "  }",
        "",
        "  static class B {",
        "    @Inject B(A aParam) {}",
        "  }",
        "",
        "  static class C {",
        "    @Inject C(B bParam) {}",
        "  }",
        "",
        "  @Component()",
        "  interface CComponent {",
        "    C getC();",
        "  }",
        "}");

    String expectedError =
        Joiner.on('\n')
            .join(
                "Found a dependency cycle:",
                "      test.Outer.C is injected at",
                "          test.Outer.A.<init>(cParam)",
                "      test.Outer.A is injected at",
                "          test.Outer.B.<init>(aParam)",
                "      test.Outer.B is injected at",
                "          test.Outer.C.<init>(bParam)",
                "      test.Outer.C is provided at",
                "          test.Outer.CComponent.getC()");

    assertAbout(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(23);
  }

  @Test public void cyclicDependencyNotIncludingEntryPoint() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  static class A {",
            "    @Inject A(C cParam) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(A aParam) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject C(B bParam) {}",
            "  }",
            "",
            "  static class D {",
            "    @Inject D(C cParam) {}",
            "  }",
            "",
            "  @Component()",
            "  interface DComponent {",
            "    D getD();",
            "  }",
            "}");

    String expectedError =
        Joiner.on('\n')
            .join(
                "Found a dependency cycle:",
                "      test.Outer.C is injected at",
                "          test.Outer.A.<init>(cParam)",
                "      test.Outer.A is injected at",
                "          test.Outer.B.<init>(aParam)",
                "      test.Outer.B is injected at",
                "          test.Outer.C.<init>(bParam)",
                "      test.Outer.C is injected at",
                "          test.Outer.D.<init>(cParam)",
                "      test.Outer.D is provided at",
                "          test.Outer.DComponent.getD()");

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError)
        .in(component)
        .onLine(27);
  }

  @Test
  public void cyclicDependencyNotBrokenByMapBinding() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MapKey;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import java.util.Map;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  static class A {",
            "    @Inject A(Map<String, C> cMap) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(A aParam) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject C(B bParam) {}",
            "  }",
            "",
            "  @Component(modules = CModule.class)",
            "  interface CComponent {",
            "    C getC();",
            "  }",
            "",
            "  @Module",
            "  static class CModule {",
            "    @Provides @IntoMap",
            "    @StringKey(\"C\")",
            "    static C c(C c) {",
            "      return c;",
            "    }",
            "  }",
            "",
            "  @MapKey",
            "  @interface StringKey {",
            "    String value();",
            "  }",
            "}");

    String expectedError =
        Joiner.on('\n')
            .join(
                "Found a dependency cycle:",
                "      test.Outer.C is injected at",
                "          test.Outer.CModule.c(c)",
                "      java.util.Map<java.lang.String,test.Outer.C> is injected at",
                "          test.Outer.A.<init>(cMap)",
                "      test.Outer.A is injected at",
                "          test.Outer.B.<init>(aParam)",
                "      test.Outer.B is injected at",
                "          test.Outer.C.<init>(bParam)",
                "      test.Outer.C is provided at",
                "          test.Outer.CComponent.getC()");

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError)
        .in(component)
        .onLine(26);
  }

  @Test
  public void cyclicDependencyWithSetBinding() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  static class A {",
            "    @Inject A(Set<C> cSet) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(A aParam) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject C(B bParam) {}",
            "  }",
            "",
            "  @Component(modules = CModule.class)",
            "  interface CComponent {",
            "    C getC();",
            "  }",
            "",
            "  @Module",
            "  static class CModule {",
            "    @Provides @IntoSet",
            "    static C c(C c) {",
            "      return c;",
            "    }",
            "  }",
            "}");

    String expectedError =
        Joiner.on('\n')
            .join(
                "Found a dependency cycle:",
                "      test.Outer.C is injected at",
                "          test.Outer.CModule.c(c)",
                "      java.util.Set<test.Outer.C> is injected at",
                "          test.Outer.A.<init>(cSet)",
                "      test.Outer.A is injected at",
                "          test.Outer.B.<init>(aParam)",
                "      test.Outer.B is injected at",
                "          test.Outer.C.<init>(bParam)",
                "      test.Outer.C is provided at",
                "          test.Outer.CComponent.getC()");

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError)
        .in(component)
        .onLine(25);
  }

  @Test
  public void falsePositiveCyclicDependencyIndirectionDetected() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class Outer {",
            "  static class A {",
            "    @Inject A(C cParam) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(A aParam) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject C(B bParam) {}",
            "  }",
            "",
            "  static class D {",
            "    @Inject D(Provider<C> cParam) {}",
            "  }",
            "",
            "  @Component()",
            "  interface DComponent {",
            "    D getD();",
            "  }",
            "}");

    String expectedError =
        Joiner.on('\n')
            .join(
                "Found a dependency cycle:",
                "      test.Outer.C is injected at",
                "          test.Outer.A.<init>(cParam)",
                "      test.Outer.A is injected at",
                "          test.Outer.B.<init>(aParam)",
                "      test.Outer.B is injected at",
                "          test.Outer.C.<init>(bParam)",
                "      javax.inject.Provider<test.Outer.C> is injected at",
                "          test.Outer.D.<init>(cParam)",
                "      test.Outer.D is provided at",
                "          test.Outer.DComponent.getD()");

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError)
        .in(component)
        .onLine(28);
  }

  @Test
  public void cyclicDependencyInSubcomponents() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Grandchild grandchild();",
            "}");
    JavaFileObject grandchild =
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandchildModule.class)",
            "interface Grandchild {",
            "  String entry();",
            "}");
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class ChildModule {",
            "  @Provides static Object object(String string) {",
            "    return string;",
            "  }",
            "}");
    JavaFileObject grandchildModule =
        JavaFileObjects.forSourceLines(
            "test.GrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class GrandchildModule {",
            "  @Provides static String string(Object object) {",
            "    return object.toString();",
            "  }",
            "}");

    String expectedError =
        Joiner.on('\n')
            .join(
                "[test.Grandchild.entry()] Found a dependency cycle:",
                "      java.lang.String is injected at",
                "          test.ChildModule.object(string)",
                "      java.lang.Object is injected at",
                "          test.GrandchildModule.string(object)",
                "      java.lang.String is provided at",
                "          test.Grandchild.entry()");

    assertAbout(javaSources())
        .that(ImmutableList.of(parent, child, grandchild, childModule, grandchildModule))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError)
        .in(child)
        .onLine(6);
  }

  @Test
  public void circularBindsMethods() {
    JavaFileObject qualifier =
        JavaFileObjects.forSourceLines(
            "test.SomeQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier @interface SomeQualifier {}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Binds abstract Object bindUnqualified(@SomeQualifier Object qualified);",
            "  @Binds @SomeQualifier abstract Object bindQualified(Object unqualified);",
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
            "  Object unqualified();",
            "  @SomeQualifier Object qualified();",
            "}");

    assertThat(qualifier, module, component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "Found a dependency cycle:\n"
                + "      java.lang.Object is injected at\n"
                + "          test.TestModule.bindQualified(unqualified)\n"
                + "      @test.SomeQualifier java.lang.Object is injected at\n"
                + "          test.TestModule.bindUnqualified(qualified)\n"
                + "      java.lang.Object is provided at\n"
                + "          test.TestComponent.unqualified()")
        .in(component)
        .onLine(7)
        .and()
        .withErrorContaining(
            "Found a dependency cycle:\n"
                + "      @test.SomeQualifier java.lang.Object is injected at\n"
                + "          test.TestModule.bindUnqualified(qualified)\n"
                + "      java.lang.Object is injected at\n"
                + "          test.TestModule.bindQualified(unqualified)\n"
                + "      @test.SomeQualifier java.lang.Object is provided at\n"
                + "          test.TestComponent.qualified()")
        .in(component)
        .onLine(8);
  }

  @Test
  public void selfReferentialBinds() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Binds abstract Object bindToSelf(Object sameKey);",
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
            "  Object selfReferential();",
            "}");

    assertThat(module, component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            // TODO(gak): cl/126230644 produces a better error message in this case. Here it isn't
            // unclear what is going wrong.
            "Found a dependency cycle:\n"
                + "      java.lang.Object is injected at\n"
                + "          test.TestModule.bindToSelf(sameKey)\n"
                + "      java.lang.Object is provided at\n"
                + "          test.TestComponent.selfReferential()")
        .in(component)
        .onLine(7);
  }

  @Test public void duplicateExplicitBindings_ProvidesAndComponentProvision() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "final class Outer {",
        "  interface A {}",
        "",
        "  interface B {}",
        "",
        "  @Module",
        "  static class AModule {",
        "    @Provides String provideString() { return \"\"; }",
        "    @Provides A provideA(String s) { return new A() {}; }",
        "  }",
        "",
        "  @Component(modules = AModule.class)",
        "  interface Parent {",
        "    A getA();",
        "  }",
        "",
        "  @Module",
        "  static class BModule {",
        "    @Provides B provideB(A a) { return new B() {}; }",
        "  }",
        "",
        "  @Component(dependencies = Parent.class, modules = { BModule.class, AModule.class})",
        "  interface Child {",
        "    B getB();",
        "  }",
        "}");

    String expectedError = "test.Outer.A is bound multiple times:\n"
        + "      test.Outer.A test.Outer.Parent.getA()\n"
        + "      @Provides test.Outer.A test.Outer.AModule.provideA(String)";

    assertAbout(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(30);
  }

  @Test public void duplicateExplicitBindings_TwoProvidesMethods() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "final class Outer {",
        "  interface A {}",
        "",
        "  @Module",
        "  static class Module1 {",
        "    @Provides A provideA1() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module2 {",
        "    @Provides String provideString() { return \"\"; }",
        "    @Provides A provideA2(String s) { return new A() {}; }",
        "  }",
        "",
        "  @Component(modules = { Module1.class, Module2.class})",
        "  interface TestComponent {",
        "    A getA();",
        "  }",
        "}");

    String expectedError = "test.Outer.A is bound multiple times:\n"
        + "      @Provides test.Outer.A test.Outer.Module1.provideA1()\n"
        + "      @Provides test.Outer.A test.Outer.Module2.provideA2(String)";

    assertAbout(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(24);
  }

  @Test
  public void duplicateExplicitBindings_ProvidesVsBinds() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  interface A {}",
            "",
            "  static final class B implements A {",
            "    @Inject B() {}",
            "  }",
            "",
            "  @Module",
            "  static class Module1 {",
            "    @Provides A provideA1() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static abstract class Module2 {",
            "    @Binds abstract A bindA2(B b);",
            "  }",
            "",
            "  @Component(modules = { Module1.class, Module2.class})",
            "  interface TestComponent {",
            "    A getA();",
            "  }",
            "}");

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            Joiner.on("\n      ")
                .join(
                    "test.Outer.A is bound multiple times:",
                    "@Provides test.Outer.A test.Outer.Module1.provideA1()",
                    "@Binds test.Outer.A test.Outer.Module2.bindA2(test.Outer.B)"))
        .in(component)
        .onLine(28);
  }

  @Test public void duplicateExplicitBindings_MultipleProvisionTypes() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
        "package test;",
        "",
        "import dagger.Binds;",
        "import dagger.Component;",
        "import dagger.MapKey;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.MapKey;",
        "import dagger.multibindings.IntoMap;",
        "import dagger.multibindings.IntoSet;",
        "import java.util.HashMap;",
        "import java.util.HashSet;",
        "import java.util.Map;",
        "import java.util.Set;",
        "import javax.inject.Qualifier;",
        "",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "final class Outer {",
        "  @MapKey(unwrapValue = true)",
        "  @interface StringKey {",
        "    String value();",
        "  }",
        "",
        "  @Qualifier @interface SomeQualifier {}",
        "",
        "  @Module",
        "  abstract static class TestModule1 {",
        "    @Provides @IntoMap",
        "    @StringKey(\"foo\")",
        "    static String stringMapEntry() { return \"\"; }",
        "",
        "    @Binds @IntoMap @StringKey(\"bar\")",
        "    abstract String bindStringMapEntry(@SomeQualifier String value);",
        "",
        "    @Provides @IntoSet static String stringSetElement() { return \"\"; }",
        "    @Binds @IntoSet abstract String bindStringSetElement(@SomeQualifier String value);",
        "",
        "    @Provides @SomeQualifier static String provideSomeQualifiedString() { return \"\"; }",
        "  }",
        "",
        "  @Module",
        "  static class TestModule2 {",
        "    @Provides Set<String> stringSet() { return new HashSet<String>(); }",
        "",
        "    @Provides Map<String, String> stringMap() {",
        "      return new HashMap<String, String>();",
        "    }",
        "  }",
        "",
        "  @Component(modules = { TestModule1.class, TestModule2.class })",
        "  interface TestComponent {",
        "    Set<String> getStringSet();",
        "    Map<String, String> getStringMap();",
        "  }",
        "}");

    String expectedSetError =
        "java.util.Set<java.lang.String> has incompatible bindings or declarations:\n"
            + "      Set bindings and declarations:\n"
            + "          @Provides @dagger.multibindings.IntoSet String "
            + "test.Outer.TestModule1.stringSetElement()\n"
            + "          @Binds @dagger.multibindings.IntoSet String "
            + "test.Outer.TestModule1.bindStringSetElement(@test.Outer.SomeQualifier String)\n"
            + "      Unique bindings and declarations:\n"
            + "          @Provides Set<String> test.Outer.TestModule2.stringSet()";

    String expectedMapError =
        "java.util.Map<java.lang.String,java.lang.String> has incompatible bindings "
            + "or declarations:\n"
            + "      Map bindings and declarations:\n"
            + "          @Provides @dagger.multibindings.IntoMap "
            + "@test.Outer.StringKey(\"foo\") String"
            + " test.Outer.TestModule1.stringMapEntry()\n"
            + "          @Binds @dagger.multibindings.IntoMap "
            + "@test.Outer.StringKey(\"bar\") String"
            + " test.Outer.TestModule1.bindStringMapEntry(@test.Outer.SomeQualifier String)\n"
            + "      Unique bindings and declarations:\n"
            + "          @Provides Map<String,String> test.Outer.TestModule2.stringMap()";

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedSetError)
        .in(component)
        .onLine(52)
        .and()
        .withErrorContaining(expectedMapError)
        .in(component)
        .onLine(53);
  }

  @Test
  public void duplicateExplicitBindings_UniqueBindingAndMultibindingDeclaration() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.HashMap;",
            "import java.util.HashSet;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "final class Outer {",
            "  @Module",
            "  abstract static class TestModule1 {",
            "    @Multibinds abstract Map<String, String> stringMap();",
            "    @Multibinds abstract Set<String> stringSet();",
            "  }",
            "",
            "  @Module",
            "  static class TestModule2 {",
            "    @Provides Set<String> stringSet() { return new HashSet<String>(); }",
            "",
            "    @Provides Map<String, String> stringMap() {",
            "      return new HashMap<String, String>();",
            "    }",
            "  }",
            "",
            "  @Component(modules = { TestModule1.class, TestModule2.class })",
            "  interface TestComponent {",
            "    Set<String> getStringSet();",
            "    Map<String, String> getStringMap();",
            "  }",
            "}");

    String expectedSetError =
        "java.util.Set<java.lang.String> has incompatible bindings or declarations:\n"
            + "      Set bindings and declarations:\n"
            + "          @dagger.multibindings.Multibinds Set<String> "
            + "test.Outer.TestModule1.stringSet()\n"
            + "      Unique bindings and declarations:\n"
            + "          @Provides Set<String> test.Outer.TestModule2.stringSet()";

    String expectedMapError =
        "java.util.Map<java.lang.String,java.lang.String> has incompatible bindings "
            + "or declarations:\n"
            + "      Map bindings and declarations:\n"
            + "          @dagger.multibindings.Multibinds Map<String,String> "
            + "test.Outer.TestModule1.stringMap()\n"
            + "      Unique bindings and declarations:\n"
            + "          @Provides Map<String,String> test.Outer.TestModule2.stringMap()";

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedSetError)
        .in(component)
        .onLine(32)
        .and()
        .withErrorContaining(expectedMapError)
        .in(component)
        .onLine(33);
  }

  @Test public void duplicateBindings_TruncateAfterLimit() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "final class Outer {",
        "  interface A {}",
        "",
        "  @Module",
        "  static class Module1 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module2 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module3 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module4 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module5 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module6 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module7 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module8 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module9 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module10 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module11 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module12 {",
        "    @Provides A provideA() { return new A() {}; }",
        "  }",
        "",
        "  @Component(modules = {",
        "    Module1.class,",
        "    Module2.class,",
        "    Module3.class,",
        "    Module4.class,",
        "    Module5.class,",
        "    Module6.class,",
        "    Module7.class,",
        "    Module8.class,",
        "    Module9.class,",
        "    Module10.class,",
        "    Module11.class,",
        "    Module12.class",
        "  })",
        "  interface TestComponent {",
        "    A getA();",
        "  }",
        "}");

    String expectedError = "test.Outer.A is bound multiple times:\n"
        + "      @Provides test.Outer.A test.Outer.Module1.provideA()\n"
        + "      @Provides test.Outer.A test.Outer.Module2.provideA()\n"
        + "      @Provides test.Outer.A test.Outer.Module3.provideA()\n"
        + "      @Provides test.Outer.A test.Outer.Module4.provideA()\n"
        + "      @Provides test.Outer.A test.Outer.Module5.provideA()\n"
        + "      @Provides test.Outer.A test.Outer.Module6.provideA()\n"
        + "      @Provides test.Outer.A test.Outer.Module7.provideA()\n"
        + "      @Provides test.Outer.A test.Outer.Module8.provideA()\n"
        + "      @Provides test.Outer.A test.Outer.Module9.provideA()\n"
        + "      @Provides test.Outer.A test.Outer.Module10.provideA()\n"
        + "      and 2 others";

    assertAbout(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(86);
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
    String errorText = "test.TestClass.A cannot be provided without an @Provides-annotated method.";
    String firstError =
        Joiner.on("\n      ")
            .join(
                errorText,
                "test.TestClass.A is injected at",
                "    test.TestClass.B.<init>(a)",
                "test.TestClass.B is injected at",
                "    test.TestClass.C.b",
                "test.TestClass.C is injected at",
                "    test.TestClass.DImpl.<init>(c, …)",
                "test.TestClass.DImpl is injected at",
                "    test.TestClass.DModule.d(…, impl, …)",
                "@javax.inject.Named(\"slim shady\") test.TestClass.D is provided at",
                "    test.TestClass.AComponent.getFoo()");
    String otherErrorFormat =
        Joiner.on("\n      ")
            .join(
                errorText,
                "test.TestClass.A is injected at",
                "    test.TestClass.B.<init>(a)",
                "test.TestClass.B is injected at",
                "    test.TestClass.C.b",
                "test.TestClass.C is %s at",
                "    test.TestClass.AComponent.%s");
    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(firstError)
        .in(component)
        .onLine(40)
        .and()
        .withErrorContaining(String.format(otherErrorFormat, "injected", "injectC(c)"))
        .in(component)
        .onLine(41)
        .and()
        .withErrorContaining(String.format(otherErrorFormat, "provided", "cProvider()"))
        .in(component)
        .onLine(42)
        .and()
        .withErrorContaining(String.format(otherErrorFormat, "provided", "lazyC()"))
        .in(component)
        .onLine(43)
        .and()
        .withErrorContaining(String.format(otherErrorFormat, "provided", "lazyCProvider()"))
        .in(component)
        .onLine(44);
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
    assertAbout(javaSources())
        .that(ImmutableList.of(component, module, interfaceFile, implementationFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            Joiner.on("\n      ")
                .join(
                    "java.lang.String cannot be provided without an @Inject constructor or from "
                        + "an @Provides-annotated method.",
                    "java.lang.String is injected at",
                    "    TestImplementation.<init>(missingBinding)",
                    "TestImplementation is injected at",
                    "    TestModule.bindTestInterface(implementation)",
                    "TestInterface is provided at",
                    "    TestComponent.testInterface()"))
        .in(component)
        .onLine(5);
  }

  @Test
  public void bindsMissingRightHandSide() {
    JavaFileObject duplicates =
        JavaFileObjects.forSourceLines(
            "test.Duplicates",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.multibindings.IntKey;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.LongKey;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "interface Duplicates {",
            "",
            "  interface BoundTwice {}",
            "",
            "  class BoundImpl implements BoundTwice {",
            "    @Inject BoundImpl() {}",
            "  }",
            "",
            "  class NotBound implements BoundTwice {}",
            "",
            "  @Module",
            "  abstract class DuplicatesModule {",
            "    @Binds abstract BoundTwice bindWithResolvedKey(BoundImpl impl);",
            "    @Binds abstract BoundTwice bindWithUnresolvedKey(NotBound notBound);",
            "",
            "    @Binds abstract Object bindObject(NotBound notBound);",
            "",
            "    @Binds @IntoSet abstract BoundTwice bindWithUnresolvedKey_set(NotBound notBound);",
            "",
            "    @Binds @IntoMap @IntKey(1)",
            "    abstract BoundTwice bindWithUnresolvedKey_intMap(NotBound notBound);",
            "",
            "    @Provides @IntoMap @LongKey(2L)",
            "    static BoundTwice provideWithUnresolvedKey_longMap(BoundImpl impl) {",
            "      return impl;",
            "    }",
            "    @Binds @IntoMap @LongKey(2L)",
            "    abstract BoundTwice bindWithUnresolvedKey_longMap(NotBound notBound);",
            "  }",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "import java.util.Map;",
            "import test.Duplicates.BoundTwice;",
            "",
            "@Component(modules = Duplicates.DuplicatesModule.class)",
            "interface C {",
            "  BoundTwice boundTwice();",
            "  Object object();",
            "  Set<BoundTwice> set();",
            "  Map<Integer, BoundTwice> intMap();",
            "  Map<Long, BoundTwice> longMap();",
            "}");

    assertThat(duplicates, component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("test.Duplicates.BoundTwice is bound multiple times:")
            .in(component).onLine(10)
        .and().withErrorContaining("test.Duplicates.DuplicatesModule.bindWithUnresolvedKey")
            .in(component).onLine(10)
        .and().withErrorContaining("test.Duplicates.NotBound cannot be provided")
            .in(component).onLine(11)
        .and().withErrorContaining("test.Duplicates.NotBound cannot be provided")
            .in(component).onLine(12)
        .and().withErrorContaining("test.Duplicates.NotBound cannot be provided")
            .in(component).onLine(13)
        .and().withErrorContaining("same map key is bound more than once")
            .in(component).onLine(14);
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
    String expectedMsg =
        Joiner.on("\n")
            .join(
                "java.util.List cannot be provided without an @Provides-annotated method.",
                "      java.util.List is injected at",
                "          test.TestClass.<init>(list)",
                "      test.TestClass is injected at",
                "          test.Generic.<init>(t)",
                "      test.Generic<test.TestClass> is injected at",
                "          test.UsesTest.<init>(genericTestClass)",
                "      test.UsesTest is provided at",
                "          test.TestComponent.usesTest()");
    assertAbout(javaSources()).that(ImmutableList.of(generic, testClass, usesTest, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedMsg);
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
    String expectedMsg =
        Joiner.on("\n")
            .join(
                "java.util.List cannot be provided without an @Provides-annotated method.",
                "      java.util.List is injected at",
                "          test.TestClass.<init>(list)",
                "      test.TestClass is injected at",
                "          test.Generic.t",
                "      test.Generic<test.TestClass> is injected at",
                "          test.UsesTest.<init>(genericTestClass)",
                "      test.UsesTest is provided at",
                "          test.TestComponent.usesTest()");
    assertAbout(javaSources()).that(ImmutableList.of(generic, testClass, usesTest, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedMsg);
  }

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
    assertAbout(javaSources()).that(ImmutableList.of(NULLABLE, a, module, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    assertAbout(javaSources()).that(ImmutableList.of(NULLABLE, a, module, component))
        .withCompilerOptions("-Adagger.nullableValidation=WARNING")
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
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
    assertAbout(javaSources()).that(ImmutableList.of(NULLABLE, a, module, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    assertAbout(javaSources()).that(ImmutableList.of(NULLABLE, a, module, component))
        .withCompilerOptions("-Adagger.nullableValidation=WARNING")
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
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
    assertAbout(javaSources()).that(ImmutableList.of(NULLABLE, a, module, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    assertAbout(javaSources()).that(ImmutableList.of(NULLABLE, a, module, component))
        .withCompilerOptions("-Adagger.nullableValidation=WARNING")
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
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
    assertAbout(javaSources()).that(ImmutableList.of(NULLABLE, module, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    assertAbout(javaSources()).that(ImmutableList.of(NULLABLE, module, component))
        .withCompilerOptions("-Adagger.nullableValidation=WARNING")
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
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
    assertAbout(javaSources())
        .that(ImmutableList.of(NULLABLE, a, module, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
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
    assertAbout(javaSources())
        .that(ImmutableList.of(NULLABLE, a, module, component))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
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
    assertAbout(javaSources())
        .that(ImmutableList.of(NULLABLE, a, module, component))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
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
    assertAbout(javaSources())
        .that(ImmutableList.of(NULLABLE, a, module, component))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
  }

  @Test public void componentDependencyMustNotCycle_Direct() {
    JavaFileObject shortLifetime = JavaFileObjects.forSourceLines("test.ComponentShort",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = ComponentShort.class)",
        "interface ComponentShort {",
        "}");
    String errorMessage =
        "test.ComponentShort contains a cycle in its component dependencies:\n"
            + "      test.ComponentShort";
    assertAbout(javaSource())
        .that(shortLifetime)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(errorMessage);
  }

  @Test public void componentDependencyMustNotCycle_Indirect() {
    JavaFileObject longLifetime = JavaFileObjects.forSourceLines("test.ComponentLong",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = ComponentMedium.class)",
        "interface ComponentLong {",
        "}");
    JavaFileObject mediumLifetime = JavaFileObjects.forSourceLines("test.ComponentMedium",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = ComponentLong.class)",
        "interface ComponentMedium {",
        "}");
    JavaFileObject shortLifetime = JavaFileObjects.forSourceLines("test.ComponentShort",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = ComponentMedium.class)",
        "interface ComponentShort {",
        "}");
    String longErrorMessage =
        "test.ComponentLong contains a cycle in its component dependencies:\n"
            + "      test.ComponentLong\n"
            + "      test.ComponentMedium\n"
            + "      test.ComponentLong";
    String mediumErrorMessage =
        "test.ComponentMedium contains a cycle in its component dependencies:\n"
            + "      test.ComponentMedium\n"
            + "      test.ComponentLong\n"
            + "      test.ComponentMedium";
    String shortErrorMessage =
        "test.ComponentShort contains a cycle in its component dependencies:\n"
            + "      test.ComponentMedium\n"
            + "      test.ComponentLong\n"
            + "      test.ComponentMedium\n"
            + "      test.ComponentShort";
    assertAbout(javaSources())
        .that(ImmutableList.of(longLifetime, mediumLifetime, shortLifetime))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(longErrorMessage).in(longLifetime)
        .and()
        .withErrorContaining(mediumErrorMessage).in(mediumLifetime)
        .and()
        .withErrorContaining(shortErrorMessage).in(shortLifetime);
  }

  @Test
  public void subcomponentBindingConflictsWithParent() {
    JavaFileObject parentChildConflict =
        JavaFileObjects.forSourceLines(
            "test.ParentChildConflict",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier @interface ParentChildConflict {}");
    JavaFileObject parentGrandchildConflict =
        JavaFileObjects.forSourceLines(
            "test.ParentGrandchildConflict",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier @interface ParentGrandchildConflict {}");
    JavaFileObject childGrandchildConflict =
        JavaFileObjects.forSourceLines(
            "test.ChildGrandchildConflict",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier @interface ChildGrandchildConflict {}");

    /* Some annotation processor implementations do not report more than one error per element. So
     * separate parents for testing parent-conflicts-with-child and
     * parent-conflicts-with-grandchild.
     */
    JavaFileObject parentConflictsWithChild =
        JavaFileObjects.forSourceLines(
            "test.ParentConflictsWithChild",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Component(modules = ParentConflictsWithChild.ParentModule.class)",
            "interface ParentConflictsWithChild {",
            "  @ParentChildConflict Object parentChildConflict();",
            "",
            "  Child child();",
            "",
            "  @Module",
            "  static class ParentModule {",
            "    @Provides @ParentChildConflict static Object parentChildConflict() {",
            "      return \"parent\";",
            "    }",
            "  }",
            "}");
    JavaFileObject parentConflictsWithGrandchild =
        JavaFileObjects.forSourceLines(
            "test.ParentConflictsWithGrandchild",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Component(modules = ParentConflictsWithGrandchild.ParentModule.class)",
            "interface ParentConflictsWithGrandchild {",
            "  @ParentGrandchildConflict Object parentGrandchildConflict();",
            "",
            "  Child child();",
            "",
            "  @Module",
            "  static class ParentModule {",
            "    @Provides @ParentGrandchildConflict static Object parentGrandchildConflict() {",
            "      return \"parent\";",
            "    }",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child.ChildModule.class)",
            "interface Child {",
            "  @ParentChildConflict Object parentChildConflict();",
            "  @ChildGrandchildConflict Object childGrandchildConflict();",
            "",
            "  Grandchild grandchild();",
            "",
            "  @Module",
            "  static class ChildModule {",
            "    @Provides @ParentChildConflict static Object parentChildConflict() {",
            "      return \"child\";",
            "    }",
            "",
            "    @Provides @ChildGrandchildConflict static Object childGrandchildConflict() {",
            "      return \"child\";",
            "    }",
            "  }",
            "}");
    JavaFileObject grandchild =
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Grandchild.GrandchildModule.class)",
            "interface Grandchild {",
            "  @ParentChildConflict Object parentChildConflict();",
            "  @ParentGrandchildConflict Object parentGrandchildConflict();",
            "  @ChildGrandchildConflict Object childGrandchildConflict();",
            "",
            "  @Module",
            "  static class GrandchildModule {",
            "    @Provides @ParentGrandchildConflict static Object parentGrandchildConflict() {",
            "      return \"grandchild\";",
            "    }",
            "",
            "    @Provides @ChildGrandchildConflict static Object childGrandchildConflict() {",
            "      return \"grandchild\";",
            "    }",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(
            ImmutableList.of(
                parentChildConflict,
                parentGrandchildConflict,
                childGrandchildConflict,
                parentConflictsWithChild,
                parentConflictsWithGrandchild,
                child,
                grandchild))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "[test.Child.parentChildConflict()] "
                + "@test.ParentChildConflict java.lang.Object is bound multiple times:\n"
                + "      @Provides @test.ParentChildConflict Object"
                + " test.ParentConflictsWithChild.ParentModule.parentChildConflict()\n"
                + "      @Provides @test.ParentChildConflict Object"
                + " test.Child.ChildModule.parentChildConflict()")
        .in(parentConflictsWithChild)
        .onLine(8)
        .and()
        .withErrorContaining(
            "[test.Grandchild.parentGrandchildConflict()] "
                + "@test.ParentGrandchildConflict java.lang.Object is bound multiple times:\n"
                + "      @Provides @test.ParentGrandchildConflict Object"
                + " test.ParentConflictsWithGrandchild.ParentModule.parentGrandchildConflict()\n"
                + "      @Provides @test.ParentGrandchildConflict Object"
                + " test.Grandchild.GrandchildModule.parentGrandchildConflict()")
        .in(parentConflictsWithGrandchild)
        .onLine(8)
        .and()
        .withErrorContaining(
            "[test.Grandchild.childGrandchildConflict()] "
                + "@test.ChildGrandchildConflict java.lang.Object is bound multiple times:\n"
                + "      @Provides @test.ChildGrandchildConflict Object"
                + " test.Child.ChildModule.childGrandchildConflict()\n"
                + "      @Provides @test.ChildGrandchildConflict Object"
                + " test.Grandchild.GrandchildModule.childGrandchildConflict()")
        .in(child)
        .onLine(8);
  }

  @Test
  public void subcomponentBindingConflictsWithParentWithNullableViolationAsWarning() {
    JavaFileObject parentConflictsWithChild =
        JavaFileObjects.forSourceLines(
            "test.ParentConflictsWithChild",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.annotation.Nullable;",
            "",
            "@Component(modules = ParentConflictsWithChild.ParentModule.class)",
            "interface ParentConflictsWithChild {",
            "  Child child();",
            "",
            "  @Module",
            "  static class ParentModule {",
            "    @Provides @Nullable static Object nullableParentChildConflict() {",
            "      return \"parent\";",
            "    }",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child.ChildModule.class)",
            "interface Child {",
            "  Object parentChildConflictThatViolatesNullability();",
            "",
            "  @Module",
            "  static class ChildModule {",
            "    @Provides static Object nonNullableParentChildConflict() {",
            "      return \"child\";",
            "    }",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(parentConflictsWithChild, child))
        .withCompilerOptions("-Adagger.nullableValidation=WARNING")
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "[test.Child.parentChildConflictThatViolatesNullability()] "
                + "java.lang.Object is bound multiple times:\n"
                + "      @Provides @javax.annotation.Nullable Object"
                + " test.ParentConflictsWithChild.ParentModule.nullableParentChildConflict()\n"
                + "      @Provides Object"
                + " test.Child.ChildModule.nonNullableParentChildConflict()")
        .in(parentConflictsWithChild)
        .onLine(9);
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
    assertAbout(javaSources())
        .that(ImmutableList.of(parent, parentModule, child, childModule))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("[Child.needsString()] java.lang.String cannot be provided")
        .in(parent)
        .onLine(4);
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
    assertAbout(javaSources())
        .that(ImmutableList.of(parent, parentModule, child, childModule, grandchild))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("[Grandchild.object()] java.lang.Double cannot be provided")
        .in(parent)
        .onLine(4);
  }

  @Test
  public void missingReleasableReferenceManager() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@CanReleaseReferences",
            "@BadMetadata",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject otherScope =
        JavaFileObjects.forSourceLines(
            "test.OtherScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface OtherScope {}");
    JavaFileObject yetAnotherScope =
        JavaFileObjects.forSourceLines(
            "test.YetAnotherScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface YetAnotherScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");
    JavaFileObject badMetadata =
        JavaFileObjects.forSourceLines(
            "test.BadMetadata", // force one-string-per-line format
            "package test;",
            "",
            "@interface BadMetadata {}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "",
            "@TestScope",
            "@YetAnotherScope",
            "@Component",
            "interface TestComponent {",
            "  @ForReleasableReferences(OtherScope.class)",
            "  ReleasableReferenceManager otherManager();",
            "",
            "  @ForReleasableReferences(TestScope.class)",
            "  TypedReleasableReferenceManager<TestMetadata> typedManager();",
            "",
            "  @ForReleasableReferences(TestScope.class)",
            "  TypedReleasableReferenceManager<BadMetadata> badManager();",
            "}");
    assertAbout(javaSources())
        .that(
            ImmutableList.of(
                testScope, otherScope, yetAnotherScope, testMetadata, badMetadata, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "There is no binding for "
                + "@dagger.releasablereferences.ForReleasableReferences(test.OtherScope.class) "
                + "dagger.releasablereferences.ReleasableReferenceManager "
                + "because no component in test.TestComponent's component hierarchy is annotated "
                + "with @test.OtherScope. "
                + "The available reference-releasing scopes are "
                + "[@test.TestScope, @test.YetAnotherScope].")
        .in(component)
        .onLine(13)
        .and()
        .withErrorContaining(
            "There is no binding for "
                + "@dagger.releasablereferences.ForReleasableReferences(test.TestScope.class) "
                + "dagger.releasablereferences.TypedReleasableReferenceManager<test.TestMetadata> "
                + "because test.TestScope is not annotated with @test.TestMetadata")
        .in(component)
        .onLine(16)
        .and()
        .withErrorContaining(
            "There is no binding for "
                + "@dagger.releasablereferences.ForReleasableReferences(test.TestScope.class) "
                + "dagger.releasablereferences.TypedReleasableReferenceManager<test.BadMetadata> "
                + "because test.BadMetadata is not annotated with "
                + "@dagger.releasablereferences.CanReleaseReferences")
        .in(component)
        .onLine(19);
  }

  @Test
  public void releasableReferenceManagerConflict() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@TestMetadata",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");

    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "import java.util.Set;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides @ForReleasableReferences(TestScope.class)",
            "  static ReleasableReferenceManager rrm() {",
            "    return null;",
            "  }",
            "",
            "  @Provides @ForReleasableReferences(TestScope.class)",
            "  static TypedReleasableReferenceManager<TestMetadata> typedRrm() {",
            "    return null;",
            "  }",
            "",
            "  @Provides",
            "  static Set<ReleasableReferenceManager> rrmSet() {",
            "    return null;",
            "  }",
            "",
            "  @Provides",
            "  static Set<TypedReleasableReferenceManager<TestMetadata>> typedRrmSet() {",
            "    return null;",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "import java.util.Set;",
            "",
            "@TestScope",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  @ForReleasableReferences(TestScope.class)",
            "  ReleasableReferenceManager testManager();",
            "",
            "  @ForReleasableReferences(TestScope.class)",
            "  TypedReleasableReferenceManager<TestMetadata> typedManager();",
            "",
            "  Set<ReleasableReferenceManager> managers();",
            "  Set<TypedReleasableReferenceManager<TestMetadata>> typedManagers();",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(testScope, testMetadata, testModule, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(
                error(
                    "@%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.ReleasableReferenceManager is bound multiple times:",
                    "@Provides @%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.ReleasableReferenceManager test.TestModule.rrm()",
                    "binding for "
                        + "@%1$s.ForReleasableReferences(value = test.TestScope.class) "
                        + "%1$s.ReleasableReferenceManager from the scope declaration"),
                "dagger.releasablereferences"))
        .in(component)
        .onLine(13)
        .and()
        .withErrorContaining(
            String.format(
                error(
                    "@%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.TypedReleasableReferenceManager<test.TestMetadata> "
                        + "is bound multiple times:",
                    "@Provides @%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.TypedReleasableReferenceManager<test.TestMetadata> "
                        + "test.TestModule.typedRrm()",
                    "binding for "
                        + "@%1$s.ForReleasableReferences(value = test.TestScope.class) "
                        + "%1$s.TypedReleasableReferenceManager<test.TestMetadata> "
                        + "from the scope declaration"),
                "dagger.releasablereferences"))
        .in(component)
        .onLine(16)
        .and()
        .withErrorContaining(
            error(
                "java.util.Set<dagger.releasablereferences.ReleasableReferenceManager> "
                    + "is bound multiple times:",
                "@Provides "
                    + "Set<dagger.releasablereferences.ReleasableReferenceManager> "
                    + "test.TestModule.rrmSet()",
                "Dagger-generated binding for "
                    + "Set<dagger.releasablereferences.ReleasableReferenceManager>"))
        .in(component)
        .onLine(18)
        .and()
        .withErrorContaining(
            String.format(
                error(
                    "java.util.Set<%1$s.TypedReleasableReferenceManager<test.TestMetadata>> "
                        + "is bound multiple times:",
                    "@Provides "
                        + "Set<%1$s.TypedReleasableReferenceManager<test.TestMetadata>> "
                        + "test.TestModule.typedRrmSet()",
                    "Dagger-generated binding for "
                        + "Set<%1$s.TypedReleasableReferenceManager<test.TestMetadata>>"),
                "dagger.releasablereferences"))
        .in(component)
        .onLine(19);
  }

  @Test
  public void abstractModuleWithInstanceMethod() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides int i() { return 1; }",
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
            "  int i();",
            "}");
    Compilation compilation = daggerCompiler().compile(module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("TestModule is abstract and has instance @Provides methods")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void abstractModuleWithInstanceMethod_subclassedIsAllowed() {
    JavaFileObject abstractModule =
        JavaFileObjects.forSourceLines(
            "test.AbstractModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class AbstractModule {",
            "  @Provides int i() { return 1; }",
            "}");
    JavaFileObject subclassedModule =
        JavaFileObjects.forSourceLines(
            "test.SubclassedModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "class SubclassedModule extends AbstractModule {}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = SubclassedModule.class)",
            "interface TestComponent {",
            "  int i();",
            "}");
    Compilation compilation = daggerCompiler().compile(abstractModule, subclassedModule, component);
    assertThat(compilation).succeeded();
  }

  private String error(String... lines) {
    return Joiner.on("\n      ").join(lines);
  }
}
