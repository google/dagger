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
import static dagger.internal.codegen.TestUtils.endsWithMessage;
import static dagger.internal.codegen.TestUtils.message;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DependencyCycleValidationTest {
  private static final JavaFileObject SIMPLE_CYCLIC_DEPENDENCY =
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
          "  @Module",
          "  interface MModule {",
          "    @Binds Object object(C c);",
          "  }",
          "",
          "  @Component",
          "  interface CComponent {",
          "    C getC();",
          "  }",
          "}");

  @Test
  public void cyclicDependency() {
    Compilation compilation = daggerCompiler().compile(SIMPLE_CYCLIC_DEPENDENCY);
    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.Outer.C is injected at",
                "        test.Outer.A(cParam)",
                "    test.Outer.A is injected at",
                "        test.Outer.B(aParam)",
                "    test.Outer.B is injected at",
                "        test.Outer.C(bParam)",
                "    test.Outer.C is provided at",
                "        test.Outer.CComponent.getC()"))
        .inFile(SIMPLE_CYCLIC_DEPENDENCY)
        .onLineContaining("interface CComponent");

    assertThat(compilation).hadErrorCount(1);
  }

  @Test
  public void cyclicDependencyWithModuleBindingValidation() {
    // Cycle errors should not show a dependency trace to an entry point when doing full binding
    // graph validation. So ensure that the message doesn't end with "test.Outer.C is provided at
    // test.Outer.CComponent.getC()", as the previous test's message does.
    Pattern moduleBindingValidationError =
        endsWithMessage(
            "Found a dependency cycle:",
            "    test.Outer.C is injected at",
            "        test.Outer.A(cParam)",
            "    test.Outer.A is injected at",
            "        test.Outer.B(aParam)",
            "    test.Outer.B is injected at",
            "        test.Outer.C(bParam)");

    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(SIMPLE_CYCLIC_DEPENDENCY);
    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContainingMatch(moduleBindingValidationError)
        .inFile(SIMPLE_CYCLIC_DEPENDENCY)
        .onLineContaining("interface MModule");

    assertThat(compilation)
        .hadErrorContainingMatch(moduleBindingValidationError)
        .inFile(SIMPLE_CYCLIC_DEPENDENCY)
        .onLineContaining("interface CComponent");

    assertThat(compilation).hadErrorCount(2);
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
            "  @Component",
            "  interface DComponent {",
            "    D getD();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.Outer.C is injected at",
                "        test.Outer.A(cParam)",
                "    test.Outer.A is injected at",
                "        test.Outer.B(aParam)",
                "    test.Outer.B is injected at",
                "        test.Outer.C(bParam)",
                "    test.Outer.C is injected at",
                "        test.Outer.D(cParam)",
                "    test.Outer.D is provided at",
                "        test.Outer.DComponent.getD()"))
        .inFile(component)
        .onLineContaining("interface DComponent");
  }

  @Test
  public void cyclicDependencyNotBrokenByMapBinding() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
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
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.Outer.C is injected at",
                "        test.Outer.CModule.c(c)",
                "    java.util.Map<java.lang.String,test.Outer.C> is injected at",
                "        test.Outer.A(cMap)",
                "    test.Outer.A is injected at",
                "        test.Outer.B(aParam)",
                "    test.Outer.B is injected at",
                "        test.Outer.C(bParam)",
                "    test.Outer.C is provided at",
                "        test.Outer.CComponent.getC()"))
        .inFile(component)
        .onLineContaining("interface CComponent");
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

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.Outer.C is injected at",
                "        test.Outer.CModule.c(c)",
                "    java.util.Set<test.Outer.C> is injected at",
                "        test.Outer.A(cSet)",
                "    test.Outer.A is injected at",
                "        test.Outer.B(aParam)",
                "    test.Outer.B is injected at",
                "        test.Outer.C(bParam)",
                "    test.Outer.C is provided at",
                "        test.Outer.CComponent.getC()"))
        .inFile(component)
        .onLineContaining("interface CComponent");
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
            "  @Component",
            "  interface DComponent {",
            "    D getD();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.Outer.C is injected at",
                "        test.Outer.A(cParam)",
                "    test.Outer.A is injected at",
                "        test.Outer.B(aParam)",
                "    test.Outer.B is injected at",
                "        test.Outer.C(bParam)",
                "    javax.inject.Provider<test.Outer.C> is injected at",
                "        test.Outer.D(cParam)",
                "    test.Outer.D is provided at",
                "        test.Outer.DComponent.getD()"))
        .inFile(component)
        .onLineContaining("interface DComponent");
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
            "  Child.Builder child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = CycleModule.class)",
            "interface Child {",
            "  Grandchild.Builder grandchild();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
            "}");
    JavaFileObject grandchild =
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Grandchild {",
            "  String entry();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Grandchild build();",
            "  }",
            "}");
    JavaFileObject cycleModule =
        JavaFileObjects.forSourceLines(
            "test.CycleModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class CycleModule {",
            "  @Provides static Object object(String string) {",
            "    return string;",
            "  }",
            "",
            "  @Provides static String string(Object object) {",
            "    return object.toString();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(parent, child, grandchild, cycleModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    java.lang.String is injected at",
                "        test.CycleModule.object(string)",
                "    java.lang.Object is injected at",
                "        test.CycleModule.string(object)",
                "    java.lang.String is provided at",
                "        test.Grandchild.entry()"))
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  @Test
  public void cyclicDependencyInSubcomponentsWithChildren() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child.Builder child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = CycleModule.class)",
            "interface Child {",
            "  String entry();",
            "",
            "  Grandchild.Builder grandchild();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
            "}");
    // Grandchild has no entry point that depends on the cycle. http://b/111317986
    JavaFileObject grandchild =
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Grandchild {",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Grandchild build();",
            "  }",
            "}");
    JavaFileObject cycleModule =
        JavaFileObjects.forSourceLines(
            "test.CycleModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class CycleModule {",
            "  @Provides static Object object(String string) {",
            "    return string;",
            "  }",
            "",
            "  @Provides static String string(Object object) {",
            "    return object.toString();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(parent, child, grandchild, cycleModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    java.lang.String is injected at",
                "        test.CycleModule.object(string)",
                "    java.lang.Object is injected at",
                "        test.CycleModule.string(object)",
                "    java.lang.String is provided at",
                "        test.Child.entry() [test.Parent → test.Child]"))
        .inFile(parent)
        .onLineContaining("interface Parent");
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
            "}");

    Compilation compilation = daggerCompiler().compile(qualifier, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    java.lang.Object is injected at",
                "        test.TestModule.bindQualified(unqualified)",
                "    @test.SomeQualifier java.lang.Object is injected at",
                "        test.TestModule.bindUnqualified(qualified)",
                "    java.lang.Object is provided at",
                "        test.TestComponent.unqualified()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
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

    Compilation compilation = daggerCompiler().compile(module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    java.lang.Object is injected at",
                "        test.TestModule.bindToSelf(sameKey)",
                "    java.lang.Object is provided at",
                "        test.TestComponent.selfReferential()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void cycleFromMembersInjectionMethod_WithSameKeyAsMembersInjectionMethod() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class A {",
            "  @Inject A() {}",
            "  @Inject B b;",
            "}");
    JavaFileObject b =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class B {",
            "  @Inject B() {}",
            "  @Inject A a;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.CycleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface CycleComponent {",
            "  void inject(A a);",
            "}");

    Compilation compilation = daggerCompiler().compile(a, b, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.B is injected at",
                "        test.A.b",
                "    test.A is injected at",
                "        test.B.a",
                "    test.B is injected at",
                "        test.A.b",
                "    test.A is injected at",
                "        test.CycleComponent.inject(test.A)"))
        .inFile(component)
        .onLineContaining("interface CycleComponent");
  }

  @Test
  public void longCycleMaskedByShortBrokenCycles() {
    JavaFileObject cycles =
        JavaFileObjects.forSourceLines(
            "test.Cycles",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "import dagger.Component;",
            "",
            "final class Cycles {",
            "  static class A {",
            "    @Inject A(Provider<A> aProvider, B b) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(Provider<B> bProvider, A a) {}",
            "  }",
            "",
            "  @Component",
            "  interface C {",
            "    A a();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(cycles);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Found a dependency cycle:")
        .inFile(cycles)
        .onLineContaining("interface C");
  }
}
