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

// TODO(beder): Merge the error-handling tests with the ModuleFactoryGeneratorTest.
package dagger.internal.codegen;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatMethodInUnannotatedClass;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatProductionModuleMethod;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_RETURN_A_VALUE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_PRIVATE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_SET_VALUES_RAW_SET;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_TYPE_PARAMETER;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_SAME_NAME;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_RAW_FUTURE;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_SCOPE;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_SET_VALUES_RETURN_SET;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.testing.compile.JavaFileObjects;
import javax.inject.Qualifier;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProducerModuleFactoryGeneratorTest {

  private String formatErrorMessage(String msg) {
    return String.format(msg, "Produces");
  }

  private String formatModuleErrorMessage(String msg) {
    return String.format(msg, "Produces", "ProducerModule");
  }

  @Test public void producesMethodNotInModule() {
    assertThatMethodInUnannotatedClass("@Produces String produceString() { return null; }")
        .hasError(formatModuleErrorMessage(BINDING_METHOD_NOT_IN_MODULE));
  }

  @Test public void producesMethodAbstract() {
    assertThatProductionModuleMethod("@Produces abstract String produceString();")
        .hasError(formatErrorMessage(BINDING_METHOD_ABSTRACT));
  }

  @Test public void producesMethodPrivate() {
    assertThatProductionModuleMethod("@Produces private String produceString() { return null; }")
        .hasError(formatErrorMessage(BINDING_METHOD_PRIVATE));
  }

  @Test public void producesMethodReturnVoid() {
    assertThatProductionModuleMethod("@Produces void produceNothing() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_RETURN_A_VALUE));
  }

  @Test
  public void producesProvider() {
    assertThatProductionModuleMethod("@Produces Provider<String> produceProvider() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES));
  }

  @Test
  public void producesLazy() {
    assertThatProductionModuleMethod("@Produces Lazy<String> produceLazy() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES));
  }

  @Test
  public void producesMembersInjector() {
    assertThatProductionModuleMethod(
            "@Produces MembersInjector<String> produceMembersInjector() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES));
  }

  @Test
  public void producesProducer() {
    assertThatProductionModuleMethod("@Produces Producer<String> produceProducer() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES));
  }

  @Test
  public void producesProduced() {
    assertThatProductionModuleMethod("@Produces Produced<String> produceProduced() {}")
        .hasError(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES));
  }

  @Test public void producesMethodReturnRawFuture() {
    assertThatProductionModuleMethod("@Produces ListenableFuture produceRaw() {}")
        .importing(ListenableFuture.class)
        .hasError(PRODUCES_METHOD_RAW_FUTURE);
  }

  @Test public void producesMethodReturnWildcardFuture() {
    assertThatProductionModuleMethod("@Produces ListenableFuture<?> produceRaw() {}")
        .importing(ListenableFuture.class)
        .hasError(PRODUCES_METHOD_RETURN_TYPE);
  }

  @Test public void producesMethodWithTypeParameter() {
    assertThatProductionModuleMethod("@Produces <T> String produceString() { return null; }")
        .hasError(formatErrorMessage(BINDING_METHOD_TYPE_PARAMETER));
  }

  @Test public void producesMethodSetValuesWildcard() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet Set<?> produceWildcard() { return null; }")
        .hasError(PRODUCES_METHOD_RETURN_TYPE);
  }

  @Test public void producesMethodSetValuesRawSet() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet Set produceSomething() { return null; }")
        .hasError(formatErrorMessage(BINDING_METHOD_SET_VALUES_RAW_SET));
  }

  @Test public void producesMethodSetValuesNotASet() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet List<String> produceStrings() { return null; }")
        .hasError(PRODUCES_METHOD_SET_VALUES_RETURN_SET);
  }

  @Test public void producesMethodSetValuesWildcardInFuture() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet "
                + "ListenableFuture<Set<?>> produceWildcard() { return null; }")
        .importing(ListenableFuture.class)
        .hasError(PRODUCES_METHOD_RETURN_TYPE);
  }

  @Test public void producesMethodSetValuesFutureRawSet() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet ListenableFuture<Set> produceSomething() { return null; }")
        .importing(ListenableFuture.class)
        .hasError(formatErrorMessage(BINDING_METHOD_SET_VALUES_RAW_SET));
  }

  @Test public void producesMethodSetValuesFutureNotASet() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet "
                + "ListenableFuture<List<String>> produceStrings() { return null; }")
        .importing(ListenableFuture.class)
        .hasError(PRODUCES_METHOD_SET_VALUES_RETURN_SET);
  }

  @Test public void multipleProducesMethodsWithSameName() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces Object produce(int i) {",
        "    return i;",
        "  }",
        "",
        "  @Produces String produce() {",
        "    return \"\";",
        "  }",
        "}");
    String errorMessage = String.format(BINDING_METHOD_WITH_SAME_NAME, "Produces");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(errorMessage).in(moduleFile).onLine(8)
        .and().withErrorContaining(errorMessage).in(moduleFile).onLine(12);
  }

  @Test
  public void producesMethodThrowsThrowable() {
    assertThatProductionModuleMethod("@Produces int produceInt() throws Throwable { return 0; }")
        .hasError(
            "@Produces methods may only throw unchecked exceptions or exceptions subclassing "
                + "Exception");
  }

  @Test public void producesMethodWithScope() {
    assertThatProductionModuleMethod("@Produces @Singleton String str() { return \"\"; }")
        .hasError(PRODUCES_METHOD_SCOPE);
  }

  @Test
  public void privateModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.Enclosing",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "final class Enclosing {",
        "  @ProducerModule private static final class PrivateModule {",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Modules cannot be private.")
        .in(moduleFile).onLine(6);
  }

  @Test
  public void enclosedInPrivateModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.Enclosing",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "final class Enclosing {",
        "  private static final class PrivateEnclosing {",
        "    @ProducerModule static final class TestModule {",
        "    }",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("Modules cannot be enclosed in private types.")
        .in(moduleFile).onLine(7);
  }

  @Test
  public void includesNonModule() {
    JavaFileObject xFile =
        JavaFileObjects.forSourceLines("test.X", "package test;", "", "public final class X {}");
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.FooModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "",
            "@ProducerModule(includes = X.class)",
            "public final class FooModule {",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(xFile, moduleFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(
                ErrorMessages.REFERENCED_MODULE_NOT_ANNOTATED,
                "X",
                "one of @Module, @ProducerModule"));
  }

  @Test
  public void publicModuleNonPublicIncludes() {
    JavaFileObject publicModuleFile = JavaFileObjects.forSourceLines("test.PublicModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "@ProducerModule(includes = {",
        "    NonPublicModule1.class, OtherPublicModule.class, NonPublicModule2.class",
        "})",
        "public final class PublicModule {",
        "}");
    JavaFileObject nonPublicModule1File = JavaFileObjects.forSourceLines("test.NonPublicModule1",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "@ProducerModule",
        "final class NonPublicModule1 {",
        "}");
    JavaFileObject nonPublicModule2File = JavaFileObjects.forSourceLines("test.NonPublicModule2",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "@ProducerModule",
        "final class NonPublicModule2 {",
        "}");
    JavaFileObject otherPublicModuleFile = JavaFileObjects.forSourceLines("test.OtherPublicModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "",
        "@ProducerModule",
        "public final class OtherPublicModule {",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(
            publicModuleFile, nonPublicModule1File, nonPublicModule2File, otherPublicModuleFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("This module is public, but it includes non-public "
            + "(or effectively non-public) modules. "
            + "Either reduce the visibility of this module or make "
            + "test.NonPublicModule1 and test.NonPublicModule2 public.")
        .in(publicModuleFile).onLine(8);
  }

  @Test public void argumentNamedModuleCompiles() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces String produceString(int module) {",
        "    return null;",
        "  }",
        "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
  }

  @Test public void singleProducesMethodNoArgsFuture() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces ListenableFuture<String> produceString() {",
        "    return null;",
        "  }",
        "}");
    JavaFileObject factoryFile =
        JavaFileObjects.forSourceLines(
            "TestModule_ProduceStringFactory",
            "package test;",
            "",
            "import com.google.common.util.concurrent.AsyncFunction;",
            "import com.google.common.util.concurrent.Futures;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.internal.AbstractProducer;",
            "import dagger.producers.monitoring.ProducerToken;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.concurrent.Executor;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            "@SuppressWarnings(\"FutureReturnValueIgnored\")",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProduceStringFactory",
            "    extends AbstractProducer<String>",
            "    implements AsyncFunction<Void, String>, Executor {",
            "  private final TestModule module;",
            "  private final Provider<Executor> executorProvider;",
            "  private final ",
            "      Provider<ProductionComponentMonitor> productionComponentMonitorProvider;",
            "",
            "  public TestModule_ProduceStringFactory(",
            "      TestModule module,",
            "      Provider<Executor> executorProvider,",
            "      Provider<ProductionComponentMonitor> productionComponentMonitorProvider) {",
            "    super(",
            "        productionComponentMonitorProvider,",
            "        ProducerToken.create(TestModule_ProduceStringFactory.class));",
            "    this.module = module;",
            "    this.executorProvider = executorProvider;",
            "    this.productionComponentMonitorProvider = productionComponentMonitorProvider;",
            "  }",
            "",
            "  @Override protected ListenableFuture<String> compute() {",
            "    return Futures.transformAsync(",
            "        Futures.<Void>immediateFuture(null), this, this);",
            "  }",
            "",
            "  @Deprecated",
            "  @Override public ListenableFuture<String> apply(Void ignoredVoidArg) {",
            "    monitor.methodStarting();",
            "    try {",
            "      return TestModule_ProduceStringFactory.this.module.produceString();",
            "    } finally {",
            "      monitor.methodFinished();",
            "    }",
            "  }",
            "",
            "  @Deprecated",
            "  @Override public void execute(Runnable runnable) {",
            "    monitor.ready();",
            "    executorProvider.get().execute(runnable);",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(factoryFile);
  }

  @Test
  public void singleProducesMethodNoArgsFutureWithProducerName() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import com.google.common.util.concurrent.Futures;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "",
            "@ProducerModule",
            "final class TestModule {",
            "  @Produces ListenableFuture<String> produceString() {",
            "    return Futures.immediateFuture(\"\");",
            "  }",
            "}");
    JavaFileObject factoryFile =
        JavaFileObjects.forSourceLines(
            "TestModule_ProduceStringFactory",
            "package test;",
            "",
            "import com.google.common.util.concurrent.AsyncFunction;",
            "import com.google.common.util.concurrent.Futures;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.internal.AbstractProducer;",
            "import dagger.producers.monitoring.ProducerToken;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.concurrent.Executor;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            "@SuppressWarnings(\"FutureReturnValueIgnored\")",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProduceStringFactory",
            "    extends AbstractProducer<String>",
            "    implements AsyncFunction<Void, String>, Executor {",
            "  private final TestModule module;",
            "  private final Provider<Executor> executorProvider;",
            "  private final ",
            "      Provider<ProductionComponentMonitor> productionComponentMonitorProvider;",
            "",
            "  public TestModule_ProduceStringFactory(",
            "      TestModule module,",
            "      Provider<Executor> executorProvider,",
            "      Provider<ProductionComponentMonitor> productionComponentMonitorProvider) {",
            "    super(",
            "        productionComponentMonitorProvider,",
            "        ProducerToken.create(\"test.TestModule#produceString\"));",
            "    this.module = module;",
            "    this.executorProvider = executorProvider;",
            "    this.productionComponentMonitorProvider = productionComponentMonitorProvider;",
            "  }",
            "",
            "  @Override protected ListenableFuture<String> compute() {",
            "    return Futures.transformAsync(",
            "      Futures.<Void>immediateFuture(null), this, this);",
            "  }",
            "",
            "  @Deprecated",
            "  @Override public ListenableFuture<String> apply(Void ignoredVoidArg) {",
            "    monitor.methodStarting();",
            "    try {",
            "      return TestModule_ProduceStringFactory.this.module.produceString();",
            "    } finally {",
            "      monitor.methodFinished();",
            "    }",
            "  }",
            "",
            "  @Deprecated",
            "  @Override public void execute(Runnable runnable) {",
            "    monitor.ready();",
            "    executorProvider.get().execute(runnable);",
            "  }",
            "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .withCompilerOptions("-Adagger.writeProducerNameInToken=ENABLED")
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(factoryFile);
  }

  @Test public void producesMethodMultipleQualifiers() {
    assertThatProductionModuleMethod(
            "@Produces @QualifierA @QualifierB abstract String produceString() { return null; }")
        .importing(ListenableFuture.class, QualifierA.class, QualifierB.class)
        .hasError(BINDING_METHOD_MULTIPLE_QUALIFIERS);
  }
  
  @Qualifier
  public @interface QualifierA {}

  @Qualifier
  public @interface QualifierB {}
}
