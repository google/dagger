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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Producer-specific validation tests. */
@RunWith(JUnit4.class)
public class ProductionGraphValidationTest {
  private static final JavaFileObject EXECUTOR_MODULE =
      JavaFileObjects.forSourceLines(
          "test.ExecutorModule",
          "package test;",
          "",
          "import com.google.common.util.concurrent.MoreExecutors;",
          "import dagger.Module;",
          "import dagger.Provides;",
          "import dagger.producers.Production;",
          "import java.util.concurrent.Executor;",
          "",
          "@Module",
          "class ExecutorModule {",
          "  @Provides @Production Executor executor() {",
          "    return MoreExecutors.directExecutor();",
          "  }",
          "}");

  @Test public void componentWithUnprovidedInput() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.MyComponent",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent(modules = {ExecutorModule.class, FooModule.class})",
        "interface MyComponent {",
        "  ListenableFuture<Foo> getFoo();",
        "}");
    JavaFileObject module = JavaFileObjects.forSourceLines("test.FooModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "class Foo {}",
        "class Bar {}",
        "",
        "@ProducerModule",
        "class FooModule {",
        "  @Produces Foo foo(Bar bar) {",
        "    return null;",
        "  }",
        "}");
    Compilation compilation = daggerCompiler().compile(EXECUTOR_MODULE, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.Bar cannot be provided without an @Inject constructor or an @Provides- or "
                + "@Produces-annotated method.")
        .inFile(component)
        .onLineContaining("interface MyComponent");
  }

  @Test public void componentProductionWithNoDependencyChain() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProductionComponent;",
        "",
        "final class TestClass {",
        "  interface A {}",
        "",
        "  @ProductionComponent(modules = ExecutorModule.class)",
        "  interface AComponent {",
        "    ListenableFuture<A> getA();",
        "  }",
        "}");

    Compilation compilation = daggerCompiler().compile(EXECUTOR_MODULE, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.TestClass.A cannot be provided without an @Provides- or @Produces-annotated "
                + "method.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test public void provisionDependsOnProduction() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.Provides;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.producers.ProductionComponent;",
            "",
            "final class TestClass {",
            "  interface A {}",
            "  interface B {}",
            "",
            "  @ProducerModule(includes = BModule.class)",
            "  final class AModule {",
            "    @Provides A a(B b) {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @ProducerModule",
            "  final class BModule {",
            "    @Produces ListenableFuture<B> b() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @ProductionComponent(modules = {ExecutorModule.class, AModule.class})",
            "  interface AComponent {",
            "    ListenableFuture<A> getA();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(EXECUTOR_MODULE, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.TestClass.A is a provision, which cannot depend on a production.")
        .inFile(component)
        .onLineContaining("interface AComponent");

    compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(EXECUTOR_MODULE, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.TestClass.A is a provision, which cannot depend on a production.")
        .inFile(component)
        .onLineContaining("class AModule");
  }

  @Test public void provisionEntryPointDependsOnProduction() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.producers.ProductionComponent;",
            "",
            "final class TestClass {",
            "  interface A {}",
            "",
            "  @ProducerModule",
            "  static final class AModule {",
            "    @Produces ListenableFuture<A> a() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @ProductionComponent(modules = {ExecutorModule.class, AModule.class})",
            "  interface AComponent {",
            "    A getA();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(EXECUTOR_MODULE, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.TestClass.A is a provision entry-point, which cannot depend on a production.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  public void providingMultibindingWithProductions() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.producers.ProductionComponent;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "",
            "final class TestClass {",
            "  interface A {}",
            "  interface B {}",
            "",
            "  @Module",
            "  static final class AModule {",
            "    @Provides static A a(Map<String, Provider<Object>> map) {",
            "      return null;",
            "    }",
            "",
            "    @Provides @IntoMap @StringKey(\"a\") static Object aEntry() {",
            "      return \"a\";",
            "    }",
            "  }",
            "",
            "  @ProducerModule",
            "  static final class BModule {",
            "    @Produces static B b(A a) {",
            "      return null;",
            "    }",
            "",
            "    @Produces @IntoMap @StringKey(\"b\") static Object bEntry() {",
            "      return \"b\";",
            "    }",
            "  }",
            "",
            "  @ProductionComponent(",
            "      modules = {ExecutorModule.class, AModule.class, BModule.class})",
            "  interface AComponent {",
            "    ListenableFuture<B> b();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(EXECUTOR_MODULE, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.TestClass.A is a provision, which cannot depend on a production")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  public void monitoringDependsOnUnboundType() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.producers.ProductionComponent;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "",
            "final class TestClass {",
            "  interface A {}",
            "",
            "  @Module",
            "  final class MonitoringModule {",
            "    @Provides @IntoSet",
            "    ProductionComponentMonitor.Factory monitorFactory(A unbound) {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @ProducerModule",
            "  final class StringModule {",
            "    @Produces ListenableFuture<String> str() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @ProductionComponent(",
            "    modules = {ExecutorModule.class, MonitoringModule.class, StringModule.class}",
            "  )",
            "  interface StringComponent {",
            "    ListenableFuture<String> getString();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(EXECUTOR_MODULE, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.TestClass.A cannot be provided without an @Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface StringComponent");
  }

  @Test
  public void monitoringDependsOnProduction() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.producers.ProductionComponent;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "",
            "final class TestClass {",
            "  interface A {}",
            "",
            "  @Module",
            "  final class MonitoringModule {",
            "    @Provides @IntoSet ProductionComponentMonitor.Factory monitorFactory(A a) {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @ProducerModule",
            "  final class StringModule {",
            "    @Produces A a() {",
            "      return null;",
            "    }",
            "",
            "    @Produces ListenableFuture<String> str() {",
            "      return null;",
            "    }",
            "  }",
            "",
            "  @ProductionComponent(",
            "    modules = {ExecutorModule.class, MonitoringModule.class, StringModule.class}",
            "  )",
            "  interface StringComponent {",
            "    ListenableFuture<String> getString();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(EXECUTOR_MODULE, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.util.Set<dagger.producers.monitoring.ProductionComponentMonitor.Factory>"
                + " test.TestClass.MonitoringModule#monitorFactory is a provision,"
                + " which cannot depend on a production.")
        .inFile(component)
        .onLineContaining("interface StringComponent");
  }

  @Test
  public void cycleNotBrokenByMap() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProductionComponent;",
            "",
            "@ProductionComponent(modules = {ExecutorModule.class, TestModule.class})",
            "interface TestComponent {",
            "  ListenableFuture<String> string();",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@ProducerModule",
            "final class TestModule {",
            "  @Produces static String string(Map<String, String> map) {",
            "    return \"string\";",
            "  }",
            "",
            "  @Produces @IntoMap @StringKey(\"key\")",
            "  static String entry(String string) {",
            "    return string;",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(EXECUTOR_MODULE, component, module);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("cycle")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void cycleNotBrokenByProducerMap() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProductionComponent;",
            "",
            "@ProductionComponent(modules = {ExecutorModule.class, TestModule.class})",
            "interface TestComponent {",
            "  ListenableFuture<String> string();",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.producers.Producer;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.multibindings.StringKey;",
            "import dagger.multibindings.IntoMap;",
            "import java.util.Map;",
            "",
            "@ProducerModule",
            "final class TestModule {",
            "  @Produces static String string(Map<String, Producer<String>> map) {",
            "    return \"string\";",
            "  }",
            "",
            "  @Produces @IntoMap @StringKey(\"key\")",
            "  static String entry(String string) {",
            "    return string;",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(EXECUTOR_MODULE, component, module);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("cycle")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }
  
  @Test
  public void componentWithBadModule() {
    JavaFileObject badModule =
        JavaFileObjects.forSourceLines(
            "test.BadModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.multibindings.Multibinds;",
            "import dagger.Module;",
            "import java.util.Set;",
            "",
            "@Module",
            "abstract class BadModule {",
            "  @Multibinds",
            "  @BindsOptionalOf",
            "  abstract Set<String> strings();",
            "}");
    JavaFileObject badComponent =
        JavaFileObjects.forSourceLines(
            "test.BadComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Optional;",
            "import java.util.Set;",
            "",
            "@Component(modules = BadModule.class)",
            "interface BadComponent {",
            "  Set<String> strings();",
            "  Optional<Set<String>> optionalStrings();",
            "}");
    Compilation compilation = daggerCompiler().compile(badModule, badComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.BadModule has errors")
        .inFile(badComponent)
        .onLine(7);
  }
}
