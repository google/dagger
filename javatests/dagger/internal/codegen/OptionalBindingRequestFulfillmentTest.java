/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.squareup.javapoet.CodeBlock;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OptionalBindingRequestFulfillmentTest {

  public static final CodeBlock NPE_FROM_PROVIDES =
      CodeBlocks.stringLiteral(ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_PROVIDES_METHOD);

  @Test
  public void inlinedOptionalBindings() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.BindsOptionalOf;",
            "import other.Maybe;",
            "import other.DefinitelyNot;",
            "",
            "@Module",
            "interface TestModule {",
            "  @BindsOptionalOf Maybe maybe();",
            "  @BindsOptionalOf DefinitelyNot definitelyNot();",
            "}");
    JavaFileObject maybe =
        JavaFileObjects.forSourceLines(
            "other.Maybe",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "public class Maybe {",
            "  @Module",
            "  public interface MaybeModule {",
            "    @Provides static Maybe provideMaybe() { return new Maybe(); }",
            "  }",
            "}");
    JavaFileObject definitelyNot =
        JavaFileObjects.forSourceLines(
            "other.DefinitelyNot",
            "package other;",
            "",
            "public class DefinitelyNot {}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import dagger.Component;",
            "import dagger.Lazy;",
            "import javax.inject.Provider;",
            "import other.Maybe;",
            "import other.DefinitelyNot;",
            "",
            "@Component(modules = {TestModule.class, Maybe.MaybeModule.class})",
            "interface TestComponent {",
            "  Optional<Maybe> maybe();",
            "  Optional<Provider<Lazy<Maybe>>> providerOfLazyOfMaybe();",
            "  Optional<DefinitelyNot> definitelyNot();",
            "  Optional<Provider<Lazy<DefinitelyNot>>> providerOfLazyOfDefinitelyNot();",
            "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import dagger.Lazy;",
            "import dagger.internal.Preconditions;",
            "import dagger.internal.ProviderOfLazy;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "import other.DefinitelyNot;",
            "import other.Maybe;",
            "import other.Maybe_MaybeModule_ProvideMaybeFactory;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private DaggerTestComponent(Builder builder) {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Optional<Maybe> maybe() {",
            "    return Optional.of(",
            "        Preconditions.checkNotNull(",
            "            Maybe.MaybeModule.provideMaybe(), " + NPE_FROM_PROVIDES + "));",
            "  }",
            "",
            "  @Override",
            "  public Optional<Provider<Lazy<Maybe>>> providerOfLazyOfMaybe() {",
            "    return Optional.of(",
            "        ProviderOfLazy.create(Maybe_MaybeModule_ProvideMaybeFactory.create()));",
            "  }",
            "",
            "  @Override",
            "  public Optional<DefinitelyNot> definitelyNot() {",
            "    return Optional.absent();",
            "  }",
            "",
            "  @Override",
            "  public Optional<Provider<Lazy<DefinitelyNot>>> providerOfLazyOfDefinitelyNot() {",
            "    return Optional.absent();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().compile(module, maybe, definitelyNot, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void requestForFuture() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.BindsOptionalOf;",
            "import other.Maybe;",
            "import other.DefinitelyNot;",
            "",
            "@Module",
            "interface TestModule {",
            "  @BindsOptionalOf Maybe maybe();",
            "  @BindsOptionalOf DefinitelyNot definitelyNot();",
            "}");
    JavaFileObject maybe =
        JavaFileObjects.forSourceLines(
            "other.Maybe",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "public class Maybe {",
            "  @Module",
            "  public interface MaybeModule {",
            "    @Provides static Maybe provideMaybe() { return new Maybe(); }",
            "  }",
            "}");
    JavaFileObject definitelyNot =
        JavaFileObjects.forSourceLines(
            "other.DefinitelyNot",
            "package other;",
            "",
            "public class DefinitelyNot {}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProductionComponent;",
            "import javax.inject.Provider;",
            "import other.Maybe;",
            "import other.DefinitelyNot;",
            "",
            "@ProductionComponent(modules = {TestModule.class, Maybe.MaybeModule.class})",
            "interface TestComponent {",
            "  ListenableFuture<Optional<Maybe>> maybe();",
            "  ListenableFuture<Optional<DefinitelyNot>> definitelyNot();",
            "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import com.google.common.util.concurrent.Futures;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.internal.Preconditions;",
            "import javax.annotation.Generated;",
            "import other.DefinitelyNot;",
            "import other.Maybe;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private DaggerTestComponent(Builder builder) {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public ListenableFuture<Optional<Maybe>> maybe() {",
            "    return Futures.immediateFuture(Optional.of(Preconditions.checkNotNull(",
            "        Maybe.MaybeModule.provideMaybe(), " + NPE_FROM_PROVIDES + ")));",
            "  }",
            "",
            "  @Override",
            "  public ListenableFuture<Optional<DefinitelyNot>> definitelyNot() {",
            "    return Futures.immediateFuture(Optional.<DefinitelyNot>absent());",

            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder testComponent_ProductionExecutorModule(",
            "        TestComponent_ProductionExecutorModule",
            "            testComponent_ProductionExecutorModule) {",
            "      Preconditions.checkNotNull(testComponent_ProductionExecutorModule);",
            "      return this;",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().compile(module, maybe, definitelyNot, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }
}
