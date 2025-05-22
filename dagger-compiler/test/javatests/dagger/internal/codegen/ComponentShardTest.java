/*
 * Copyright (C) 2020 The Dagger Authors.
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

import static java.util.stream.Collectors.joining;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComponentShardTest {
  private static final int BINDINGS_PER_SHARD = 2;

  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public ComponentShardTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void testNewShardCreated() throws Exception {
    // Add all bindings.
    //
    //     1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7
    //          ^--------/
    //
    ImmutableList.Builder<Source> sources = ImmutableList.builder();
    sources
        // Shard 2: Bindings (1)
        .add(createBinding("Binding1", "Binding2 binding2"))
        // Shard 1: Bindings (2, 3, 4, 5). Contains more than 2 bindings due to cycle.
        .add(createBinding("Binding2", "Binding3 binding3"))
        .add(createBinding("Binding3", "Binding4 binding4"))
        .add(createBinding("Binding4", "Binding5 binding5, Provider<Binding2> binding2Provider"))
        .add(createBinding("Binding5", "Binding6 binding6"))
        // Component shard: Bindings (6, 7)
        .add(createBinding("Binding6", "Binding7 binding7"))
        .add(createBinding("Binding7"));

    // Add the component with entry points for each binding and its provider.
    sources.add(
        CompilerTests.javaSource(
            "dagger.internal.codegen.TestComponent",
            "package dagger.internal.codegen;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface TestComponent {",
            "  Binding1 binding1();",
            "  Binding2 binding2();",
            "  Binding3 binding3();",
            "  Binding4 binding4();",
            "  Binding5 binding5();",
            "  Binding6 binding6();",
            "  Binding7 binding7();",
            "  Provider<Binding1> providerBinding1();",
            "  Provider<Binding2> providerBinding2();",
            "  Provider<Binding3> providerBinding3();",
            "  Provider<Binding4> providerBinding4();",
            "  Provider<Binding5> providerBinding5();",
            "  Provider<Binding6> providerBinding6();",
            "  Provider<Binding7> providerBinding7();",
            "}"));

    CompilerTests.daggerCompiler(sources.build())
        .withProcessingOptions(compilerOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("dagger/internal/codegen/DaggerTestComponent"));
            });
  }

  @Test
  public void testNewShardCreatedWithDependencies() throws Exception {
    ImmutableList.Builder<Source> sources = ImmutableList.builder();
    sources.add(
        createBinding("Binding1"),
        createBinding("Binding2"),
        CompilerTests.javaSource(
            "dagger.internal.codegen.Binding3",
            "package dagger.internal.codegen;",
            "",
            "class Binding3 {}"),
        CompilerTests.javaSource(
            "dagger.internal.codegen.Dependency",
            "package dagger.internal.codegen;",
            "",
            "interface Dependency {",
            "  Binding3 binding3();",
            "}"),
        CompilerTests.javaSource(
            "dagger.internal.codegen.TestComponent",
            "package dagger.internal.codegen;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(dependencies = Dependency.class)",
            "interface TestComponent {",
            "  Binding1 binding1();",
            "  Binding2 binding2();",
            "  Binding3 binding3();",
            "  Provider<Binding1> providerBinding1();",
            "  Provider<Binding2> providerBinding2();",
            "  Provider<Binding3> providerBinding3();",
            "}"));

    CompilerTests.daggerCompiler(sources.build())
        .withProcessingOptions(compilerOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("dagger/internal/codegen/DaggerTestComponent"));
            });
  }

  @Test
  public void testNewShardSubcomponentCreated() throws Exception {
    ImmutableList.Builder<Source> sources = ImmutableList.builder();
    sources.add(
        CompilerTests.javaSource(
            "dagger.internal.codegen.SubcomponentScope",
            "package dagger.internal.codegen;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "public @interface SubcomponentScope {}"),
        CompilerTests.javaSource(
            "dagger.internal.codegen.Binding1",
            "package dagger.internal.codegen;",
            "",
            "@SubcomponentScope",
            "final class Binding1 {",
            "  @javax.inject.Inject Binding1() {}",
            "}"),
        CompilerTests.javaSource(
            "dagger.internal.codegen.Binding2",
            "package dagger.internal.codegen;",
            "",
            "@SubcomponentScope",
            "final class Binding2 {",
            "  @javax.inject.Inject Binding2() {}",
            "}"),
        CompilerTests.javaSource(
            "dagger.internal.codegen.Binding3",
            "package dagger.internal.codegen;",
            "",
            "@SubcomponentScope",
            "final class Binding3 {",
            "  @javax.inject.Inject Binding3() {}",
            "}"),
        CompilerTests.javaSource(
            "dagger.internal.codegen.TestComponent",
            "package dagger.internal.codegen;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  TestSubcomponent subcomponent();",
            "}"),
        CompilerTests.javaSource(
            "dagger.internal.codegen.TestSubcomponent",
            "package dagger.internal.codegen;",
            "",
            "import dagger.Subcomponent;",
            "import javax.inject.Provider;",
            "",
            "@SubcomponentScope",
            "@Subcomponent",
            "interface TestSubcomponent {",
            "  Binding1 binding1();",
            "  Binding2 binding2();",
            "  Binding3 binding3();",
            "  Provider<Binding1> providerBinding1();",
            "  Provider<Binding2> providerBinding2();",
            "  Provider<Binding3> providerBinding3();",
            "}"));

    CompilerTests.daggerCompiler(sources.build())
        .withProcessingOptions(compilerOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("dagger/internal/codegen/DaggerTestComponent"));
            });
  }

  private static Source createBinding(String bindingName, String... deps) {
    return CompilerTests.javaSource(
        "dagger.internal.codegen." + bindingName,
        "package dagger.internal.codegen;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "import javax.inject.Singleton;",
        "",
        "@Singleton",
        "final class " + bindingName + " {",
        "  @Inject",
        "  " + bindingName + "(" + Arrays.stream(deps).collect(joining(", ")) + ") {}",
        "}");
  }

  private ImmutableMap<String, String> compilerOptions() {
    return ImmutableMap.<String, String>builder()
        .putAll(compilerMode.processorOptions())
        .put("dagger.generatedClassExtendsComponent", "DISABLED")
        .put("dagger.keysPerComponentShard", Integer.toString(BINDINGS_PER_SHARD))
        .buildOrThrow();
  }
}
