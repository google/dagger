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
// TODO(beder): Merge the error-handling tests with the ModuleFactoryGeneratorTest.
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
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_SET_VALUES_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_THROWS;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

@RunWith(JUnit4.class)
public class ProducerModuleFactoryGeneratorTest {
  private String formatErrorMessage(String msg) {
    return String.format(msg, "Produces");
  }

  private String formatModuleErrorMessage(String msg) {
    return String.format(msg, "Produces", "ProducerModule");
  }

  @Test public void producesMethodNotInModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.Produces;",
        "",
        "final class TestModule {",
        "  @Produces String produceString() {",
        "    return \"\";",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatModuleErrorMessage(BINDING_METHOD_NOT_IN_MODULE));
  }

  @Test public void producesMethodAbstract() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "abstract class TestModule {",
        "  @Produces abstract String produceString();",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_ABSTRACT));
  }

  @Test public void producesMethodPrivate() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces private String produceString() {",
        "    return \"\";",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_PRIVATE));
  }

  @Test public void producesMethodReturnVoid() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces void produceNothing() {}",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_MUST_RETURN_A_VALUE));
  }

  @Test public void producesMethodFrameworkType() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import dagger.MembersInjector;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produced;",
        "import dagger.producers.Producer;",
        "import dagger.producers.Produces;",
        "import javax.inject.Provider;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces Provider<String> produceProvider() {}",
        "  @Produces Lazy<String> produceLazy() {}",
        "  @Produces MembersInjector<String> produceMembersInjector() {}",
        "  @Produces Producer<String> produceProducer() {}",
        "  @Produces Produced<String> produceProduced() {}",
        "}");
    assertAbout(javaSource()).that(moduleFile)
    .processedWith(new ComponentProcessor())
    .failsToCompile()
    .withErrorContaining(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES))
    .in(moduleFile).onLine(13)
    .and()
    .withErrorContaining(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES))
    .in(moduleFile).onLine(14)
    .and()
    .withErrorContaining(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES))
    .in(moduleFile).onLine(15)
    .and()
    .withErrorContaining(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES))
    .in(moduleFile).onLine(16)
    .and()
    .withErrorContaining(formatErrorMessage(BINDING_METHOD_MUST_NOT_BIND_FRAMEWORK_TYPES))
    .in(moduleFile).onLine(17);
  }

  @Test public void producesMethodReturnRawFuture() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces ListenableFuture produceRaw() {}",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PRODUCES_METHOD_RAW_FUTURE);
  }

  @Test public void producesMethodReturnWildcardFuture() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces ListenableFuture<?> produceRaw() {}",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PRODUCES_METHOD_RETURN_TYPE);
  }

  @Test public void producesMethodWithTypeParameter() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces <T> String produceString() {",
        "    return \"\";",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_TYPE_PARAMETER));
  }

  @Test public void producesMethodSetValuesWildcard() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.multibindings.ElementsIntoSet;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.Set;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces @ElementsIntoSet Set<?> produceWildcard() {",
        "    return null;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PRODUCES_METHOD_RETURN_TYPE);
  }

  @Test public void producesMethodSetValuesRawSet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.multibindings.ElementsIntoSet;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.Set;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces @ElementsIntoSet Set produceSomething() {",
        "    return null;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_SET_VALUES_RAW_SET));
  }

  @Test public void producesMethodSetValuesNotASet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.multibindings.ElementsIntoSet;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.List;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces @ElementsIntoSet List<String> produceStrings() {",
        "    return null;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PRODUCES_METHOD_SET_VALUES_RETURN_SET);
  }

  @Test public void producesMethodSetValuesWildcardInFuture() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.multibindings.ElementsIntoSet;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.Set;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces @ElementsIntoSet ListenableFuture<Set<?>> produceWildcard() {",
        "    return null;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PRODUCES_METHOD_RETURN_TYPE);
  }

  @Test public void producesMethodSetValuesFutureRawSet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.multibindings.ElementsIntoSet;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.Set;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces @ElementsIntoSet ListenableFuture<Set> produceSomething() {",
        "    return null;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(formatErrorMessage(BINDING_METHOD_SET_VALUES_RAW_SET));
  }

  @Test public void producesMethodSetValuesFutureNotASet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.multibindings.ElementsIntoSet;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.List;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces @ElementsIntoSet ListenableFuture<List<String>> produceStrings() {",
        "    return null;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PRODUCES_METHOD_SET_VALUES_RETURN_SET);
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
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "",
            "@ProducerModule",
            "final class TestModule {",
            "  @Produces int produceInt() throws Throwable {",
            "    return 0;",
            "  }",
            "",
            "}");
    assertAbout(javaSource())
        .that(moduleFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(PRODUCES_METHOD_THROWS)
        .in(moduleFile)
        .onLine(8);
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
            "import dagger.producers.monitoring.ProducerMonitor;",
            "import dagger.producers.monitoring.ProducerToken;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.concurrent.Executor;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProduceStringFactory extends AbstractProducer<String> {",
            "  private final TestModule module;",
            "  private final Provider<Executor> executorProvider;",
            "  private final Provider<ProductionComponentMonitor> monitorProvider;",
            "",
            "  public TestModule_ProduceStringFactory(",
            "      TestModule module,",
            "      Provider<Executor> executorProvider,",
            "      Provider<ProductionComponentMonitor> monitorProvider) {",
            "    super(",
            "        monitorProvider,",
            "        ProducerToken.create(TestModule_ProduceStringFactory.class));",
            "    assert module != null;",
            "    this.module = module;",
            "    assert executorProvider != null;",
            "    this.executorProvider = executorProvider;",
            "    assert monitorProvider != null;",
            "    this.monitorProvider = monitorProvider;",
            "  }",
            "",
            "  @Override protected ListenableFuture<String> compute(",
            "      final ProducerMonitor monitor) {",
            "    return Futures.transformAsync(",
            "      Futures.<Void>immediateFuture(null),",
            "      new AsyncFunction<Void, String>() {",
            "        @Override public ListenableFuture<String> apply(Void ignoredVoidArg) {",
            "          monitor.methodStarting();",
            "          try {",
            "            return TestModule_ProduceStringFactory.this.module.produceString();",
            "          } finally {",
            "            monitor.methodFinished();",
            "          }",
            "        }",
            "      }, executorProvider.get());",
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
            "import dagger.producers.monitoring.ProducerMonitor;",
            "import dagger.producers.monitoring.ProducerToken;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.concurrent.Executor;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class TestModule_ProduceStringFactory extends AbstractProducer<String> {",
            "  private final TestModule module;",
            "  private final Provider<Executor> executorProvider;",
            "  private final Provider<ProductionComponentMonitor> monitorProvider;",
            "",
            "  public TestModule_ProduceStringFactory(",
            "      TestModule module,",
            "      Provider<Executor> executorProvider,",
            "      Provider<ProductionComponentMonitor> monitorProvider) {",
            "    super(",
            "        monitorProvider,",
            "        ProducerToken.create(\"test.TestModule#produceString\"));",
            "    assert module != null;",
            "    this.module = module;",
            "    assert executorProvider != null;",
            "    this.executorProvider = executorProvider;",
            "    assert monitorProvider != null;",
            "    this.monitorProvider = monitorProvider;",
            "  }",
            "",
            "  @Override protected ListenableFuture<String> compute(",
            "      final ProducerMonitor monitor) {",
            "    return Futures.transformAsync(",
            "      Futures.<Void>immediateFuture(null),",
            "      new AsyncFunction<Void, String>() {",
            "        @Override public ListenableFuture<String> apply(Void ignoredVoidArg) {",
            "          monitor.methodStarting();",
            "          try {",
            "            return TestModule_ProduceStringFactory.this.module.produceString();",
            "          } finally {",
            "            monitor.methodFinished();",
            "          }",
            "        }",
            "      }, executorProvider.get());",
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

  private static final JavaFileObject QUALIFIER_A =
      JavaFileObjects.forSourceLines("test.QualifierA",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierA {}");
  private static final JavaFileObject QUALIFIER_B =
      JavaFileObjects.forSourceLines("test.QualifierB",
          "package test;",
          "",
          "import javax.inject.Qualifier;",
          "",
          "@Qualifier @interface QualifierB {}");

  @Test public void producesMethodMultipleQualifiers() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces @QualifierA @QualifierB abstract String produceString() {",
        "    return \"\";",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(moduleFile, QUALIFIER_A, QUALIFIER_B))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(BINDING_METHOD_MULTIPLE_QUALIFIERS);
  }
}
