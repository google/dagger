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
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

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
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("is not annotated with @Module or @ProducerModule");
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
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerTestClass_SimpleComponent",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.Producer;",
        "import dagger.producers.internal.Producers;",
        "import java.util.concurrent.Executor;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "import test.TestClass.A;",
        "import test.TestClass.AModule;",
        "import test.TestClass.B;",
        "import test.TestClass.BModule;",
        "import test.TestClass.SimpleComponent;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class DaggerTestClass_SimpleComponent implements SimpleComponent {",
        "  private Provider<B> bProvider;",
        "  private Producer<A> aProducer;",
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
        "  private void initialize(final Builder builder) {",
        "    this.bProvider = TestClass$BModule_BFactory.create(",
        "        builder.bModule, TestClass$C_Factory.create());",
        "    this.aProducer = new TestClass$AModule_AFactory(",
        "        builder.aModule, builder.executor, Producers.producerFromProvider(bProvider));",
        "  }",
        "",
        "  @Override",
        "  public ListenableFuture<A> a() {",
        "    return aProducer.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private AModule aModule;",
        "    private BModule bModule;",
        "    private Executor executor;",
        "",
        "    private Builder() {",
        "    }",
        "",
        "    public SimpleComponent build() {",
        "      if (aModule == null) {",
        "        this.aModule = new AModule();",
        "      }",
        "      if (bModule == null) {",
        "        this.bModule = new BModule();",
        "      }",
        "      if (executor == null) {",
        "        throw new IllegalStateException(\"executor must be set\");",
        "      }",
        "      return new DaggerTestClass_SimpleComponent(this);",
        "    }",
        "",
        "    public Builder aModule(AModule aModule) {",
        "      if (aModule == null) {",
        "        throw new NullPointerException(\"aModule\");",
        "      }",
        "      this.aModule = aModule;",
        "      return this;",
        "    }",
        "",
        "    public Builder bModule(BModule bModule) {",
        "      if (bModule == null) {",
        "        throw new NullPointerException(\"bModule\");",
        "      }",
        "      this.bModule = bModule;",
        "      return this;",
        "    }",
        "",
        "    public Builder executor(Executor executor) {",
        "      if (executor == null) {",
        "        throw new NullPointerException(\"executor\");",
        "      }",
        "      this.executor = executor;",
        "      return this;",
        "    }",
        "  }",
        "}");
    assertAbout(javaSource()).that(component)
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
  }
}
