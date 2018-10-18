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
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatMethodInUnannotatedClass;
import static dagger.internal.codegen.DaggerModuleMethodSubject.Factory.assertThatProductionModuleMethod;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.lang.annotation.Retention;
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
        .hasError(formatModuleErrorMessage("@%s methods can only be present within a @%s"));
  }

  @Test public void producesMethodAbstract() {
    assertThatProductionModuleMethod("@Produces abstract String produceString();")
        .hasError(formatErrorMessage("@%s methods cannot be abstract"));
  }

  @Test public void producesMethodPrivate() {
    assertThatProductionModuleMethod("@Produces private String produceString() { return null; }")
        .hasError(formatErrorMessage("@%s methods cannot be private"));
  }

  @Test public void producesMethodReturnVoid() {
    assertThatProductionModuleMethod("@Produces void produceNothing() {}")
        .hasError(formatErrorMessage("@%s methods must return a value (not void)"));
  }

  @Test
  public void producesProvider() {
    assertThatProductionModuleMethod("@Produces Provider<String> produceProvider() {}")
        .hasError(formatErrorMessage("@%s methods must not return framework types"));
  }

  @Test
  public void producesLazy() {
    assertThatProductionModuleMethod("@Produces Lazy<String> produceLazy() {}")
        .hasError(formatErrorMessage("@%s methods must not return framework types"));
  }

  @Test
  public void producesMembersInjector() {
    assertThatProductionModuleMethod(
            "@Produces MembersInjector<String> produceMembersInjector() {}")
        .hasError(formatErrorMessage("@%s methods must not return framework types"));
  }

  @Test
  public void producesProducer() {
    assertThatProductionModuleMethod("@Produces Producer<String> produceProducer() {}")
        .hasError(formatErrorMessage("@%s methods must not return framework types"));
  }

  @Test
  public void producesProduced() {
    assertThatProductionModuleMethod("@Produces Produced<String> produceProduced() {}")
        .hasError(formatErrorMessage("@%s methods must not return framework types"));
  }

  @Test public void producesMethodReturnRawFuture() {
    assertThatProductionModuleMethod("@Produces ListenableFuture produceRaw() {}")
        .importing(ListenableFuture.class)
        .hasError("@Produces methods cannot return a raw ListenableFuture");
  }

  @Test public void producesMethodReturnWildcardFuture() {
    assertThatProductionModuleMethod("@Produces ListenableFuture<?> produceRaw() {}")
        .importing(ListenableFuture.class)
        .hasError(
            "@Produces methods can return only a primitive, an array, a type variable, "
                + "a declared type, or a ListenableFuture of one of those types");
  }

  @Test public void producesMethodWithTypeParameter() {
    assertThatProductionModuleMethod("@Produces <T> String produceString() { return null; }")
        .hasError(formatErrorMessage("@%s methods may not have type parameters"));
  }

  @Test public void producesMethodSetValuesWildcard() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet Set<?> produceWildcard() { return null; }")
        .hasError(
            "@Produces methods can return only a primitive, an array, a type variable, "
                + "a declared type, or a ListenableFuture of one of those types");
  }

  @Test public void producesMethodSetValuesRawSet() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet Set produceSomething() { return null; }")
        .hasError(
            formatErrorMessage(
                "@%s methods annotated with @ElementsIntoSet cannot return a raw Set"));
  }

  @Test public void producesMethodSetValuesNotASet() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet List<String> produceStrings() { return null; }")
        .hasError(
            "@Produces methods of type set values must return a Set or ListenableFuture of Set");
  }

  @Test public void producesMethodSetValuesWildcardInFuture() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet "
                + "ListenableFuture<Set<?>> produceWildcard() { return null; }")
        .importing(ListenableFuture.class)
        .hasError(
            "@Produces methods can return only a primitive, an array, a type variable, "
                + "a declared type, or a ListenableFuture of one of those types");
  }

  @Test public void producesMethodSetValuesFutureRawSet() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet ListenableFuture<Set> produceSomething() { return null; }")
        .importing(ListenableFuture.class)
        .hasError(
            formatErrorMessage(
                "@%s methods annotated with @ElementsIntoSet cannot return a raw Set"));
  }

  @Test public void producesMethodSetValuesFutureNotASet() {
    assertThatProductionModuleMethod(
            "@Produces @ElementsIntoSet "
                + "ListenableFuture<List<String>> produceStrings() { return null; }")
        .importing(ListenableFuture.class)
        .hasError(
            "@Produces methods of type set values must return a Set or ListenableFuture of Set");
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
    String errorMessage =
        "Cannot have more than one @Produces method with the same name in a single module";
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(errorMessage).inFile(moduleFile).onLine(8);
    assertThat(compilation).hadErrorContaining(errorMessage).inFile(moduleFile).onLine(12);
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
        .hasError("@Produces methods may not have scope annotations");
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
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Modules cannot be private")
        .inFile(moduleFile)
        .onLine(6);
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
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Modules cannot be enclosed in private types")
        .inFile(moduleFile)
        .onLine(7);
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
    Compilation compilation = daggerCompiler().compile(xFile, moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "X is listed as a module, but is not annotated with one of @Module, @ProducerModule");
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
    Compilation compilation =
        daggerCompiler()
            .compile(
                publicModuleFile,
                nonPublicModule1File,
                nonPublicModule2File,
                otherPublicModuleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "This module is public, but it includes non-public "
                + "(or effectively non-public) modules. "
                + "Either reduce the visibility of this module or make "
                + "test.NonPublicModule1 and test.NonPublicModule2 public.")
        .inFile(publicModuleFile)
        .onLine(8);
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
    Compilation compilation = daggerCompiler().compile(moduleFile);
    assertThat(compilation).succeeded();
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
            "import com.google.common.util.concurrent.Futures;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.internal.AbstractProducesMethodProducer;",
            "import dagger.producers.monitoring.ProducerToken;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.concurrent.Executor;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            "@SuppressWarnings(\"FutureReturnValueIgnored\")",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProduceStringFactory",
            "    extends AbstractProducesMethodProducer<Void, String> {",
            "  private final TestModule module;",
            "",
            "  public TestModule_ProduceStringFactory(",
            "      TestModule module,",
            "      Provider<Executor> executorProvider,",
            "      Provider<ProductionComponentMonitor> productionComponentMonitorProvider) {",
            "    super(",
            "        productionComponentMonitorProvider,",
            "        ProducerToken.create(TestModule_ProduceStringFactory.class),",
            "        executorProvider);",
            "    this.module = module;",
            "  }",
            "",
            "  @Override protected ListenableFuture<Void> collectDependencies() {",
            "    return Futures.<Void>immediateFuture(null);",
            "  }",
            "",
            "  @Override public ListenableFuture<String> callProducesMethod(Void ignoredVoidArg) {",
            "    return module.produceString();",
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
            "import com.google.common.util.concurrent.Futures;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.internal.AbstractProducesMethodProducer;",
            "import dagger.producers.monitoring.ProducerToken;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.concurrent.Executor;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            "@SuppressWarnings(\"FutureReturnValueIgnored\")",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProduceStringFactory",
            "    extends AbstractProducesMethodProducer<Void, String> {",
            "  private final TestModule module;",
            "",
            "  public TestModule_ProduceStringFactory(",
            "      TestModule module,",
            "      Provider<Executor> executorProvider,",
            "      Provider<ProductionComponentMonitor> productionComponentMonitorProvider) {",
            "    super(",
            "        productionComponentMonitorProvider,",
            "        ProducerToken.create(\"test.TestModule#produceString\"),",
            "        executorProvider);",
            "    this.module = module;",
            "  }",
            "",
            "  @Override protected ListenableFuture<Void> collectDependencies() {",
            "    return Futures.<Void>immediateFuture(null);",
            "  }",
            "",
            "  @Override public ListenableFuture<String> callProducesMethod(Void ignoredVoidArg) {",
            "    return module.produceString();",
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

  @Test
  public void producesMethodMultipleQualifiersOnMethod() {
    assertThatProductionModuleMethod(
            "@Produces @QualifierA @QualifierB static String produceString() { return null; }")
        .importing(ListenableFuture.class, QualifierA.class, QualifierB.class)
        .hasError("may not use more than one @Qualifier");
  }

  @Test
  public void producesMethodMultipleQualifiersOnParameter() {
    assertThatProductionModuleMethod(
            "@Produces static String produceString(@QualifierA @QualifierB Object input) "
                + "{ return null; }")
        .importing(ListenableFuture.class, QualifierA.class, QualifierB.class)
        .hasError("may not use more than one @Qualifier");
  }

  @Test
  public void producesMethodWildcardDependency() {
    assertThatProductionModuleMethod(
            "@Produces static String produceString(Provider<? extends Number> numberProvider) "
                + "{ return null; }")
        .importing(ListenableFuture.class, QualifierA.class, QualifierB.class)
        .hasError(
            "Dagger does not support injecting Provider<T>, Lazy<T>, Producer<T>, or Produced<T> "
                + "when T is a wildcard type such as ? extends java.lang.Number");
  }

  @Qualifier
  @Retention(RUNTIME)
  public @interface QualifierA {}

  @Qualifier
  @Retention(RUNTIME)
  public @interface QualifierB {}
}
