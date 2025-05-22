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
public class SwitchingProviderTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public SwitchingProviderTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void switchingProviderTest() throws Exception {
    ImmutableList.Builder<Source> sources = ImmutableList.builder();
    StringBuilder entryPoints = new StringBuilder();
    for (int i = 0; i <= 100; i++) {
      String bindingName = "Binding" + i;
      sources.add(
          CompilerTests.javaSource(
              "test." + bindingName,
              "package test;",
              "",
              "import javax.inject.Inject;",
              "",
              "final class " + bindingName + " {",
              "  @Inject",
              "  " + bindingName + "() {}",
              "}"));
      entryPoints.append(String.format("  Provider<%1$s> get%1$sProvider();\n", bindingName));
    }

    sources.add(
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface TestComponent {",
            entryPoints.toString(),
            "}"));

    CompilerTests.daggerCompiler(sources.build())
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void unscopedBinds() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String s() {",
            "    return new String();",
            "  }",
            "",
            "  @Binds CharSequence c(String s);",
            "  @Binds Object o(CharSequence c);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Object> objectProvider();",
            "  Provider<CharSequence> charSequenceProvider();",
            "}");

    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void scopedBinds() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String s() {",
            "    return new String();",
            "  }",
            "",
            "  @Binds @Singleton Object o(CharSequence s);",
            "  @Binds @Singleton CharSequence c(String s);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Object> objectProvider();",
            "  Provider<CharSequence> charSequenceProvider();",
            "}");

    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void emptyMultibindings_avoidSwitchProviders() throws Exception {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.multibindings.Multibinds;",
            "import dagger.Module;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Multibinds Set<String> set();",
            "  @Multibinds Map<String, String> map();",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Map;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Set<String>> setProvider();",
            "  Provider<Map<String, String>> mapProvider();",
            "}");

    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void memberInjectors() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "class Foo {}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Provider<MembersInjector<Foo>> providerOfMembersInjector();",
            "}");

    CompilerTests.daggerCompiler(foo, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void optionals() throws Exception {
    Source present =
        CompilerTests.javaSource(
            "test.Present",
            "package test;",
            "",
            "class Present {}");
    Source absent =
        CompilerTests.javaSource(
            "test.Absent",
            "package test;",
            "",
            "class Absent {}");
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @BindsOptionalOf Present bindOptionalOfPresent();",
            "  @BindsOptionalOf Absent bindOptionalOfAbsent();",
            "",
            "  @Provides static Present p() { return new Present(); }",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Optional;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Optional<Present>> providerOfOptionalOfPresent();",
            "  Provider<Optional<Absent>> providerOfOptionalOfAbsent();",
            "}");

    CompilerTests.daggerCompiler(present, absent, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }
}
