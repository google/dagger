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

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OptionalBindingRequestFulfillmentTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public OptionalBindingRequestFulfillmentTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void inlinedOptionalBindings() throws Exception {
    Source module =
        CompilerTests.javaSource(
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
    Source maybe =
        CompilerTests.javaSource(
            "other.Maybe",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "public class Maybe {",
            "  @Module",
            "  public static class MaybeModule {",
            "    @Provides static Maybe provideMaybe() { return new Maybe(); }",
            "  }",
            "}");
    Source definitelyNot =
        CompilerTests.javaSource(
            "other.DefinitelyNot",
            "package other;",
            "",
            "public class DefinitelyNot {}");

    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(module, maybe, definitelyNot, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void requestForFuture() throws Exception {
    Source module =
        CompilerTests.javaSource(
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
    Source maybe =
        CompilerTests.javaSource(
            "other.Maybe",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "public class Maybe {",
            "  @Module",
            "  public static class MaybeModule {",
            "    @Provides static Maybe provideMaybe() { return new Maybe(); }",
            "  }",
            "}");
    Source definitelyNot =
        CompilerTests.javaSource(
            "other.DefinitelyNot",
            "package other;",
            "",
            "public class DefinitelyNot {}");

    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(module, maybe, definitelyNot, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }
}
