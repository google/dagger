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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.compilerWithOptions;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import dagger.testing.golden.GoldenFileRule;
import javax.tools.JavaFileObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SwitchingProviderTest {

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  @Test
  public void switchingProviderTest() throws Exception {
    ImmutableList.Builder<JavaFileObject> javaFileObjects = ImmutableList.builder();
    StringBuilder entryPoints = new StringBuilder();
    for (int i = 0; i <= 100; i++) {
      String bindingName = "Binding" + i;
      javaFileObjects.add(
          JavaFileObjects.forSourceLines(
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

    javaFileObjects.add(
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = compilerWithAndroidMode().compile(javaFileObjects.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void unscopedBinds() throws Exception {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = compilerWithAndroidMode().compile(module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void scopedBinds() throws Exception {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = compilerWithAndroidMode().compile(module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void emptyMultibindings_avoidSwitchProviders() throws Exception {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = compilerWithAndroidMode().compile(module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void memberInjectors() throws Exception {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "class Foo {}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = compilerWithAndroidMode().compile(foo, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void optionals() throws Exception {
    JavaFileObject present =
        JavaFileObjects.forSourceLines(
            "test.Present",
            "package test;",
            "",
            "class Present {}");
    JavaFileObject absent =
        JavaFileObjects.forSourceLines(
            "test.Absent",
            "package test;",
            "",
            "class Absent {}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = compilerWithAndroidMode().compile(present, absent, module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  private Compiler compilerWithAndroidMode() {
    return compilerWithOptions(CompilerMode.FAST_INIT_MODE.javacopts());
  }
}
