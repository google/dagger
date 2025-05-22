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

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Producer-specific validation tests. */
@RunWith(Parameterized.class)
public class ProductionGraphValidationTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ProductionGraphValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final Source EXECUTOR_MODULE =
      CompilerTests.javaSource(
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
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProductionComponent;",
            "",
            "@ProductionComponent(modules = {ExecutorModule.class, FooModule.class})",
            "interface MyComponent {",
            "  ListenableFuture<Foo> getFoo();",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.FooModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "",
            "@ProducerModule",
            "class FooModule {",
            "  @Produces Foo foo(Bar bar) {",
            "    return null;",
            "  }",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "class Foo {}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "class Bar {}");
    CompilerTests.daggerCompiler(EXECUTOR_MODULE, module, component, foo, bar)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Bar cannot be provided without an @Inject constructor or an @Provides- or "
                          + "@Produces-annotated method.")
                  .onSource(component)
                  .onLineContaining("interface MyComponent");
            });
  }

  @Test public void componentProductionWithNoDependencyChain() {
    Source component =
        CompilerTests.javaSource(
            "test.TestClass",
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

    CompilerTests.daggerCompiler(EXECUTOR_MODULE, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "TestClass.A cannot be provided without an @Provides- or @Produces-annotated "
                          + "method.")
                  .onSource(component)
                  .onLineContaining("interface AComponent");
            });
  }

  @Test public void provisionDependsOnProduction() {
    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(EXECUTOR_MODULE, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "TestClass.A is a provision, which cannot depend on a production.")
                  .onSource(component)
                  .onLineContaining("interface AComponent");
            });

    CompilerTests.daggerCompiler(EXECUTOR_MODULE, component)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.fullBindingGraphValidation", "ERROR")
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "TestClass.A is a provision, which cannot depend on a production.")
                  .onSource(component)
                  .onLineContaining("class AModule");
            });
  }

  @Test public void provisionEntryPointDependsOnProduction() {
    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(EXECUTOR_MODULE, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "TestClass.A is a provision entry-point, which cannot depend on a "
                          + "production.")
                  .onSource(component)
                  .onLineContaining("interface AComponent");
            });
  }

  @Test
  public void providingMultibindingWithProductions() {
    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(EXECUTOR_MODULE, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "TestClass.A is a provision, which cannot depend on a production")
                  .onSource(component)
                  .onLineContaining("interface AComponent");
            });
  }

  @Test
  public void monitoringDependsOnUnboundType() {
    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(EXECUTOR_MODULE, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "TestClass.A cannot be provided without an @Provides-annotated method.")
                  .onSource(component)
                  .onLineContaining("interface StringComponent");
            });
  }

  @Test
  public void monitoringDependsOnProduction() {
    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(EXECUTOR_MODULE, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Set<ProductionComponentMonitor.Factory>"
                          + " TestClass.MonitoringModule#monitorFactory is a provision,"
                          + " which cannot depend on a production.")
                  .onSource(component)
                  .onLineContaining("interface StringComponent");
            });
  }

  @Test
  public void cycleNotBrokenByMap() {
    Source component =
        CompilerTests.javaSource(
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
    Source module =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(EXECUTOR_MODULE, component, module)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("cycle")
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void cycleNotBrokenByProducerMap() {
    Source component =
        CompilerTests.javaSource(
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
    Source module =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(EXECUTOR_MODULE, component, module)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("cycle")
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void componentWithBadModule() {
    Source badModule =
        CompilerTests.javaSource(
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
    Source badComponent =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(badModule, badComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                      "strings is annotated with more than one of (dagger.Provides, "
                          + "dagger.producers.Produces, dagger.Binds, "
                          + "dagger.multibindings.Multibinds, dagger.BindsOptionalOf)")
                  .onSource(badModule)
                  .onLine(12);
              subject.hasErrorContaining("test.BadModule has errors")
                  .onSource(badComponent)
                  .onLine(7);
            });
  }
}
