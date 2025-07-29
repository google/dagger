/*
 * Copyright (C) 2024 The Dagger Authors.
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SetMultibindingValidationTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public SetMultibindingValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void setBindingOfProduced_provides() {
    Source providesModule =
        CompilerTests.javaSource(
            "test.SetModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.producers.Produced;",
            "",
            "@Module",
            "abstract class SetModule {",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static Produced<String> provideProducer() {",
            "    return null;",
            "  }",
            "}");

    // Entry points aren't needed because the check we care about here is a module validation
    Source providesComponent = component("");

    CompilerTests.daggerCompiler(providesModule, providesComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "@Provides methods with @IntoSet/@ElementsIntoSet must not return framework "
                  + "types");
              subject.hasErrorContaining("test.SetModule has errors")
                  .onSource(providesComponent)
                  .onLineContaining("@Component(modules = {SetModule.class})");
            });
  }

  @Test
  public void setBindingOfProduced_binds() {

    Source bindsModule =
        CompilerTests.javaSource(
            "test.SetModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Binds;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.producers.Produced;",
            "",
            "@Module",
            "abstract class SetModule {",
            "",
            "  @Binds",
            "  @IntoSet",
            "  abstract Produced<String> provideProvider(Produced<String> impl);",
            "}");

    // Entry points aren't needed because the check we care about here is a module validation
    Source bindsComponent = component("");

    CompilerTests.daggerCompiler(bindsModule, bindsComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "@Binds methods with @IntoSet/@ElementsIntoSet must not return framework types");
              subject.hasErrorContaining("test.SetModule has errors")
                  .onSource(bindsComponent)
                  .onLineContaining("@Component(modules = {SetModule.class})");
            });
  }

  @Test
  public void elementsIntoSetBindingOfProduced_provides() {
    Source providesModule =
        CompilerTests.javaSource(
            "test.SetModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import dagger.producers.Produced;",
            "import java.util.Set;",
            "",
            "@Module",
            "abstract class SetModule {",
            "",
            "  @Provides",
            "  @ElementsIntoSet",
            "  static Set<Produced<String>> provideProducer() {",
            "    return null;",
            "  }",
            "}");

    // Entry points aren't needed because the check we care about here is a module validation
    Source providesComponent = component("");

    CompilerTests.daggerCompiler(providesModule, providesComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "@Provides methods with @IntoSet/@ElementsIntoSet must not return framework "
                  + "types");
              subject.hasErrorContaining("test.SetModule has errors")
                  .onSource(providesComponent)
                  .onLineContaining("@Component(modules = {SetModule.class})");
            });
  }

  @Test
  public void elementsIntoSetBindingOfProduced_binds() {

    Source bindsModule =
        CompilerTests.javaSource(
            "test.SetModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Binds;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import dagger.producers.Produced;",
            "import java.util.Set;",
            "",
            "@Module",
            "abstract class SetModule {",
            "",
            "  @Binds",
            "  @ElementsIntoSet",
            "  abstract Set<Produced<String>> provideProvider(Set<Produced<String>> impl);",
            "}");

    // Entry points aren't needed because the check we care about here is a module validation
    Source bindsComponent = component("");

    CompilerTests.daggerCompiler(bindsModule, bindsComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(
                  "@Binds methods with @IntoSet/@ElementsIntoSet must not return framework types");
              subject.hasErrorContaining("test.SetModule has errors")
                  .onSource(bindsComponent)
                  .onLineContaining("@Component(modules = {SetModule.class})");
            });
  }

  private static Source component(String... entryPoints) {
    return CompilerTests.javaSource(
        "test.TestComponent",
        ImmutableList.<String>builder()
            .add(
                "package test;",
                "",
                "import dagger.Component;",
                "import dagger.producers.Producer;",
                "import java.util.Set;",
                "import javax.inject.Provider;",
                "",
                "@Component(modules = {SetModule.class})",
                "interface TestComponent {")
            .add(entryPoints)
            .add("}")
            .build());
  }
}
