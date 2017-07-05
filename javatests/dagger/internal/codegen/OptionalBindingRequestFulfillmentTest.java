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
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OptionalBindingRequestFulfillmentTest {
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
            "import dagger.internal.InstanceFactory;",
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
            "  @SuppressWarnings(\"rawtypes\")",
            "  private static final Provider ABSENT_GUAVA_OPTIONAL_PROVIDER =",
            "      InstanceFactory.create(Optional.absent());",
            "",
            "  private Provider<Optional<Maybe>> optionalOfMaybeProvider;",
            "",
            "  private Provider<Optional<Provider<Lazy<Maybe>>>> ",
            "      optionalOfProviderOfLazyOfMaybeProvider;",
            "",
            "  private Provider<Optional<DefinitelyNot>> optionalOfDefinitelyNotProvider;",
            "",
            "  private Provider<Optional<Provider<Lazy<DefinitelyNot>>>>",
            "      optionalOfProviderOfLazyOfDefinitelyNotProvider;",
            "",
            "  private DaggerTestComponent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "",
            "    this.optionalOfMaybeProvider =",
            "        PresentGuavaOptionalInstanceProvider.of(",
            "            Maybe_MaybeModule_ProvideMaybeFactory.create());",
            "",
            "    this.optionalOfProviderOfLazyOfMaybeProvider =",
            "        PresentGuavaOptionalProviderOfLazyProvider.of(",
            "            Maybe_MaybeModule_ProvideMaybeFactory.create());",
            "",
            "    this.optionalOfDefinitelyNotProvider = absentGuavaOptionalProvider();",
            "",
            "    this.optionalOfProviderOfLazyOfDefinitelyNotProvider = ",
            "        absentGuavaOptionalProvider();",
            "  }",
            "",
            "  @Override",
            "  public Optional<Maybe> maybe() {",
            "    return Optional.of(Maybe.MaybeModule.provideMaybe());",
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
            "  private static <T> Provider<Optional<T>> absentGuavaOptionalProvider() {",
            "    @SuppressWarnings(\"unchecked\")",
            "    Provider<Optional<T>> provider = ",
            "        (Provider<Optional<T>>) ABSENT_GUAVA_OPTIONAL_PROVIDER;",
            "    return provider;",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "  }",
            "",
            "  private static final class PresentGuavaOptionalInstanceProvider<T>",
            "      implements Provider<Optional<T>> {",
            "    private final Provider<T> delegate;",
            "",
            "    private PresentGuavaOptionalInstanceProvider(Provider<T> delegate) {",
            "      this.delegate = Preconditions.checkNotNull(delegate);",
            "    }",
            "",
            "    @Override",
            "    public Optional<T> get() {",
            "      return Optional.of(delegate.get());",
            "    }",
            "",
            "    private static <T> Provider<Optional<T>> of(Provider<T> delegate) {",
            "      return new PresentGuavaOptionalInstanceProvider<T>(delegate);",
            "    }",
            "  }",
            "",
            "  private static final class PresentGuavaOptionalProviderOfLazyProvider<T>",
            "      implements Provider<Optional<Provider<Lazy<T>>>> {",
            "    private final Provider<T> delegate;",
            "",
            "    private PresentGuavaOptionalProviderOfLazyProvider(Provider<T> delegate) {",
            "      this.delegate = Preconditions.checkNotNull(delegate);",
            "    }",
            "",
            "    @Override",
            "    public Optional<Provider<Lazy<T>>> get() {",
            "      return Optional.of(ProviderOfLazy.create(delegate));",
            "    }",
            "",
            "    private static <T> Provider<Optional<Provider<Lazy<T>>>> of(",
            "        Provider<T> delegate) {",
            "      return new PresentGuavaOptionalProviderOfLazyProvider<T>(delegate);",
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
            "import dagger.internal.InstanceFactory;",
            "import dagger.internal.Preconditions;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "import other.DefinitelyNot;",
            "import other.Maybe;",
            "import other.Maybe_MaybeModule_ProvideMaybeFactory;",
            "",
            GENERATED_ANNOTATION,
            "  public final class DaggerTestComponent implements TestComponent {",
            "    @SuppressWarnings(\"rawtypes\")",
            "    private static final Provider ABSENT_GUAVA_OPTIONAL_PROVIDER =",
            "        InstanceFactory.create(Optional.absent());",
            "    private Provider<Optional<Maybe>> optionalOfMaybeProvider;",
            "    private Provider<Optional<DefinitelyNot>> optionalOfDefinitelyNotProvider;",
            "",
            "  private DaggerTestComponent(Builder builder) {",
            "      assert builder != null;",
            "      initialize(builder);",
            "    }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.optionalOfMaybeProvider =",
            "        PresentGuavaOptionalInstanceProvider.of(",
            "            Maybe_MaybeModule_ProvideMaybeFactory.create());",
            "    this.optionalOfDefinitelyNotProvider = absentGuavaOptionalProvider();",
            "  }",
            "",
            "  @Override",
            "  public ListenableFuture<Optional<Maybe>> maybe() {",
            "    return Futures.immediateFuture(Optional.of(Maybe.MaybeModule.provideMaybe()));",
            "  }",
            "",
            "  @Override",
            "  public ListenableFuture<Optional<DefinitelyNot>> definitelyNot() {",
            "    return Futures.immediateFuture(Optional.<DefinitelyNot>absent());",

            "  }",
            "",
            "  private static <T> Provider<Optional<T>> absentGuavaOptionalProvider() {",
            "    @SuppressWarnings(\"unchecked\")",
            "        Provider<Optional<T>> provider = ",
            "            (Provider<Optional<T>>) ABSENT_GUAVA_OPTIONAL_PROVIDER;",
            "    return provider;",
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
            "",
            "  private static final class PresentGuavaOptionalInstanceProvider<T>",
            "      implements Provider<Optional<T>> {",
            "    private final Provider<T> delegate;",
            "",
            "    private PresentGuavaOptionalInstanceProvider(Provider<T> delegate) {",
            "      this.delegate = Preconditions.checkNotNull(delegate);",
            "    }",
            "",
            "    @Override",
            "    public Optional<T> get() {",
            "      return Optional.of(delegate.get());",
            "    }",
            "",
            "    private static <T> Provider<Optional<T>> of(Provider<T> delegate) {",
            "      return new PresentGuavaOptionalInstanceProvider<T>(delegate);",
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
