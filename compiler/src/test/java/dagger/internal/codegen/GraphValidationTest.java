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
import org.junit.Ignore;
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

    String expectedError = "test.Outer.CComponent.getC() contains a dependency cycle:\n"
        + "      test.Outer.C.<init>(test.Outer.B bParam)\n"
        + "          [parameter: test.Outer.B bParam]\n"
        + "      test.Outer.B.<init>(test.Outer.A aParam)\n"
        + "          [parameter: test.Outer.A aParam]\n"
        + "      test.Outer.A.<init>(test.Outer.C cParam)\n"
        + "          [parameter: test.Outer.C cParam]";

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

    String expectedError = "test.Outer.DComponent.getD() contains a dependency cycle:\n"
        + "      test.Outer.D.<init>(test.Outer.C cParam)\n"
        + "          [parameter: test.Outer.C cParam]\n"
        + "      test.Outer.C.<init>(test.Outer.B bParam)\n"
        + "          [parameter: test.Outer.B bParam]\n"
        + "      test.Outer.B.<init>(test.Outer.A aParam)\n"
        + "          [parameter: test.Outer.A aParam]\n"
        + "      test.Outer.A.<init>(test.Outer.C cParam)\n"
        + "          [parameter: test.Outer.C cParam]";

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
            "    @Provides(type = Provides.Type.MAP)",
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
                "      test.Outer.C.<init>(test.Outer.B bParam)",
                "          [parameter: test.Outer.B bParam]",
                "      test.Outer.B.<init>(test.Outer.A aParam)",
                "          [parameter: test.Outer.A aParam]",
                "      test.Outer.A.<init>(java.util.Map<java.lang.String,test.Outer.C> cMap)",
                "          [parameter: java.util.Map<java.lang.String,test.Outer.C> cMap]",
                "      test.Outer.A.<init>(java.util.Map<java.lang.String,test.Outer.C> cMap)",
                "          [parameter: java.util.Map<java.lang.String,test.Outer.C> cMap]",
                "      test.Outer.CModule.c(test.Outer.C c)",
                "          [parameter: test.Outer.C c]");

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
        "test.Outer.DComponent.getD() contains a dependency cycle:\n"
            + "      test.Outer.D.<init>(javax.inject.Provider<test.Outer.C> cParam)\n"
            + "          [parameter: javax.inject.Provider<test.Outer.C> cParam]\n"
            + "      test.Outer.C.<init>(test.Outer.B bParam)\n"
            + "          [parameter: test.Outer.B bParam]\n"
            + "      test.Outer.B.<init>(test.Outer.A aParam)\n"
            + "          [parameter: test.Outer.A aParam]\n"
            + "      test.Outer.A.<init>(test.Outer.C cParam)\n"
            + "          [parameter: test.Outer.C cParam]";

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError)
        .in(component)
        .onLine(28);
  }

  @Ignore @Test public void cyclicDependencySimpleProviderIndirectionWarning() {
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
            "    @Inject A(B bParam) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(C bParam, D dParam) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject C(Provider<A> aParam) {}",
            "  }",
            "",
            "  static class D {",
            "    @Inject D() {}",
            "  }",
            "",
            "  @Component()",
            "  interface CComponent {",
            "    C get();",
            "  }",
            "}");

    /* String expectedWarning =
     "test.Outer.CComponent.get() contains a dependency cycle:"
     + "      test.Outer.C.<init>(javax.inject.Provider<test.Outer.A> aParam)"
     + "          [parameter: javax.inject.Provider<test.Outer.A> aParam]"
     + "      test.Outer.A.<init>(test.Outer.B bParam)"
     + "          [parameter: test.Outer.B bParam]"
     + "      test.Outer.B.<init>(test.Outer.C bParam, test.Outer.D dParam)"
     + "          [parameter: test.Outer.C bParam]";
     */
    assertAbout(javaSource()) // TODO(cgruber): Implement warning checks.
        .that(component)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
        //.withWarningContaining(expectedWarning).in(component).onLine(X);
  }

  @Ignore @Test public void cyclicDependencySimpleProviderIndirectionWarningSuppressed() {
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
            "    @Inject A(B bParam) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(C bParam, D dParam) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject C(Provider<A> aParam) {}",
            "  }",
            "",
            "  static class D {",
            "    @Inject D() {}",
            "  }",
            "",
            "  @SuppressWarnings(\"dependency-cycle\")",
            "  @Component()",
            "  interface CComponent {",
            "    C get();",
            "  }",
            "}");

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
        //.compilesWithoutWarning(); //TODO(cgruber)
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
        "import java.util.HashMap;",
        "import java.util.HashSet;",
        "import java.util.Map;",
        "import java.util.Set;",
        "",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "import static dagger.Provides.Type.MAP;",
        "import static dagger.Provides.Type.SET;",
        "",
        "final class Outer {",
        "  @MapKey(unwrapValue = true)",
        "  @interface StringKey {",
        "    String value();",
        "  }",
        "",
        "  @Module",
        "  static class TestModule1 {",
        "    @Provides(type = MAP)",
        "    @StringKey(\"foo\")",
        "    String stringMapEntry() { return \"\"; }",
        "",
        "    @Provides(type = SET) String stringSetElement() { return \"\"; }",
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
            + "          @Provides(type=SET) String test.Outer.TestModule1.stringSetElement()\n"
            + "      Unique bindings and declarations:\n"
            + "          @Provides Set<String> test.Outer.TestModule2.stringSet()";

    String expectedMapError =
        "java.util.Map<java.lang.String,java.lang.String> has incompatible bindings "
            + "or declarations:\n"
            + "      Map bindings and declarations:\n"
            + "          @Provides(type=MAP) @test.Outer.StringKey(\"foo\") String"
            + " test.Outer.TestModule1.stringMapEntry()\n"
            + "      Unique bindings and declarations:\n"
            + "          @Provides Map<String,String> test.Outer.TestModule2.stringMap()";

    assertAbout(javaSource())
        .that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedSetError)
        .in(component)
        .onLine(43)
        .and()
        .withErrorContaining(expectedMapError)
        .in(component)
        .onLine(44);
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
            "import static dagger.Provides.Type.MAP;",
            "import static dagger.Provides.Type.SET;",
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
        .onLine(37)
        .and()
        .withErrorContaining(expectedMapError)
        .in(component)
        .onLine(38);
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
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
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
        "    @Inject C(B b) {}",
        "  }",
        "",
        "  interface D { }",
        "",
        "  static class DImpl implements D {",
        "    @Inject DImpl(C c, B b) {}",
        "  }",
        "",
        "  @Module",
        "  static class DModule {",
        "    @Provides D d(DImpl impl) { return impl; }",
        "  }",
        "",
        "  @Component(modules = { DModule.class })",
        "  interface AComponent {",
        "    D getFoo();",
        "    C injectC(C c);",
        "  }",
        "}");
    String errorText =
        "test.TestClass.A cannot be provided without an @Provides-annotated method.\n";
    String firstError = errorText
        + "      test.TestClass.DModule.d(test.TestClass.DImpl impl)\n"
        + "          [parameter: test.TestClass.DImpl impl]\n"
        + "      test.TestClass.DImpl.<init>(test.TestClass.C c, test.TestClass.B b)\n"
        + "          [parameter: test.TestClass.C c]\n"
        + "      test.TestClass.C.b\n"
        + "          [injected field of type: test.TestClass.B b]\n"
        + "      test.TestClass.B.<init>(test.TestClass.A a)\n"
        + "          [parameter: test.TestClass.A a]";
    String secondError = errorText
        + "      test.TestClass.C.b\n"
        + "          [injected field of type: test.TestClass.B b]\n"
        + "      test.TestClass.B.<init>(test.TestClass.A a)\n"
        + "          [parameter: test.TestClass.A a]";
    assertAbout(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(firstError).in(component).onLine(33)
        .and().withErrorContaining(secondError).in(component).onLine(34);
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
    String expectedMsg = Joiner.on("\n").join(
        "java.util.List cannot be provided without an @Provides-annotated method.",
        "      test.UsesTest.<init>(test.Generic<test.TestClass> genericTestClass)",
        "          [parameter: test.Generic<test.TestClass> genericTestClass]",
        "      test.Generic.<init>(test.TestClass t)",
        "          [parameter: test.TestClass t]",
        "      test.TestClass.<init>(java.util.List list)",
        "          [parameter: java.util.List list]");
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
    String expectedMsg = Joiner.on("\n").join(
        "java.util.List cannot be provided without an @Provides-annotated method.",
        "      test.UsesTest.<init>(test.Generic<test.TestClass> genericTestClass)",
        "          [parameter: test.Generic<test.TestClass> genericTestClass]",
        "      test.Generic.t",
        "          [injected field of type: test.TestClass t]",
        "      test.TestClass.<init>(java.util.List list)",
        "          [parameter: java.util.List list]");
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
  @Ignore
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
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Component(modules = Parent.ParentModule.class)",
            "interface Parent {",
            "  @ParentChildConflict Object parentChildConflict();",
            "  @ParentGrandchildConflict Object parentGrandchildConflict();",
            "",
            "  Child child();",
            "",
            "  @Module",
            "  static class ParentModule {",
            "    @Provides @ParentChildConflict static Object parentChildConflict() {",
            "      return \"parent\";",
            "    }",
            "",
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
                parent,
                child,
                grandchild))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            "@ParentChildConflict Object is rebound in test.Child:\n"
                + "      @Provides @ParentChildConflict Object"
                + " test.Parent.ParentModule.parentChildConflict()\n"
                + "      @Provides @ParentChildConflict Object"
                + " test.Child.ChildModule.parentChildConflict()\n")
        .in(parent)
        .onLine(12)
        .and()
        .withErrorContaining(
            "@ParentGrandchildConflict Object is rebound in test.Grandchild:\n"
                + "      @Provides @ParentGrandchildConflict Object"
                + " test.Parent.ParentModule.parentGrandchildConflict()\n"
                + "      @Provides @ParentGrandchildConflict Object"
                + " test.Grandchild.GrandchildModule.parentGrandchildConflict()\n")
        .in(parent)
        .onLine(12)
        .and()
        .withErrorContaining(
            "@ChildGrandchildConflict Object is rebound in test.Grandchild:\n"
                + "      @Provides @ChildGrandchildConflict Object"
                + " test.Child.ChildModule.childGrandchildConflict()\n"
                + "      @Provides @ChildGrandchildConflict Object"
                + " test.Grandchild.GrandchildModule.childGrandchildConflict()\n")
        .in(child)
        .onLine(12);
  }
}
