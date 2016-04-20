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
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

@RunWith(JUnit4.class)
public class ProductionComponentProcessorTest {
  @Test public void componentOnConcreteClass() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent",
        "final class NotAComponent {}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("interface");
  }

  @Test public void componentOnEnum() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent",
        "enum NotAComponent {",
        "  INSTANCE",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("interface");
  }

  @Test public void componentOnAnnotation() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent",
        "@interface NotAComponent {}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("interface");
  }

  @Test public void nonModuleModule() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent(modules = Object.class)",
        "interface NotAComponent {}");
    assertAbout(javaSource())
        .that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("is not annotated with one of @Module, @ProducerModule");
  }

  @Test
  public void builderWithExecutorAndProvidedExecutor() {
    JavaFileObject moduleFile =
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
            "final class ExecutorModule {",
            "  @Provides @Production Executor executor() {",
            "    return MoreExecutors.directExecutor();",
            "  }",
            "}");
    JavaFileObject producerModuleFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "",
            "@ProducerModule",
            "final class SimpleModule {",
            "  @Produces String str() {",
            "    return \"\";",
            "  }",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProductionComponent;",
            "import java.util.concurrent.Executor;",
            "",
            "@ProductionComponent(modules = {ExecutorModule.class, SimpleModule.class})",
            "interface SimpleComponent {",
            "  ListenableFuture<String> str();",
            "",
            "  @ProductionComponent.Builder",
            "  interface Builder {",
            "    Builder executor(Executor executor);",
            "    SimpleComponent build();",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(moduleFile, producerModuleFile, componentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("is bound multiple times");
  }

  @Test
  public void dependsOnProductionExecutor() {
    JavaFileObject moduleFile =
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
            "final class ExecutorModule {",
            "  @Provides @Production Executor executor() {",
            "    return MoreExecutors.directExecutor();",
            "  }",
            "}");
    JavaFileObject producerModuleFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.producers.Production;",
            "import java.util.concurrent.Executor;",
            "",
            "@ProducerModule",
            "final class SimpleModule {",
            "  @Produces String str(@Production Executor executor) {",
            "    return \"\";",
            "  }",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProductionComponent;",
            "import java.util.concurrent.Executor;",
            "",
            "@ProductionComponent(modules = {ExecutorModule.class, SimpleModule.class})",
            "interface SimpleComponent {",
            "  ListenableFuture<String> str();",
            "",
            "  @ProductionComponent.Builder",
            "  interface Builder {",
            "    SimpleComponent build();",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(moduleFile, producerModuleFile, componentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("may not depend on the production executor");
  }

  @Test public void simpleComponent() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "import dagger.producers.ProductionComponent;",
        "import javax.inject.Inject;",
        "",
        "final class TestClass {",
        "  static final class C {",
        "    @Inject C() {}",
        "  }",
        "",
        "  interface A {}",
        "  interface B {}",
        "",
        "  @Module",
        "  static final class BModule {",
        "    @Provides B b(C c) {",
        "      return null;",
        "    }",
        "  }",
        "",
        "  @ProducerModule",
        "  static final class AModule {",
        "    @Produces ListenableFuture<A> a(B b) {",
        "      return null;",
        "    }",
        "  }",
        "",
        "  @ProductionComponent(modules = {AModule.class, BModule.class})",
        "  interface SimpleComponent {",
        "    ListenableFuture<A> a();",
        "  }",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestClass_SimpleComponent",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.internal.DoubleCheck;",
            "import dagger.internal.InstanceFactory;",
            "import dagger.internal.Preconditions;",
            "import dagger.internal.SetFactory;",
            "import dagger.producers.Producer;",
            "import dagger.producers.internal.Producers;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.concurrent.Executor;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestClass_SimpleComponent",
            "    implements TestClass.SimpleComponent {",
            "  private Provider<Executor> simpleComponentProvider;",
            "  private Provider<Executor> executorProvider;",
            "  private Provider<TestClass.SimpleComponent> simpleComponentProvider2;",
            "  private Provider<ProductionComponentMonitor> monitorProvider;",
            "  private Provider<TestClass.B> bProvider;",
            "  private Producer<TestClass.A> aProducer;",
            "",
            "  private DaggerTestClass_SimpleComponent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.simpleComponentProvider =",
            "        InstanceFactory.<Executor>create(builder.executor);",
            "    this.executorProvider =",
            "        DoubleCheck.provider(",
            "            TestClass$SimpleComponent_ProductionExecutorModule_ExecutorFactory",
            "                .create(simpleComponentProvider));",
            "    this.simpleComponentProvider2 =",
            "        InstanceFactory.<TestClass.SimpleComponent>create(this);",
            "    this.monitorProvider =",
            "        DoubleCheck.provider(",
            "            TestClass$SimpleComponent_MonitoringModule_MonitorFactory.create(",
            "                simpleComponentProvider2,",
            "                SetFactory.<ProductionComponentMonitor.Factory>create());",
            "    this.bProvider = TestClass$BModule_BFactory.create(",
            "        builder.bModule, TestClass$C_Factory.create());",
            "    this.aProducer = new TestClass$AModule_AFactory(",
            "        builder.aModule,",
            "        executorProvider,",
            "        monitorProvider,",
            "        Producers.producerFromProvider(bProvider));",
            "  }",
            "",
            "  @Override",
            "  public ListenableFuture<TestClass.A> a() {",
            "    return aProducer.get();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private TestClass.BModule bModule;",
            "    private TestClass.AModule aModule;",
            "    private Executor executor;",
            "",
            "    private Builder() {",
            "    }",
            "",
            "    public TestClass.SimpleComponent build() {",
            "      if (bModule == null) {",
            "        this.bModule = new TestClass.BModule();",
            "      }",
            "      if (aModule == null) {",
            "        this.aModule = new TestClass.AModule();",
            "      }",
            "      if (executor == null) {",
            "        throw new IllegalStateException(Executor.class.getCanonicalName()",
            "            + \" must be set\");",
            "      }",
            "      return new DaggerTestClass_SimpleComponent(this);",
            "    }",
            "",
            "    public Builder aModule(TestClass.AModule aModule) {",
            "      this.aModule = Preconditions.checkNotNull(aModule);",
            "      return this;",
            "    }",
            "",
            "    public Builder bModule(TestClass.BModule bModule) {",
            "      this.bModule = Preconditions.checkNotNull(bModule);",
            "      return this;",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder testClass$SimpleComponent_MonitoringModule(",
            "        TestClass$SimpleComponent_MonitoringModule",
            "        testClass$SimpleComponent_MonitoringModule) {",
            "      Preconditions.checkNotNull(testClass$SimpleComponent_MonitoringModule);",
            "      return this;",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder testClass$SimpleComponent_ProductionExecutorModule(",
            "        TestClass$SimpleComponent_ProductionExecutorModule",
            "        testClass$SimpleComponent_ProductionExecutorModule) {",
            "      Preconditions.checkNotNull(testClass$SimpleComponent_ProductionExecutorModule);",
            "      return this;",
            "    }",
            "",
            "    public Builder executor(Executor executor) {",
            "      this.executor = Preconditions.checkNotNull(executor);",
            "      return this;",
            "    }",
            "  }",
            "}");
    assertAbout(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
  }

  @Test public void nullableProducersAreNotErrors() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "import dagger.producers.ProductionComponent;",
        "import javax.annotation.Nullable;",
        "import javax.inject.Inject;",
        "",
        "final class TestClass {",
        "  interface A {}",
        "  interface B {}",
        "  interface C {}",
        "",
        "  @Module",
        "  static final class CModule {",
        "    @Provides @Nullable C c() {",
        "      return null;",
        "    }",
        "  }",
        "",
        "  @ProducerModule",
        "  static final class ABModule {",
        "    @Produces @Nullable B b(@Nullable C c) {",
        "      return null;",
        "    }",

        "    @Produces @Nullable ListenableFuture<A> a(B b) {",  // NOTE: B not injected as nullable
        "      return null;",
        "    }",
        "  }",
        "",
        "  @ProductionComponent(modules = {ABModule.class, CModule.class})",
        "  interface SimpleComponent {",
        "    ListenableFuture<A> a();",
        "  }",
        "}");
    assertAbout(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .withWarningContaining("@Nullable on @Produces methods does not do anything")
        .in(component)
        .onLine(26)
        .and()
        .withWarningContaining("@Nullable on @Produces methods does not do anything")
        .in(component)
        .onLine(29);
  }
}
