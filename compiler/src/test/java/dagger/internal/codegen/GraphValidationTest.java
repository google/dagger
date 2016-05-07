/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.ErrorMessages.nullableToNonNullable;

@RunWith(JUnit4.class)
public class GraphValidationTest {
  private final JavaFileObject NULLABLE = JavaFileObjects.forSourceLines("test.Nullable",
      "package test;",
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
                "test.Outer.CComponent.getC() contains a dependency cycle:",
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
                "test.Outer.DComponent.getD() contains a dependency cycle:",
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
                "test.Outer.CComponent.getC() contains a dependency cycle:",
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
                "test.Outer.CComponent.getC() contains a dependency cycle:",
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
                "test.Outer.DComponent.getD() contains a dependency cycle:",
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
  
  @Test public void duplicateExplicitBindings_MultipleProvisionTypes() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
        "package test;",
        "",
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
        "",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "final class Outer {",
        "  @MapKey(unwrapValue = true)",
        "  @interface StringKey {",
        "    String value();",
        "  }",
        "",
        "  @Module",
        "  static class TestModule1 {",
        "    @Provides @IntoMap",
        "    @StringKey(\"foo\")",
        "    String stringMapEntry() { return \"\"; }",
        "",
        "    @Provides @IntoSet String stringSetElement() { return \"\"; }",
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
            + "      Unique bindings and declarations:\n"
            + "          @Provides Set<String> test.Outer.TestModule2.stringSet()";

    String expectedMapError =
        "java.util.Map<java.lang.String,java.lang.String> has incompatible bindings "
            + "or declarations:\n"
            + "      Map bindings and declarations:\n"
            + "          @Provides @dagger.multibindings.IntoMap "
            + "@test.Outer.StringKey(\"foo\") String"
            + " test.Outer.TestModule1.stringMapEntry()\n"
            + "      Unique bindings and declarations:\n"
            + "          @Provides Map<String,String> test.Outer.TestModule2.stringMap()";

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedSetError)
        .in(component)
        .onLine(42)
        .and()
        .withErrorContaining(expectedMapError)
        .in(component)
        .onLine(43);
  }

  @Test
  public void duplicateExplicitBindings_UniqueBindingAndMultibindingDeclaration() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Multibindings;",
            "import dagger.Provides;",
            "import java.util.HashMap;",
            "import java.util.HashSet;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "final class Outer {",
            "  @Module",
            "  static class TestModule1 {",
            "    @Multibindings",
            "    interface Empties {",
            "      Map<String, String> stringMap();",
            "      Set<String> stringSet();",
            "    }",
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
            + "          Set<String> test.Outer.TestModule1.Empties.stringSet()\n"
            + "      Unique bindings and declarations:\n"
            + "          @Provides Set<String> test.Outer.TestModule2.stringSet()";

    String expectedMapError =
        "java.util.Map<java.lang.String,java.lang.String> has incompatible bindings "
            + "or declarations:\n"
            + "      Map bindings and declarations:\n"
            + "          Map<String,String> test.Outer.TestModule1.Empties.stringMap()\n"
            + "      Unique bindings and declarations:\n"
            + "          @Provides Map<String,String> test.Outer.TestModule2.stringMap()";

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedSetError)
        .in(component)
        .onLine(35)
        .and()
        .withErrorContaining(expectedMapError)
        .in(component)
        .onLine(36);
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
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "import javax.inject.Named;",
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
            "    @Inject C(X x, B b) {}",
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
            "  }",
            "}");
    String errorText = "test.TestClass.A cannot be provided without an @Provides-annotated method.";
    String firstError =
        Joiner.on('\n')
            .join(
                errorText,
                "      test.TestClass.A is injected at",
                "          test.TestClass.B.<init>(a)",
                "      test.TestClass.B is injected at",
                "          test.TestClass.C.b",
                "      test.TestClass.C is injected at",
                "          test.TestClass.DImpl.<init>(c, …)",
                "      test.TestClass.DImpl is injected at",
                "          test.TestClass.DModule.d(…, impl, …)",
                "      @javax.inject.Named(\"slim shady\") test.TestClass.D is provided at",
                "          test.TestClass.AComponent.getFoo()");
    String secondError =
        Joiner.on('\n')
            .join(
                errorText,
                "      test.TestClass.A is injected at",
                "          test.TestClass.B.<init>(a)",
                "      test.TestClass.B is injected at",
                "          test.TestClass.C.b",
                "      test.TestClass.C is injected at",
                "          test.TestClass.AComponent.injectC(c)");
    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(firstError)
        .in(component)
        .onLine(38)
        .and()
        .withErrorContaining(secondError)
        .in(component)
        .onLine(39);
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
}
