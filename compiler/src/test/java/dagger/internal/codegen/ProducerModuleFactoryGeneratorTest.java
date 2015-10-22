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
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_RETURN_A_VALUE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_PRIVATE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_SET_VALUES_RAW_SET;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_TYPE_PARAMETER;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_SAME_NAME;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_RAW_FUTURE;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.PRODUCES_METHOD_SET_VALUES_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_OR_PRODUCES_METHOD_MULTIPLE_QUALIFIERS;

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
        "import static dagger.producers.Produces.Type.SET_VALUES;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.Set;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces(type = SET_VALUES) Set<?> produceWildcard() {",
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
        "import static dagger.producers.Produces.Type.SET_VALUES;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.Set;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces(type = SET_VALUES) Set produceSomething() {",
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
        "import static dagger.producers.Produces.Type.SET_VALUES;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.List;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces(type = SET_VALUES) List<String> produceStrings() {",
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
        "import static dagger.producers.Produces.Type.SET_VALUES;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.Set;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces(type = SET_VALUES) ListenableFuture<Set<?>> produceWildcard() {",
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
        "import static dagger.producers.Produces.Type.SET_VALUES;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.Set;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces(type = SET_VALUES) ListenableFuture<Set> produceSomething() {",
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
        "import static dagger.producers.Produces.Type.SET_VALUES;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "import java.util.List;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces(type = SET_VALUES) ListenableFuture<List<String>> produceStrings() {",
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
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProduceStringFactory",
        "package test;",
        "",
        "import com.google.common.util.concurrent.Futures;",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.internal.AbstractProducer;",
        "import dagger.producers.internal.Producers;",
        "import java.util.concurrent.Callable;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "",
        "@SuppressWarnings(\"unchecked\")",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProduceStringFactory extends AbstractProducer<String> {",
        "  private final TestModule module;",
        "  private final Executor executor;",
        "",
        "  public TestModule_ProduceStringFactory(TestModule module, Executor executor) {",
        "    assert module != null;",
        "    this.module = module;",
        "    assert executor != null;",
        "    this.executor = executor;",
        "  }",
        "",
        "  @Override protected ListenableFuture<String> compute() {",
        "    ListenableFuture<ListenableFuture<String>> future = Producers.submitToExecutor(",
        "      new Callable<ListenableFuture<String>>() {",
        "        @Override public ListenableFuture<String> call() {",
        "          return module.produceString();",
        "        }",
        "      }, executor);",
        "    return Futures.dereference(future);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void singleProducesMethodNoArgsFutureSet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces(type = Produces.Type.SET)",
        "  ListenableFuture<String> produceString() {",
        "    return null;",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProduceStringFactory",
        "package test;",
        "",
        "import com.google.common.util.concurrent.Futures;",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.internal.AbstractProducer;",
        "import dagger.producers.internal.Producers;",
        "import java.util.Set;",
        "import java.util.concurrent.Callable;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "",
        "@SuppressWarnings(\"unchecked\")",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProduceStringFactory",
        "    extends AbstractProducer<Set<String>> {",
        "  private final TestModule module;",
        "  private final Executor executor;",
        "",
        "  public TestModule_ProduceStringFactory(TestModule module, Executor executor) {  ",
        "    assert module != null;",
        "    this.module = module;",
        "    assert executor != null;",
        "    this.executor = executor;",
        "  }",
        "",
        "  @Override",
        "  protected ListenableFuture<Set<String>> compute() {  ",
        "    ListenableFuture<ListenableFuture<Set<String>>> future =",
        "        Producers.submitToExecutor(new Callable<ListenableFuture<Set<String>>>() {",
        "      @Override public ListenableFuture<Set<String>> call() {",
        "        return Producers.createFutureSingletonSet(module.produceString());",
        "      }",
        "    }, executor);",
        "    return Futures.dereference(future);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void singleProducesMethodNoArgsNoFuture() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces String produceString() {",
        "    return \"\";",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProduceStringFactory",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.internal.AbstractProducer;",
        "import dagger.producers.internal.Producers;",
        "import java.util.concurrent.Callable;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "",
        "@SuppressWarnings(\"unchecked\")",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProduceStringFactory extends AbstractProducer<String> {",
        "  private final TestModule module;",
        "  private final Executor executor;",
        "",
        "  public TestModule_ProduceStringFactory(TestModule module, Executor executor) {",
        "    assert module != null;",
        "    this.module = module;",
        "    assert executor != null;",
        "    this.executor = executor;",
        "  }",
        "",
        "  @Override protected ListenableFuture<String> compute() {",
        "    ListenableFuture<String> future = Producers.submitToExecutor(",
        "      new Callable<String>() {",
        "        @Override public String call() {",
        "          return module.produceString();",
        "        }",
        "      }, executor);",
        "    return future;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void singleProducesMethodNoArgsNoFutureSet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces(type = Produces.Type.SET)",
        "  String produceString() {",
        "    return \"\";",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProduceStringFactory",
        "package test;",
        "",
        "import com.google.common.collect.ImmutableSet;",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.internal.AbstractProducer;",
        "import dagger.producers.internal.Producers;",
        "import java.util.Set;",
        "import java.util.concurrent.Callable;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "",
        "@SuppressWarnings(\"unchecked\")",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProduceStringFactory",
        "    extends AbstractProducer<Set<String>> {",
        "  private final TestModule module;",
        "  private final Executor executor;",
        "",
        "  public TestModule_ProduceStringFactory(TestModule module, Executor executor) {  ",
        "    assert module != null;",
        "    this.module = module;",
        "    assert executor != null;",
        "    this.executor = executor;",
        "  }",
        "",
        "  @Override",
        "  protected ListenableFuture<Set<String>> compute() {  ",
        "    ListenableFuture<Set<String>> future =",
        "        Producers.submitToExecutor(new Callable<Set<String>>() {",
        "      @Override public Set<String> call() {",
        "        return ImmutableSet.of(module.produceString());",
        "      }",
        "    }, executor);",
        "    return future;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void singleProducesMethodArgsFuture() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.Produced;",
        "import dagger.producers.Producer;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "import javax.inject.Provider;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces ListenableFuture<String> produceString(",
        "      int a, Produced<Double> b, Producer<Object> c, Provider<Boolean> d) {",
        "    return null;",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProduceStringFactory",
        "package test;",
        "",
        "import com.google.common.util.concurrent.AsyncFunction;",
        "import com.google.common.util.concurrent.Futures;",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.Produced;",
        "import dagger.producers.Producer;",
        "import dagger.producers.internal.AbstractProducer;",
        "import dagger.producers.internal.Producers;",
        "import java.util.List;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@SuppressWarnings(\"unchecked\")",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProduceStringFactory extends AbstractProducer<String> {",
        "  private final TestModule module;",
        "  private final Executor executor;",
        "  private final Producer<Integer> aProducer;",
        "  private final Producer<Double> bProducer;",
        "  private final Producer<Object> cProducer;",
        "  private final Provider<Boolean> dProvider;",
        "",
        "  public TestModule_ProduceStringFactory(",
        "      TestModule module,",
        "      Executor executor,",
        "      Producer<Integer> aProducer,",
        "      Producer<Double> bProducer,",
        "      Producer<Object> cProducer,",
        "      Provider<Boolean> dProvider) {",
        "    assert module != null;",
        "    this.module = module;",
        "    assert executor != null;",
        "    this.executor = executor;",
        "    assert aProducer != null;",
        "    this.aProducer = aProducer;",
        "    assert bProducer != null;",
        "    this.bProducer = bProducer;",
        "    assert cProducer != null;",
        "    this.cProducer = cProducer;",
        "    assert dProvider != null;",
        "    this.dProvider = dProvider;",
        "  }",
        "",
        "  @Override protected ListenableFuture<String> compute() {",
        "    ListenableFuture<Integer> aProducerFuture = aProducer.get();",
        "    ListenableFuture<Produced<Double>> bProducerFuture =",
        "        Producers.createFutureProduced(bProducer.get());",
        "    return Futures.transform(",
        "        Futures.<Object>allAsList(aProducerFuture, bProducerFuture),",
        "        new AsyncFunction<List<Object>, String>() {",
        "          @SuppressWarnings(\"unchecked\")  // safe by specification",
        "          @Override public ListenableFuture<String> apply(List<Object> args) {",
        "            return module.produceString(",
        "                (Integer) args.get(0),",
        "                (Produced<Double>) args.get(1),",
        "                cProducer,",
        "                dProvider);",
        "          }",
        "        }, executor);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void singleProducesMethodArgsNoFuture() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.Produced;",
        "import dagger.producers.Producer;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "import javax.inject.Provider;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces String produceString(",
        "      int a, Produced<Double> b, Producer<Object> c, Provider<Boolean> d) {",
        "    return \"\";",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProduceStringFactory",
        "package test;",
        "",
        "import com.google.common.util.concurrent.AsyncFunction;",
        "import com.google.common.util.concurrent.Futures;",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.Produced;",
        "import dagger.producers.Producer;",
        "import dagger.producers.internal.AbstractProducer;",
        "import dagger.producers.internal.Producers;",
        "import java.util.List;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@SuppressWarnings(\"unchecked\")",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProduceStringFactory extends AbstractProducer<String> {",
        "  private final TestModule module;",
        "  private final Executor executor;",
        "  private final Producer<Integer> aProducer;",
        "  private final Producer<Double> bProducer;",
        "  private final Producer<Object> cProducer;",
        "  private final Provider<Boolean> dProvider;",
        "",
        "  public TestModule_ProduceStringFactory(",
        "      TestModule module,",
        "      Executor executor,",
        "      Producer<Integer> aProducer,",
        "      Producer<Double> bProducer,",
        "      Producer<Object> cProducer,",
        "      Provider<Boolean> dProvider) {",
        "    assert module != null;",
        "    this.module = module;",
        "    assert executor != null;",
        "    this.executor = executor;",
        "    assert aProducer != null;",
        "    this.aProducer = aProducer;",
        "    assert bProducer != null;",
        "    this.bProducer = bProducer;",
        "    assert cProducer != null;",
        "    this.cProducer = cProducer;",
        "    assert dProvider != null;",
        "    this.dProvider = dProvider;",
        "  }",
        "",
        "  @Override protected ListenableFuture<String> compute() {",
        "    ListenableFuture<Integer> aProducerFuture = aProducer.get();",
        "    ListenableFuture<Produced<Double>> bProducerFuture =",
        "        Producers.createFutureProduced(bProducer.get());",
        "    return Futures.transform(",
        "        Futures.<Object>allAsList(aProducerFuture, bProducerFuture),",
        "        new AsyncFunction<List<Object>, String>() {",
        "          @SuppressWarnings(\"unchecked\")  // safe by specification",
        "          @Override public ListenableFuture<String> apply(List<Object> args) {",
        "            return Futures.immediateFuture(module.produceString(",
        "                (Integer) args.get(0),",
        "                (Produced<Double>) args.get(1),",
        "                cProducer,",
        "                dProvider));",
        "          }",
        "        }, executor);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void singleProducesMethodSingleArgsFuture() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces ListenableFuture<String> produceString(int a) {",
        "    return null;",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProduceStringFactory",
        "package test;",
        "",
        "import com.google.common.util.concurrent.AsyncFunction;",
        "import com.google.common.util.concurrent.Futures;",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.Producer;",
        "import dagger.producers.internal.AbstractProducer;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "",
        "@SuppressWarnings(\"unchecked\")",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProduceStringFactory extends AbstractProducer<String> {",
        "  private final TestModule module;",
        "  private final Executor executor;",
        "  private final Producer<Integer> aProducer;",
        "",
        "  public TestModule_ProduceStringFactory(",
        "      TestModule module,",
        "      Executor executor,",
        "      Producer<Integer> aProducer) {",
        "    assert module != null;",
        "    this.module = module;",
        "    assert executor != null;",
        "    this.executor = executor;",
        "    assert aProducer != null;",
        "    this.aProducer = aProducer;",
        "  }",
        "",
        "  @Override protected ListenableFuture<String> compute() {",
        "    ListenableFuture<Integer> aProducerFuture = aProducer.get();",
        "    return Futures.transform(aProducerFuture,",
        "        new AsyncFunction<Integer, String>() {",
        "          @Override public ListenableFuture<String> apply(Integer a) {",
        "            return module.produceString(a);",
        "          }",
        "        }, executor);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void singleProducesMethodCheckedException() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "import java.io.IOException;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces ListenableFuture<String> produceString()",
        "      throws InterruptedException, IOException {",
        "    return null;",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProduceStringFactory",
        "package test;",
        "",
        "import com.google.common.util.concurrent.Futures;",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.internal.AbstractProducer;",
        "import dagger.producers.internal.Producers;",
        "import java.io.IOException;",
        "import java.util.concurrent.Callable;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "",
        "@SuppressWarnings(\"unchecked\")",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProduceStringFactory extends AbstractProducer<String> {",
        "  private final TestModule module;",
        "  private final Executor executor;",
        "",
        "  public TestModule_ProduceStringFactory(TestModule module, Executor executor) {",
        "    assert module != null;",
        "    this.module = module;",
        "    assert executor != null;",
        "    this.executor = executor;",
        "  }",
        "",
        "  @Override protected ListenableFuture<String> compute() {",
        "    ListenableFuture<ListenableFuture<String>> future = Producers.submitToExecutor(",
        "      new Callable<ListenableFuture<String>>() {",
        "        @Override public ListenableFuture<String> call()",
        "            throws InterruptedException, IOException {",
        "          return module.produceString();",
        "        }",
        "      }, executor);",
        "    return Futures.dereference(future);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void singleProducesMethodCheckedExceptionNoFuture() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "import java.io.IOException;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces String produceString() throws IOException {",
        "    return \"\";",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProduceStringFactory",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.internal.AbstractProducer;",
        "import dagger.producers.internal.Producers;",
        "import java.io.IOException;",
        "import java.util.concurrent.Callable;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "",
        "@SuppressWarnings(\"unchecked\")",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProduceStringFactory extends AbstractProducer<String> {",
        "  private final TestModule module;",
        "  private final Executor executor;",
        "",
        "  public TestModule_ProduceStringFactory(TestModule module, Executor executor) {",
        "    assert module != null;",
        "    this.module = module;",
        "    assert executor != null;",
        "    this.executor = executor;",
        "  }",
        "",
        "  @Override protected ListenableFuture<String> compute() {",
        "    ListenableFuture<String> future = Producers.submitToExecutor(",
        "      new Callable<String>() {",
        "        @Override public String call() throws IOException {",
        "          return module.produceString();",
        "        }",
        "      }, executor);",
        "    return future;",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void singleProducesMethodCheckedExceptionFuture() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.Produced;",
        "import dagger.producers.Producer;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "import java.io.IOException;",
        "import javax.inject.Provider;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces ListenableFuture<String> produceString(",
        "      int a, Produced<Double> b, Producer<Object> c, Provider<Boolean> d)",
        "          throws IOException {",
        "    return null;",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProduceStringFactory",
        "package test;",
        "",
        "import com.google.common.util.concurrent.AsyncFunction;",
        "import com.google.common.util.concurrent.Futures;",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.Produced;",
        "import dagger.producers.Producer;",
        "import dagger.producers.internal.AbstractProducer;",
        "import dagger.producers.internal.Producers;",
        "import java.io.IOException;",
        "import java.util.List;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@SuppressWarnings(\"unchecked\")",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProduceStringFactory extends AbstractProducer<String> {",
        "  private final TestModule module;",
        "  private final Executor executor;",
        "  private final Producer<Integer> aProducer;",
        "  private final Producer<Double> bProducer;",
        "  private final Producer<Object> cProducer;",
        "  private final Provider<Boolean> dProvider;",
        "",
        "  public TestModule_ProduceStringFactory(",
        "      TestModule module,",
        "      Executor executor,",
        "      Producer<Integer> aProducer,",
        "      Producer<Double> bProducer,",
        "      Producer<Object> cProducer,",
        "      Provider<Boolean> dProvider) {",
        "    assert module != null;",
        "    this.module = module;",
        "    assert executor != null;",
        "    this.executor = executor;",
        "    assert aProducer != null;",
        "    this.aProducer = aProducer;",
        "    assert bProducer != null;",
        "    this.bProducer = bProducer;",
        "    assert cProducer != null;",
        "    this.cProducer = cProducer;",
        "    assert dProvider != null;",
        "    this.dProvider = dProvider;",
        "  }",
        "",
        "  @Override protected ListenableFuture<String> compute() {",
        "    ListenableFuture<Integer> aProducerFuture = aProducer.get();",
        "    ListenableFuture<Produced<Double>> bProducerFuture =",
        "        Producers.createFutureProduced(bProducer.get());",
        "    return Futures.transform(",
        "        Futures.<Object>allAsList(aProducerFuture, bProducerFuture),",
        "        new AsyncFunction<List<Object>, String>() {",
        "          @SuppressWarnings(\"unchecked\")  // safe by specification",
        "          @Override public ListenableFuture<String> apply(List<Object> args)",
        "              throws IOException {",
        "            return module.produceString(",
        "                (Integer) args.get(0),",
        "                (Produced<Double>) args.get(1),",
        "                cProducer,",
        "                dProvider);",
        "          }",
        "        }, executor);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
  }

  @Test public void singleProducesMethodCheckedExceptionNoArgsFutureSet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProducerModule;",
        "import dagger.producers.Produces;",
        "import java.io.IOException;",
        "",
        "@ProducerModule",
        "final class TestModule {",
        "  @Produces(type = Produces.Type.SET)",
        "  ListenableFuture<String> produceString() throws IOException {",
        "    return null;",
        "  }",
        "}");
    JavaFileObject factoryFile = JavaFileObjects.forSourceLines("TestModule_ProduceStringFactory",
        "package test;",
        "",
        "import com.google.common.util.concurrent.Futures;",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.internal.AbstractProducer;",
        "import dagger.producers.internal.Producers;",
        "import java.io.IOException;",
        "import java.util.Set;",
        "import java.util.concurrent.Callable;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "",
        "@SuppressWarnings(\"unchecked\")",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class TestModule_ProduceStringFactory",
        "    extends AbstractProducer<Set<String>> {",
        "  private final TestModule module;",
        "  private final Executor executor;",
        "",
        "  public TestModule_ProduceStringFactory(TestModule module, Executor executor) {  ",
        "    assert module != null;",
        "    this.module = module;",
        "    assert executor != null;",
        "    this.executor = executor;",
        "  }",
        "",
        "  @Override",
        "  protected ListenableFuture<Set<String>> compute() {  ",
        "    ListenableFuture<ListenableFuture<Set<String>>> future =",
        "        Producers.submitToExecutor(new Callable<ListenableFuture<Set<String>>>() {",
        "      @Override public ListenableFuture<Set<String>> call() throws IOException {",
        "        return Producers.createFutureSingletonSet(module.produceString());",
        "      }",
        "    }, executor);",
        "    return Futures.dereference(future);",
        "  }",
        "}");
    assertAbout(javaSource()).that(moduleFile)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(factoryFile);
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
        .withErrorContaining(PROVIDES_OR_PRODUCES_METHOD_MULTIPLE_QUALIFIERS);
  }
}
