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
package dagger2.internal.codegen;

import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

/**
 * Unit tests for {@link BindingGraphValidator} that exercise producer-specific logic.
 */
@RunWith(JUnit4.class)
public class ProductionGraphValidationTest {
  @Test public void componentWithUnprovidedInput() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.MyComponent",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent(modules = FooModule.class)",
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
    assertAbout(javaSources()).that(Arrays.asList(module, component))
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
        "  @ProductionComponent()",
        "  interface AComponent {",
        "    ListenableFuture<A> getA();",
        "  }",
        "}");
    String expectedError =
        "test.TestClass.A cannot be provided without an @Provides- or @Produces-annotated method.";
    assertAbout(javaSource()).that(component)
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
        "  @ProductionComponent(modules = {AModule.class, BModule.class})",
        "  interface AComponent {",
        "    ListenableFuture<A> getA();",
        "  }",
        "}");
    String expectedError =
        "test.TestClass.A is a provision, which cannot depend on a production.";
    assertAbout(javaSource()).that(component)
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
        "  @ProductionComponent(modules = AModule.class)",
        "  interface AComponent {",
        "    A getA();",
        "  }",
        "}");
    String expectedError =
        "test.TestClass.A is a provision entry-point, which cannot depend on a production.";
    assertAbout(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(expectedError).in(component).onLine(20);
  }
}
