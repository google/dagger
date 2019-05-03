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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProductionComponentProcessorTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ProductionComponentProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test public void componentOnConcreteClass() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent",
        "final class NotAComponent {}");
    Compilation compilation = daggerCompiler().compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test public void componentOnAnnotation() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent",
        "@interface NotAComponent {}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test public void nonModuleModule() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent(modules = Object.class)",
        "interface NotAComponent {}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("is not annotated with one of @Module, @ProducerModule");
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
    Compilation compilation =
        daggerCompiler()
            .compile(moduleFile, producerModuleFile, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("java.lang.String may not depend on the production executor")
        .inFile(componentFile)
        .onLineContaining("interface SimpleComponent");

    compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(producerModuleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("java.lang.String may not depend on the production executor")
        .inFile(producerModuleFile)
        .onLineContaining("class SimpleModule");
    // TODO(dpb): Report at the binding if enclosed in the module.
  }

  @Test
  public void simpleComponent() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import com.google.common.util.concurrent.MoreExecutors;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.producers.Production;",
            "import dagger.producers.ProductionComponent;",
            "import java.util.concurrent.Executor;",
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
            "",
            "    @Provides @Production Executor executor() {",
            "      return MoreExecutors.directExecutor();",
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
    JavaFileObject generatedComponent;
    switch (compilerMode) {
      case FAST_INIT_MODE:
        generatedComponent =
            JavaFileObjects.forSourceLines(
                "test.DaggerTestClass_SimpleComponent",
                "package test;",
                "",
                "import com.google.common.util.concurrent.ListenableFuture;",
                "import dagger.internal.DoubleCheck;",
                "import dagger.internal.InstanceFactory;",
                "import dagger.internal.MemoizedSentinel;",
                "import dagger.internal.Preconditions;",
                "import dagger.internal.SetFactory;",
                "import dagger.producers.Producer;",
                "import dagger.producers.internal.CancellationListener;",
                "import dagger.producers.internal.Producers;",
                "import dagger.producers.monitoring.ProductionComponentMonitor;",
                "import java.util.concurrent.Executor;",
                IMPORT_GENERATED_ANNOTATION,
                "import javax.inject.Provider;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestClass_SimpleComponent",
                "    implements TestClass.SimpleComponent, CancellationListener {",
                "  private final TestClass.BModule bModule;",
                "  private volatile Object productionImplementationExecutor =",
                "      new MemoizedSentinel();",
                "  private volatile Provider<Executor> productionImplementationExecutorProvider;",
                "  private volatile Object productionComponentMonitor = new MemoizedSentinel();",
                "  private volatile Provider<ProductionComponentMonitor> monitorProvider;",
                "  private volatile Provider<TestClass.B> bProvider;",
                "  private Producer<TestClass.A> aEntryPoint;",
                "  private Provider<TestClass.SimpleComponent> simpleComponentProvider;",
                "  private Producer<TestClass.B> bProducer;",
                "  private Producer<TestClass.A> aProducer;",
                "",
                "  private DaggerTestClass_SimpleComponent(",
                "      TestClass.AModule aModuleParam,",
                "      TestClass.BModule bModuleParam) {",
                "    this.bModule = bModuleParam;",
                "    initialize(aModuleParam, bModuleParam);",
                "  }",
                "",
                "  public static Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  public static TestClass.SimpleComponent create() {",
                "    return new Builder().build();",
                "  }",
                "",
                "  private Executor getProductionImplementationExecutor() {",
                "    Object local = productionImplementationExecutor;",
                "    if (local instanceof MemoizedSentinel) {",
                "      synchronized (local) {",
                "        local = productionImplementationExecutor;",
                "        if (local instanceof MemoizedSentinel) {",
                "          local =",
                "              TestClass_BModule_ExecutorFactory.executor(bModule);",
                "          productionImplementationExecutor =",
                "              DoubleCheck.reentrantCheck(",
                "                  productionImplementationExecutor, local);",
                "        }",
                "      }",
                "    }",
                "    return (Executor) local;",
                "  }",
                "",
                "  private Provider<Executor> getProductionImplementationExecutorProvider() {",
                "    Object local = productionImplementationExecutorProvider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(0);",
                "      productionImplementationExecutorProvider = (Provider<Executor>) local;",
                "    }",
                "    return (Provider<Executor>) local;",
                "  }",
                "",
                "  private ProductionComponentMonitor getProductionComponentMonitor() {",
                "    Object local = productionComponentMonitor;",
                "    if (local instanceof MemoizedSentinel) {",
                "      synchronized (local) {",
                "        local = productionComponentMonitor;",
                "        if (local instanceof MemoizedSentinel) {",
                "          local =",
                "              TestClass_SimpleComponent_MonitoringModule_MonitorFactory",
                "                  .monitor(",
                "                      simpleComponentProvider,",
                "                      SetFactory.<ProductionComponentMonitor.Factory>empty());",
                "          productionComponentMonitor =",
                "              DoubleCheck.reentrantCheck(",
                "                  productionComponentMonitor, local);",
                "        }",
                "      }",
                "    }",
                "    return (ProductionComponentMonitor) local;",
                "  }",
                "",
                "  private Provider<ProductionComponentMonitor>",
                "      getProductionComponentMonitorProvider() {",
                "      Object local = monitorProvider;",
                "      if (local == null) {",
                "        local = new SwitchingProvider<>(1);",
                "        monitorProvider = (Provider<ProductionComponentMonitor>) local;",
                "      }",
                "      return (Provider<ProductionComponentMonitor>) local;",
                "  }",
                "",
                "  private TestClass.B getB() {",
                "    return TestClass_BModule_BFactory.b(bModule, new TestClass.C());",
                "  }",
                "",
                "  private Provider<TestClass.B> getBProvider() {",
                "    Object local = bProvider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(2);",
                "      bProvider = (Provider<TestClass.B>) local;",
                "    }",
                "    return (Provider<TestClass.B>) local;",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(",
                "      final TestClass.AModule aModuleParam,",
                "      final TestClass.BModule bModuleParam) {",
                "    this.simpleComponentProvider =",
                "        InstanceFactory.create((TestClass.SimpleComponent) this);",
                "    this.bProducer = Producers.producerFromProvider(getBProvider());",
                "    this.aProducer =",
                "        TestClass_AModule_AFactory.create(",
                "            aModuleParam,",
                "            getProductionImplementationExecutorProvider(),",
                "            getProductionComponentMonitorProvider(),",
                "            bProducer);",
                "    this.aEntryPoint = Producers.entryPointViewOf(aProducer, this);",
                "  }",
                "",
                "  @Override",
                "  public ListenableFuture<TestClass.A> a() {",
                "    return aEntryPoint.get();",
                "  }",
                "",
                "  @Override",
                "  public void onProducerFutureCancelled(boolean mayInterruptIfRunning) {",
                "    Producers.cancel(aProducer, mayInterruptIfRunning);",
                "    Producers.cancel(bProducer, mayInterruptIfRunning);",
                "  }",
                "",
                "  static final class Builder {",
                "    private TestClass.AModule aModule;",
                "    private TestClass.BModule bModule;",
                "",
                "    private Builder() {}",
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
                "    public TestClass.SimpleComponent build() {",
                "      if (aModule == null) {",
                "        this.aModule = new TestClass.AModule();",
                "      }",
                "      if (bModule == null) {",
                "        this.bModule = new TestClass.BModule();",
                "      }",
                "      return new DaggerTestClass_SimpleComponent(aModule, bModule);",
                "    }",
                "  }",
                "",
                "  private final class SwitchingProvider<T> implements Provider<T> {",
                "    private final int id;",
                "",
                "    SwitchingProvider(int id) {",
                "      this.id = id;",
                "    }",
                "",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: return (T) DaggerTestClass_SimpleComponent.this",
                "            .getProductionImplementationExecutor();",
                "        case 1: return (T)",
                "            DaggerTestClass_SimpleComponent.this.getProductionComponentMonitor();",
                "        case 2: return (T)",
                "            DaggerTestClass_SimpleComponent.this.getB();",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }",
                "}");
        break;
      default:
        generatedComponent =
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
                "import dagger.producers.internal.CancellationListener;",
                "import dagger.producers.internal.Producers;",
                "import dagger.producers.monitoring.ProductionComponentMonitor;",
                "import java.util.concurrent.Executor;",
                IMPORT_GENERATED_ANNOTATION,
                "import javax.inject.Provider;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestClass_SimpleComponent",
                "    implements TestClass.SimpleComponent, CancellationListener {",
                "  private Producer<TestClass.A> aEntryPoint;",
                "  private Provider<Executor> executorProvider;",
                "  private Provider<Executor> productionImplementationExecutorProvider;",
                "  private Provider<TestClass.SimpleComponent> simpleComponentProvider;",
                "  private Provider<ProductionComponentMonitor> monitorProvider;",
                "  private Provider<TestClass.B> bProvider;",
                "  private Producer<TestClass.B> bProducer;",
                "  private Producer<TestClass.A> aProducer;",
                "",
                "  private DaggerTestClass_SimpleComponent(",
                "      TestClass.AModule aModuleParam,",
                "      TestClass.BModule bModuleParam) {",
                "    initialize(aModuleParam, bModuleParam);",
                "  }",
                "",
                "  public static Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  public static TestClass.SimpleComponent create() {",
                "    return new Builder().build();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(",
                "      final TestClass.AModule aModuleParam,",
                "      final TestClass.BModule bModuleParam) {",
                "    this.executorProvider =",
                "        TestClass_BModule_ExecutorFactory.create(bModuleParam);",
                "    this.productionImplementationExecutorProvider =",
                "        DoubleCheck.provider((Provider) executorProvider);",
                "    this.simpleComponentProvider = ",
                "        InstanceFactory.create((TestClass.SimpleComponent) this);",
                "    this.monitorProvider =",
                "        DoubleCheck.provider(",
                "            TestClass_SimpleComponent_MonitoringModule_MonitorFactory.create(",
                "                simpleComponentProvider,",
                "                SetFactory.<ProductionComponentMonitor.Factory>empty()));",
                "    this.bProvider = TestClass_BModule_BFactory.create(",
                "        bModuleParam, TestClass_C_Factory.create());",
                "    this.bProducer = Producers.producerFromProvider(bProvider);",
                "    this.aProducer = TestClass_AModule_AFactory.create(",
                "        aModuleParam,",
                "        productionImplementationExecutorProvider,",
                "        monitorProvider,",
                "        bProducer);",
                "    this.aEntryPoint = Producers.entryPointViewOf(aProducer, this);",
                "  }",
                "",
                "  @Override",
                "  public ListenableFuture<TestClass.A> a() {",
                "    return aEntryPoint.get();",
                "  }",
                "",
                "  @Override",
                "  public void onProducerFutureCancelled(boolean mayInterruptIfRunning) {",
                "    Producers.cancel(aProducer, mayInterruptIfRunning);",
                "    Producers.cancel(bProducer, mayInterruptIfRunning);",
                "  }",
                "",
                "  static final class Builder {",
                "    private TestClass.AModule aModule;",
                "    private TestClass.BModule bModule;",
                "",
                "    private Builder() {}",
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
                "    public TestClass.SimpleComponent build() {",
                "      if (aModule == null) {",
                "        this.aModule = new TestClass.AModule();",
                "      }",
                "      if (bModule == null) {",
                "        this.bModule = new TestClass.BModule();",
                "      }",
                "      return new DaggerTestClass_SimpleComponent(aModule, bModule);",
                "    }",
                "  }",
                "}");
    }
    assertAbout(javaSource())
        .that(component)
        .withCompilerOptions(compilerMode.javacopts())
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
  }

  @Test public void nullableProducersAreNotErrors() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import com.google.common.util.concurrent.MoreExecutors;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "import dagger.producers.Production;",
        "import dagger.producers.ProductionComponent;",
        "import java.util.concurrent.Executor;",
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
        "",
        "    @Provides @Production Executor executor() {",
        "      return MoreExecutors.directExecutor();",
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining("@Nullable on @Produces methods does not do anything")
        .inFile(component)
        .onLine(33);
    assertThat(compilation)
        .hadWarningContaining("@Nullable on @Produces methods does not do anything")
        .inFile(component)
        .onLine(36);
  }

  @Test
  public void productionScope_injectConstructor() {
    JavaFileObject productionScoped =
        JavaFileObjects.forSourceLines(
            "test.ProductionScoped",
            "package test;",
            "",
            "import dagger.producers.ProductionScope;",
            "import javax.inject.Inject;",
            "",
            "@ProductionScope",
            "class ProductionScoped {",
            "  @Inject ProductionScoped() {}",
            "}");
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.producers.ProductionComponent;",
            "",
            "@ProductionComponent",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent",
            "interface Child {",
            "  ProductionScoped productionScoped();",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(productionScoped, parent, child);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .containsElementsIn(
            new JavaFileBuilder(compilerMode, "test.DaggerRoot")
                .addLines(
                    "package test;",
                    GENERATED_ANNOTATION,
                    "final class DaggerParent implements Parent, CancellationListener {",
                    "  private final class ChildImpl implements Child, CancellationListener {",
                    "    @Override",
                    "    public ProductionScoped productionScoped() {")
                .addLinesIn(
                    CompilerMode.DEFAULT_MODE, //
                    "      return DaggerParent.this.productionScopedProvider.get();")
                .addLinesIn(
                    CompilerMode.FAST_INIT_MODE, //
                    "      return DaggerParent.this.getProductionScoped();")
                .addLines(
                    "    }", //
                    "  }", //
                    "}")
                .build());
  }
}
