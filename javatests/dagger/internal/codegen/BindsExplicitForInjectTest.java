/*
 * Copyright (C) 2026 The Dagger Authors.
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

import static dagger.internal.codegen.extension.DaggerCollectors.onlyElement;

import androidx.room3.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.spi.model.Binding;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraphPlugin;
import dagger.spi.model.BindingKind;
import dagger.spi.model.DaggerTypeElement;
import dagger.spi.model.DiagnosticReporter;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class BindsExplicitForInjectTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public BindsExplicitForInjectTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final Source FOO =
      CompilerTests.javaSource(
          "test.Foo",
          "package test;",
          "import javax.inject.Inject;",
          "class Foo {",
          "  @Inject Foo() {}",
          "}");

  @Test
  public void testScopedBinds_fails() {
    Source scopedModule =
        CompilerTests.javaSource(
            "test.ScopedModule",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import javax.inject.Singleton;",
            "@Module",
            "interface ScopedModule {",
            "  @Binds",
            "  @Singleton",
            "  Foo bindFoo();",
            "}");
    Source scopedComponent =
        CompilerTests.javaSource(
            "test.ScopedComponent",
            "package test;",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "@Singleton",
            "@Component(modules = ScopedModule.class)",
            "interface ScopedComponent {",
            "  Foo getFoo();",
            "}");
    CompilerTests.daggerCompiler(FOO, scopedModule, scopedComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("Parameterless @Binds methods may not be scoped.");
            });
  }

  @Test
  public void testUnscopedBinds_succeeds() {
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import javax.inject.Inject;",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    Source unscopedModule =
        CompilerTests.javaSource(
            "test.UnscopedModule",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "@Module",
            "interface UnscopedModule {",
            "  @Binds",
            "  Bar bindBar();",
            "}");
    Source unscopedComponent =
        CompilerTests.javaSource(
            "test.UnscopedComponent",
            "package test;",
            "import dagger.Component;",
            "@Component(modules = UnscopedModule.class)",
            "interface UnscopedComponent {",
            "  Bar getBar();",
            "}");
    CompilerTests.daggerCompiler(bar, unscopedModule, unscopedComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerUnscopedComponent"));
              subject.generatedSource(
                  goldenFileRule.goldenSource(
                      "test/Bar_Factory", compilerMode.isKotlinCodegenEnabled()));
            });
  }

  @Test
  public void testQualifiedBinds_fails() {
    Source qualifiedModule =
        CompilerTests.javaSource(
            "test.QualifiedModule",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import javax.inject.Qualifier;",
            "@Qualifier @interface MyQualifier {}",
            "@Module",
            "interface QualifiedModule {",
            "  @Binds",
            "  @MyQualifier",
            "  Foo bindFoo();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "import dagger.Component;",
            "@Component(modules = QualifiedModule.class)",
            "interface TestComponent {",
            "  @MyQualifier Foo getFoo();",
            "}");
    CompilerTests.daggerCompiler(FOO, qualifiedModule, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("Parameterless @Binds methods may not have qualifiers.");
            });
  }

  @Test
  public void testScopedInject_fails() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "@Singleton",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "@Module",
            "interface TestModule {",
            "  @Binds",
            "  Foo bindFoo();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "@Singleton",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Foo getFoo();",
            "}");
    CompilerTests.daggerCompiler(foo, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "Parameterless @Binds methods cannot bind types with scoped @Inject"
                      + " constructors.");
            });
  }

  @Test
  public void testImplicitAndExplicitInDifferentComponents_succeeds() {
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import javax.inject.Inject;",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    Source unscopedModule =
        CompilerTests.javaSource(
            "test.UnscopedModule",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "@Module",
            "interface UnscopedModule {",
            "  @Binds",
            "  Bar bindBar();",
            "}");
    // This component uses the explicit @Binds binding from UnscopedModule
    Source explicitComponent =
        CompilerTests.javaSource(
            "test.ExplicitComponent",
            "package test;",
            "import dagger.Component;",
            "@Component(modules = UnscopedModule.class)",
            "interface ExplicitComponent {",
            "  Bar getBar();",
            "}");
    // This component does not install UnscopedModule, so it will use implicit @Inject
    Source implicitComponent =
        CompilerTests.javaSource(
            "test.ImplicitComponent",
            "package test;",
            "import dagger.Component;",
            "@Component",
            "interface ImplicitComponent {",
            "  Bar getBar();",
            "}");
    CompilerTests.daggerCompiler(bar, unscopedModule, explicitComponent, implicitComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
            });
  }

  @Test
  public void testExplicitBindsAndImplicitInjectDependency_succeeds() {
    Source bar = CompilerTests.javaSource("test.Bar", "package test;", "class Bar {}");
    Source module1 =
        CompilerTests.javaSource(
            "test.Module1",
            "package test;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "@Module",
            "class Module1 {",
            "  @Provides Bar provideBar(Foo foo) { return new Bar(); }",
            "}");
    Source module2 =
        CompilerTests.javaSource(
            "test.Module2",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "@Module",
            "interface Module2 {",
            "  @Binds",
            "  Foo bindFoo();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "import dagger.Component;",
            "@Component(modules = {Module1.class, Module2.class})",
            "interface TestComponent {",
            "  Foo getFoo();",
            "  Bar getBar();",
            "}");
    CompilerTests.daggerCompiler(FOO, bar, module1, module2, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
            });
  }

  @Test
  public void testBindInSubcomponentAndUsageInComponentAndSubcomponent_fails() {
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "@Module",
            "interface ChildModule {",
            "  @Binds",
            "  Foo bindFoo();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "import dagger.Component;",
            "import dagger.Subcomponent;",
            "@Component",
            "interface TestComponent {",
            "  Foo getFoo();",
            "  ChildComponent subcomponent();",
            "}");
    Source childComponent =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "import dagger.Subcomponent;",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {",
            "  Foo getFoo();",
            "}");
    CompilerTests.daggerCompiler(FOO, childModule, component, childComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Foo is bound multiple times");
            });
  }

  @Test
  public void testBindInSubcomponentAndUsageOnlyInComponent_succeeds() {
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "@Module",
            "interface ChildModule {",
            "  @Binds",
            "  Foo bindFoo();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "import dagger.Component;",
            "import dagger.Subcomponent;",
            "@Component",
            "interface TestComponent {",
            "  Foo getFoo();",
            "  ChildComponent subcomponent();",
            "}");
    Source childComponent =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "import dagger.Subcomponent;",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {}");
    CompilerTests.daggerCompiler(FOO, childModule, component, childComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
            });
  }

  @Test
  public void testOverriddenInjectWithProvides_fails() {
    Source module1 =
        CompilerTests.javaSource(
            "test.Module1",
            "package test;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "@Module",
            "class Module1 {",
            "  @Provides Foo provideFoo() { return new Foo(); }",
            "}");
    Source module2 =
        CompilerTests.javaSource(
            "test.Module2",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "@Module",
            "interface Module2 {",
            "  @Binds",
            "  Foo bindFoo();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "import dagger.Component;",
            "@Component(modules = {Module1.class, Module2.class})",
            "interface TestComponent {",
            "  Foo getFoo();",
            "}");
    CompilerTests.daggerCompiler(FOO, module1, module2, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Foo is bound multiple times");
            });
  }

  @Test
  public void testTwoExplicitBinds_fails() {
    Source module1 =
        CompilerTests.javaSource(
            "test.Module1",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "@Module",
            "interface Module1 {",
            "  @Binds",
            "  Foo bindFoo();",
            "}");
    Source module2 =
        CompilerTests.javaSource(
            "test.Module2",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "@Module",
            "interface Module2 {",
            "  @Binds",
            "  Foo bindFoo();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "import dagger.Component;",
            "@Component(modules = {Module1.class, Module2.class})",
            "interface TestComponent {",
            "  Foo getFoo();",
            "}");
    CompilerTests.daggerCompiler(FOO, module1, module2, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Foo is bound multiple times");
            });
  }

  @Test
  public void testGenericInjectBinds_fails() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import javax.inject.Inject;",
            "class Foo<T> {",
            "  @Inject Foo() {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "@Module",
            "interface TestModule {",
            "  @Binds",
            "  Foo<String> bindFoo();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "import dagger.Component;",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Foo<String> getFoo();",
            "}");
    CompilerTests.daggerCompiler(foo, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "Parameterless @Binds methods cannot return a parameterized type.");
            });
  }

  @Test
  public void testIntoSetBinds_fails() {
    Source testModule =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "@Module",
            "interface TestModule {",
            "  @Binds",
            "  @IntoSet",
            "  Foo bindFoo();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "import dagger.Component;",
            "import java.util.Set;",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Set<Foo> getFoo();",
            "}");
    CompilerTests.daggerCompiler(FOO, testModule, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "Parameterless @Binds methods cannot be used with multibinding annotations");
            });
  }

  @Test
  public void testMapKeyBinds_fails() {
    Source testModule =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.MapKey;",
            "@MapKey",
            "@interface MyMapKey {",
            "  String value();",
            "}",
            "@Module",
            "interface TestModule {",
            "  @Binds",
            "  @MyMapKey(\"foo\")",
            "  Foo bindFoo();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "import dagger.Component;",
            "import java.util.Map;",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Map<String, Foo> getFoo();",
            "}");
    CompilerTests.daggerCompiler(FOO, testModule, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(3);
              subject.hasErrorContaining("Parameterless @Binds methods cannot have a @MapKey.");
              subject.hasErrorContaining("@Binds methods of non map type cannot declare a map key");
            });
  }

  @Test
  public void testGenericInjectConstructor_fails() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import javax.inject.Inject;",
            "class Foo<T> {",
            "  @Inject Foo() {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "import dagger.Binds;",
            "import dagger.Module;",
            "@Module",
            "interface TestModule {",
            "  @Binds",
            "  Foo bindFoo();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "import dagger.Component;",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Foo<String> getFoo();",
            "}");
    CompilerTests.daggerCompiler(foo, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "Parameterless @Binds methods cannot bind generic types with @Inject"
                      + " constructors.");
            });
  }

  static class MetadataInspector implements BindingGraphPlugin {
    public static final AtomicReference<Binding> foundBinding = new AtomicReference<>();
    public static final AtomicReference<DaggerTypeElement> expectedModule = new AtomicReference<>();

    @Override
    public void visitGraph(BindingGraph graph, DiagnosticReporter diagnosticReporter) {
      if (graph.isFullBindingGraph()) {
        return;
      }
      Binding binding =
          graph.bindings().stream()
              .filter(
                  b ->
                      b.key().toString().equals("test.FooImpl")
                          && b.kind() == BindingKind.INJECTION)
              .collect(onlyElement());
      foundBinding.set(binding);
      expectedModule.set(
          binding
              .contributingModule()
              .orElseThrow(
                  () -> new IllegalStateException("Binding should have contributing module")));
    }
  }
}
