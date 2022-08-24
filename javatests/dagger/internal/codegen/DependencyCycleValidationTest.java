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
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.TestUtils.endsWithMessage;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import dagger.testing.compile.CompilerTests;
import java.util.regex.Pattern;
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
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining("Found a dependency cycle:");
              subject.hasErrorContaining("    Outer.C is injected at");
              subject.hasErrorContaining("        Outer.A(cParam)");
              subject.hasErrorContaining("    Outer.A is injected at");
              subject.hasErrorContaining("        Outer.B(aParam)");
              subject.hasErrorContaining("    Outer.B is injected at");
              subject.hasErrorContaining("        Outer.C(bParam)");
              subject.hasErrorContaining("    Outer.C is injected at");
              subject.hasErrorContaining("        Outer.A(cParam)");
              subject.hasErrorContaining("The cycle is requested via:");
              subject.hasErrorContaining("    Outer.C is requested at");
              subject.hasErrorContaining("        Outer.CComponent.getC()")
                  .onSource(SIMPLE_CYCLIC_DEPENDENCY)
                  .onLineContaining("interface CComponent");
            });
  }

  // TODO(b/243720787): Requires CompilationResultSubject#hasErrorContainingMatch()
  @Test
  public void cyclicDependencyWithModuleBindingValidation() {
    // Cycle errors should not show a dependency trace to an entry point when doing full binding
    // graph validation. So ensure that the message doesn't end with "test.Outer.C is requested at
    // test.Outer.CComponent.getC()", as the previous test's message does.
    Pattern moduleBindingValidationError =
        endsWithMessage(
            "Found a dependency cycle:",
            "    Outer.C is injected at",
            "        Outer.A(cParam)",
            "    Outer.A is injected at",
            "        Outer.B(aParam)",
            "    Outer.B is injected at",
            "        Outer.C(bParam)",
            "    Outer.C is injected at",
            "        Outer.A(cParam)",
            "    ...",
            "",
            "======================",
            "Full classname legend:",
            "======================",
            "Outer: test.Outer",
            "========================",
            "End of classname legend:",
            "========================");

    Compilation compilation =
        compilerWithOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(SIMPLE_CYCLIC_DEPENDENCY.toJFO());
    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContainingMatch(moduleBindingValidationError)
        .inFile(SIMPLE_CYCLIC_DEPENDENCY.toJFO())
        .onLineContaining("interface MModule");

    assertThat(compilation)
        .hadErrorContainingMatch(moduleBindingValidationError)
        .inFile(SIMPLE_CYCLIC_DEPENDENCY.toJFO())
        .onLineContaining("interface CComponent");

    assertThat(compilation).hadErrorCount(2);
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
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining("Found a dependency cycle:");
              subject.hasErrorContaining("    Outer.C is injected at");
              subject.hasErrorContaining("        Outer.A(cParam)");
              subject.hasErrorContaining("    Outer.A is injected at");
              subject.hasErrorContaining("        Outer.B(aParam)");
              subject.hasErrorContaining("    Outer.B is injected at");
              subject.hasErrorContaining("        Outer.C(bParam)");
              subject.hasErrorContaining("    Outer.C is injected at");
              subject.hasErrorContaining("        Outer.A(cParam)");
              subject.hasErrorContaining("   ...");
              subject.hasErrorContaining("");
              subject.hasErrorContaining("The cycle is requested via:");
              subject.hasErrorContaining("    Outer.C is injected at");
              subject.hasErrorContaining("        Outer.D(cParam)");
              subject.hasErrorContaining("    Outer.D is requested at");
              subject.hasErrorContaining("        Outer.DComponent.getD()")
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
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining("Found a dependency cycle:");
              subject.hasErrorContaining("    Outer.C is injected at");
              subject.hasErrorContaining("        Outer.CModule.c(c)");
              subject.hasErrorContaining("    Map<String,Outer.C> is injected at");
              subject.hasErrorContaining("        Outer.A(cMap)");
              subject.hasErrorContaining("    Outer.A is injected at");
              subject.hasErrorContaining("        Outer.B(aParam)");
              subject.hasErrorContaining("    Outer.B is injected at");
              subject.hasErrorContaining("        Outer.C(bParam)");
              subject.hasErrorContaining("    Outer.C is injected at");
              subject.hasErrorContaining("        Outer.CModule.c(c)");
              subject.hasErrorContaining("   ...");
              subject.hasErrorContaining("");
              subject.hasErrorContaining("The cycle is requested via:");
              subject.hasErrorContaining("    Outer.C is requested at");
              subject.hasErrorContaining("        Outer.CComponent.getC()")
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
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining("Found a dependency cycle:");
              subject.hasErrorContaining("    Outer.C is injected at");
              subject.hasErrorContaining("        Outer.CModule.c(c)");
              subject.hasErrorContaining("    Set<Outer.C> is injected at");
              subject.hasErrorContaining("        Outer.A(cSet)");
              subject.hasErrorContaining("    Outer.A is injected at");
              subject.hasErrorContaining("        Outer.B(aParam)");
              subject.hasErrorContaining("    Outer.B is injected at");
              subject.hasErrorContaining("        Outer.C(bParam)");
              subject.hasErrorContaining("    Outer.C is injected at");
              subject.hasErrorContaining("        Outer.CModule.c(c)");
              subject.hasErrorContaining("   ...");
              subject.hasErrorContaining("");
              subject.hasErrorContaining("The cycle is requested via:");
              subject.hasErrorContaining("    Outer.C is requested at");
              subject.hasErrorContaining("        Outer.CComponent.getC()")
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
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining("Found a dependency cycle:");
              subject.hasErrorContaining("    Outer.C is injected at");
              subject.hasErrorContaining("        Outer.A(cParam)");
              subject.hasErrorContaining("    Outer.A is injected at");
              subject.hasErrorContaining("        Outer.B(aParam)");
              subject.hasErrorContaining("    Outer.B is injected at");
              subject.hasErrorContaining("        Outer.C(bParam)");
              subject.hasErrorContaining("    Outer.C is injected at");
              subject.hasErrorContaining("        Outer.A(cParam)");
              subject.hasErrorContaining("   ...");
              subject.hasErrorContaining("");
              subject.hasErrorContaining("The cycle is requested via:");
              subject.hasErrorContaining("    Provider<Outer.C> is injected at");
              subject.hasErrorContaining("        Outer.D(cParam)");
              subject.hasErrorContaining("    Outer.D is requested at");
              subject.hasErrorContaining("        Outer.DComponent.getD()")
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
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining("Found a dependency cycle:");
              subject.hasErrorContaining("    String is injected at");
              subject.hasErrorContaining("        CycleModule.object(string)");
              subject.hasErrorContaining("    Object is injected at");
              subject.hasErrorContaining("        CycleModule.string(object)");
              subject.hasErrorContaining("    String is injected at");
              subject.hasErrorContaining("        CycleModule.object(string)");
              subject.hasErrorContaining("    ...");
              subject.hasErrorContaining("");
              subject.hasErrorContaining("The cycle is requested via:");
              subject.hasErrorContaining("    String is requested at");
              subject.hasErrorContaining("        Grandchild.entry()")
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
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining("Found a dependency cycle:");
              subject.hasErrorContaining("    String is injected at");
              subject.hasErrorContaining("        CycleModule.object(string)");
              subject.hasErrorContaining("    Object is injected at");
              subject.hasErrorContaining("        CycleModule.string(object)");
              subject.hasErrorContaining("    String is injected at");
              subject.hasErrorContaining("        CycleModule.object(string)");
              subject.hasErrorContaining("    ...");
              subject.hasErrorContaining("");
              subject.hasErrorContaining("The cycle is requested via:");
              subject.hasErrorContaining("    String is requested at");
              subject.hasErrorContaining("        Child.entry() [Parent → Child]")
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
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining("Found a dependency cycle:");
              subject.hasErrorContaining("    Object is injected at");
              subject.hasErrorContaining("        TestModule.bindQualified(unqualified)");
              subject.hasErrorContaining("    @SomeQualifier Object is injected at");
              subject.hasErrorContaining("        TestModule.bindUnqualified(qualified)");
              subject.hasErrorContaining("    Object is injected at");
              subject.hasErrorContaining("        TestModule.bindQualified(unqualified)");
              subject.hasErrorContaining("    ...");
              subject.hasErrorContaining("");
              subject.hasErrorContaining("The cycle is requested via:");
              subject.hasErrorContaining("    Object is requested at");
              subject.hasErrorContaining("        TestComponent.unqualified()")
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
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining("Found a dependency cycle:");
              subject.hasErrorContaining("    Object is injected at");
              subject.hasErrorContaining("        TestModule.bindToSelf(sameKey)");
              subject.hasErrorContaining("    Object is injected at");
              subject.hasErrorContaining("        TestModule.bindToSelf(sameKey)");
              subject.hasErrorContaining("    ...");
              subject.hasErrorContaining("");
              subject.hasErrorContaining("The cycle is requested via:");
              subject.hasErrorContaining("    Object is requested at");
              subject.hasErrorContaining("        TestComponent.selfReferential()")
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
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining("Found a dependency cycle:");
              subject.hasErrorContaining("    test.B is injected at");
              subject.hasErrorContaining("        test.A.b");
              subject.hasErrorContaining("    test.A is injected at");
              subject.hasErrorContaining("        test.B.a");
              subject.hasErrorContaining("    test.B is injected at");
              subject.hasErrorContaining("        test.A.b");
              subject.hasErrorContaining("    ...");
              subject.hasErrorContaining("");
              subject.hasErrorContaining("The cycle is requested via:");
              subject.hasErrorContaining("    test.B is injected at");
              subject.hasErrorContaining("        test.A.b");
              subject.hasErrorContaining("    test.A is injected at");
              subject.hasErrorContaining("        CycleComponent.inject(test.A)")
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
