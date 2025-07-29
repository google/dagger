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
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DependencyCycleValidationTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public DependencyCycleValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final Source SIMPLE_CYCLIC_DEPENDENCY =
        CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(SIMPLE_CYCLIC_DEPENDENCY)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    Outer.C is injected at",
                          "        [Outer.CComponent] Outer.A(cParam)",
                          "    Outer.A is injected at",
                          "        [Outer.CComponent] Outer.B(aParam)",
                          "    Outer.B is injected at",
                          "        [Outer.CComponent] Outer.C(bParam)",
                          "    Outer.C is injected at",
                          "        [Outer.CComponent] Outer.A(cParam)",
                          "    ...",
                          "",
                          "The cycle is requested via:",
                          "    Outer.C is requested at",
                          "        [Outer.CComponent] Outer.CComponent.getC()"))
                  .onSource(SIMPLE_CYCLIC_DEPENDENCY)
                  .onLineContaining("interface CComponent");
            });
  }

  @Test
  public void cyclicDependencyWithModuleBindingValidation() {
    // Cycle errors should not show a dependency trace to an entry point when doing full binding
    // graph validation. So ensure that the message doesn't end with "test.Outer.C is requested at
    // test.Outer.CComponent.getC()", as the previous test's message does.
    CompilerTests.daggerCompiler(SIMPLE_CYCLIC_DEPENDENCY)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .put("dagger.fullBindingGraphValidation", "ERROR")
                .putAll(compilerMode.processorOptions())
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject
                  .hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    Outer.C is injected at",
                          "        [Outer.MModule] Outer.A(cParam)",
                          "    Outer.A is injected at",
                          "        [Outer.MModule] Outer.B(aParam)",
                          "    Outer.B is injected at",
                          "        [Outer.MModule] Outer.C(bParam)",
                          "    Outer.C is injected at",
                          "        [Outer.MModule] Outer.A(cParam)",
                          "    ...",
                          "",
                          "======================",
                          "Full classname legend:",
                          "======================",
                          "Outer: test.Outer",
                          "========================",
                          "End of classname legend:",
                          "========================"))
                  .onSource(SIMPLE_CYCLIC_DEPENDENCY)
                  .onLineContaining("interface MModule");

              subject
                  .hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    Outer.C is injected at",
                          "        [Outer.CComponent] Outer.A(cParam)",
                          "    Outer.A is injected at",
                          "        [Outer.CComponent] Outer.B(aParam)",
                          "    Outer.B is injected at",
                          "        [Outer.CComponent] Outer.C(bParam)",
                          "    Outer.C is injected at",
                          "        [Outer.CComponent] Outer.A(cParam)",
                          "    ...",
                          "",
                          "======================",
                          "Full classname legend:",
                          "======================",
                          "Outer: test.Outer",
                          "========================",
                          "End of classname legend:",
                          "========================"))
                  .onSource(SIMPLE_CYCLIC_DEPENDENCY)
                  .onLineContaining("interface CComponent");
            });
  }

  @Test public void cyclicDependencyNotIncludingEntryPoint() {
    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    Outer.C is injected at",
                          "        [Outer.DComponent] Outer.A(cParam)",
                          "    Outer.A is injected at",
                          "        [Outer.DComponent] Outer.B(aParam)",
                          "    Outer.B is injected at",
                          "        [Outer.DComponent] Outer.C(bParam)",
                          "    Outer.C is injected at",
                          "        [Outer.DComponent] Outer.A(cParam)",
                          "   ...",
                          "",
                          "The cycle is requested via:",
                          "    Outer.C is injected at",
                          "        [Outer.DComponent] Outer.D(cParam)",
                          "    Outer.D is requested at",
                          "        [Outer.DComponent] Outer.DComponent.getD()"))
                  .onSource(component)
                  .onLineContaining("interface DComponent");
            });
  }

  @Test
  public void cyclicDependencyNotBrokenByMapBinding() {
    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    Outer.C is injected at",
                          "        [Outer.CComponent] Outer.CModule.c(c)",
                          "    Map<String,Outer.C> is injected at",
                          "        [Outer.CComponent] Outer.A(cMap)",
                          "    Outer.A is injected at",
                          "        [Outer.CComponent] Outer.B(aParam)",
                          "    Outer.B is injected at",
                          "        [Outer.CComponent] Outer.C(bParam)",
                          "    Outer.C is injected at",
                          "        [Outer.CComponent] Outer.CModule.c(c)",
                          "   ...",
                          "",
                          "The cycle is requested via:",
                          "    Outer.C is requested at",
                          "        [Outer.CComponent] Outer.CComponent.getC()"))
                  .onSource(component)
                  .onLineContaining("interface CComponent");
            });
  }

  @Test
  public void cyclicDependencyWithSetBinding() {
    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    Outer.C is injected at",
                          "        [Outer.CComponent] Outer.CModule.c(c)",
                          "    Set<Outer.C> is injected at",
                          "        [Outer.CComponent] Outer.A(cSet)",
                          "    Outer.A is injected at",
                          "        [Outer.CComponent] Outer.B(aParam)",
                          "    Outer.B is injected at",
                          "        [Outer.CComponent] Outer.C(bParam)",
                          "    Outer.C is injected at",
                          "        [Outer.CComponent] Outer.CModule.c(c)",
                          "   ...",
                          "",
                          "The cycle is requested via:",
                          "    Outer.C is requested at",
                          "        [Outer.CComponent] Outer.CComponent.getC()"))
                  .onSource(component)
                  .onLineContaining("interface CComponent");
            });
  }

  @Test
  public void falsePositiveCyclicDependencyIndirectionDetected() {
    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    Outer.C is injected at",
                          "        [Outer.DComponent] Outer.A(cParam)",
                          "    Outer.A is injected at",
                          "        [Outer.DComponent] Outer.B(aParam)",
                          "    Outer.B is injected at",
                          "        [Outer.DComponent] Outer.C(bParam)",
                          "    Outer.C is injected at",
                          "        [Outer.DComponent] Outer.A(cParam)",
                          "   ...",
                          "",
                          "The cycle is requested via:",
                          "    Provider<Outer.C> is injected at",
                          "        [Outer.DComponent] Outer.D(cParam)",
                          "    Outer.D is requested at",
                          "        [Outer.DComponent] Outer.DComponent.getD()"))
                  .onSource(component)
                  .onLineContaining("interface DComponent");
            });
  }

  @Test
  public void cyclicDependencyInSubcomponents() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child.Builder child();",
            "}");
    Source child =
        CompilerTests.javaSource(
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
    Source grandchild =
        CompilerTests.javaSource(
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
    Source cycleModule =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(parent, child, grandchild, cycleModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    String is injected at",
                          "        [Child] CycleModule.object(string)",
                          "    Object is injected at",
                          "        [Child] CycleModule.string(object)",
                          "    String is injected at",
                          "        [Child] CycleModule.object(string)",
                          "    ...",
                          "",
                          "The cycle is requested via:",
                          "    String is requested at",
                          "        [Grandchild] Grandchild.entry()"))
                  .onSource(parent)
                  .onLineContaining("interface Parent");
            });
  }

  @Test
  public void cyclicDependencyInSubcomponentsWithChildren() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child.Builder child();",
            "}");
    Source child =
        CompilerTests.javaSource(
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
    Source grandchild =
        CompilerTests.javaSource(
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
    Source cycleModule =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(parent, child, grandchild, cycleModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    String is injected at",
                          "        [Child] CycleModule.object(string)",
                          "    Object is injected at",
                          "        [Child] CycleModule.string(object)",
                          "    String is injected at",
                          "        [Child] CycleModule.object(string)",
                          "    ...",
                          "",
                          "The cycle is requested via:",
                          "    String is requested at",
                          "        [Child] Child.entry() [Parent → Child]"))
                  .onSource(parent)
                  .onLineContaining("interface Parent");
            });
  }

  @Test
  public void circularBindsMethods() {
    Source qualifier =
        CompilerTests.javaSource(
            "test.SomeQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier @interface SomeQualifier {}");
    Source module =
        CompilerTests.javaSource(
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
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Object unqualified();",
            "}");

    CompilerTests.daggerCompiler(qualifier, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    Object is injected at",
                          "        [TestComponent] TestModule.bindQualified(unqualified)",
                          "    @SomeQualifier Object is injected at",
                          "        [TestComponent] TestModule.bindUnqualified(qualified)",
                          "    Object is injected at",
                          "        [TestComponent] TestModule.bindQualified(unqualified)",
                          "    ...",
                          "",
                          "The cycle is requested via:",
                          "    Object is requested at",
                          "        [TestComponent] TestComponent.unqualified()"))
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void selfReferentialBinds() {
    Source module =
        CompilerTests.javaSource(
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
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Object selfReferential();",
            "}");

    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    Object is injected at",
                          "        [TestComponent] TestModule.bindToSelf(sameKey)",
                          "    Object is injected at",
                          "        [TestComponent] TestModule.bindToSelf(sameKey)",
                          "    ...",
                          "",
                          "The cycle is requested via:",
                          "    Object is requested at",
                          "        [TestComponent] TestComponent.selfReferential()"))
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void cycleFromMembersInjectionMethod_WithSameKeyAsMembersInjectionMethod() {
    Source a =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class A {",
            "  @Inject A() {}",
            "  @Inject B b;",
            "}");
    Source b =
        CompilerTests.javaSource(
            "test.B",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class B {",
            "  @Inject B() {}",
            "  @Inject A a;",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.CycleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface CycleComponent {",
            "  void inject(A a);",
            "}");

    CompilerTests.daggerCompiler(a, b, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "Found a dependency cycle:",
                          "    test.B is injected at",
                          "        [CycleComponent] test.A.b",
                          "    test.A is injected at",
                          "        [CycleComponent] test.B.a",
                          "    test.B is injected at",
                          "        [CycleComponent] test.A.b",
                          "    ...",
                          "",
                          "The cycle is requested via:",
                          "    test.B is injected at",
                          "        [CycleComponent] test.A.b",
                          "    test.A is injected at",
                          "        [CycleComponent] CycleComponent.inject(test.A)"))
                  .onSource(component)
                  .onLineContaining("interface CycleComponent");
            });
  }

  @Test
  public void longCycleMaskedByShortBrokenCycles() {
    Source cycles =
        CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(cycles)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Found a dependency cycle:")
                  .onSource(cycles)
                  .onLineContaining("interface C");
            });
  }
}
