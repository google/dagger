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

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

/**
 * Unit tests for {@link BindingGraphValidator} that exercise producer-specific logic.
 */
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
    assertAbout(javaSources()).that(ImmutableList.of(EXECUTOR_MODULE, module, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("test.Bar cannot be provided without an @Inject constructor or from "
            + "an @Provides- or @Produces-annotated method.")
            .in(component).onLine(8);
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
    String expectedError =
        "test.TestClass.A cannot be provided without an @Provides- or @Produces-annotated method.";
    assertAbout(javaSources()).that(ImmutableList.of(EXECUTOR_MODULE, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(11);
  }

  @Test public void provisionDependsOnProduction() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "import dagger.producers.ProductionComponent;",
        "",
        "final class TestClass {",
        "  interface A {}",
        "  interface B {}",
        "",
        "  @Module",
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
        "  @ProductionComponent(modules = {ExecutorModule.class, AModule.class, BModule.class})",
        "  interface AComponent {",
        "    ListenableFuture<A> getA();",
        "  }",
        "}");
    String expectedError =
        "test.TestClass.A is a provision, which cannot depend on a production.";
    assertAbout(javaSources()).that(ImmutableList.of(EXECUTOR_MODULE, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(30);
  }

  @Test public void provisionEntryPointDependsOnProduction() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
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
        "  final class AModule {",
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
    String expectedError =
        "test.TestClass.A is a provision entry-point, which cannot depend on a production.";
    assertAbout(javaSources()).that(ImmutableList.of(EXECUTOR_MODULE, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(20);
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
    String expectedError =
        "test.TestClass.A cannot be provided without an @Provides-annotated method.";
    assertAbout(javaSources()).that(ImmutableList.of(EXECUTOR_MODULE, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError)
        .in(component)
        .onLine(34);
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
    String expectedError =
        "@Provides @dagger.multibindings.IntoSet"
            + " dagger.producers.monitoring.ProductionComponentMonitor.Factory"
            + " test.TestClass.MonitoringModule.monitorFactory(test.TestClass.A) is a provision,"
            + " which cannot depend on a production.";
    assertAbout(javaSources()).that(ImmutableList.of(EXECUTOR_MODULE, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError)
        .in(component)
        .onLine(37);
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
    assertAbout(javaSources())
        .that(ImmutableList.of(EXECUTOR_MODULE, component, module))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("cycle")
        .in(component)
        .onLine(8);
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
    assertAbout(javaSources())
        .that(ImmutableList.of(EXECUTOR_MODULE, component, module))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("cycle")
        .in(component)
        .onLine(8);
  }
}
