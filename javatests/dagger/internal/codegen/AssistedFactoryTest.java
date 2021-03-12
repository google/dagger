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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;

import com.google.common.collect.ImmutableCollection;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssistedFactoryTest {
  @Parameters(name = "{0}")
  public static ImmutableCollection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public AssistedFactoryTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void testAssistedFactory() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted String str, Bar bar) {}",
            "}");
    JavaFileObject fooFactory =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory {",
            "  Foo create(String factoryStr);",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, bar, fooFactory, component);
    assertThat(compilation).succeeded();
    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;", "", GeneratedLines.generatedAnnotations())
            .addLinesIn(
                FAST_INIT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "",
                "  private Foo foo(String str) {",
                "    return new Foo(str, new Bar());",
                "  }",
                "",
                "  @Override",
                "  public FooFactory fooFactory() {",
                "    return new FooFactory() {",
                "      @Override",
                "      public Foo create(String str) {",
                "        return DaggerTestComponent.this.foo(str);",
                "      }",
                "    };",
                "  }",
                "}")
            .addLinesIn(
                DEFAULT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "",
                "  private Foo_Factory fooProvider;",
                "",
                "  private Provider<FooFactory> fooFactoryProvider;",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.fooProvider = Foo_Factory.create(Bar_Factory.create());",
                "    this.fooFactoryProvider = FooFactory_Impl.create(fooProvider);",
                "  }",
                "",
                "  @Override",
                "  public FooFactory fooFactory() {",
                "    return fooFactoryProvider.get();",
                "  }",
                "}")
            .build();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void testAssistedFactoryCycle() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(@Assisted String str, Bar bar) {}",
            "}");
    JavaFileObject fooFactory =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "interface FooFactory {",
            "  Foo create(String factoryStr);",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(FooFactory fooFactory) {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory fooFactory();",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(foo, bar, fooFactory, component);
    assertThat(compilation).succeeded();
    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;", "", GeneratedLines.generatedAnnotations())
            .addLinesIn(
                FAST_INIT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "",
                "  private Bar bar() {",
                "    return new Bar(fooFactory());",
                "  }",
                "",
                "  private Foo foo(String str) {",
                "    return new Foo(str, bar());",
                "  }",
                "",
                "  @Override",
                "  public FooFactory fooFactory() {",
                "    return new FooFactory() {",
                "      @Override",
                "      public Foo create(String str) {",
                "        return DaggerTestComponent.this.foo(str);",
                "      }",
                "    };",
                "  }",
                "}")
            .addLinesIn(
                DEFAULT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "",
                "  private Provider<FooFactory> fooFactoryProvider;",
                "",
                "  private Provider<Bar> barProvider;",
                "",
                "  private Foo_Factory fooProvider;",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.fooFactoryProvider = new DelegateFactory<>();",
                "    this.barProvider = Bar_Factory.create(fooFactoryProvider);",
                "    this.fooProvider = Foo_Factory.create(barProvider);",
                "    DelegateFactory.setDelegate(",
                "        fooFactoryProvider, FooFactory_Impl.create(fooProvider));",
                "  }",
                "",
                "  @Override",
                "  public FooFactory fooFactory() {",
                "    return fooFactoryProvider.get();",
                "  }",
                "}")
            .build();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }
}
