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
                "  private final DaggerTestComponent testComponent = this;",
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
                "        return testComponent.foo(str);",
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
                "  private final DaggerTestComponent testComponent = this;",
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
                "        return testComponent.foo(str);",
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

  @Test
  public void testInjectParamDuplicateNames() {
    JavaFileObject componentSrc =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @Component.Factory",
            "  interface Factory {",
            "    TestComponent create(@BindsInstance Bar arg);",
            "  }",
            "  FooFactory getFooFactory();",
            "}");
    JavaFileObject factorySrc =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory {",
            "  Foo create(Integer arg);",
            "}");
    JavaFileObject barSrc =
        JavaFileObjects.forSourceLines("test.Bar", "package test;", "", "interface Bar {}");
    JavaFileObject injectSrc =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(Bar bar, @Assisted Integer arg) {}",
            "}");
    JavaFileObject generatedSrc =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;", "", GeneratedLines.generatedAnnotations())
            .addLinesIn(
                FAST_INIT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private final Bar arg2;",
                "  private final DaggerTestComponent testComponent = this;",
                "",
                "  private DaggerTestComponent(Bar argParam) {",
                "    this.arg2 = argParam;",
                "  }",
                "",
                "  public static TestComponent.Factory factory() {",
                "    return new Factory();",
                "  }",
                "",
                "  private Foo foo(Integer arg) {",
                "    return new Foo(arg2, arg);",
                "  }",
                "",
                "  @Override",
                "  public FooFactory getFooFactory() {",
                "    return new FooFactory() {",
                "      @Override",
                "      public Foo create(Integer arg) {",
                "        return testComponent.foo(arg);",
                "      }",
                "    };",
                "  }",
                "",
                "  private static final class Factory implements TestComponent.Factory {",
                "    @Override",
                "    public TestComponent create(Bar arg) {",
                "      Preconditions.checkNotNull(arg);",
                "      return new DaggerTestComponent(arg);",
                "    }",
                "  }",
                "}")
            .addLinesIn(
                DEFAULT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private final DaggerTestComponent testComponent = this;",
                "  private Provider<Bar> argProvider;",
                "  private Foo_Factory fooProvider;",
                "  private Provider<FooFactory> fooFactoryProvider;",
                "",
                "  private DaggerTestComponent(Bar argParam) {",
                "    initialize(argParam);",
                "  }",
                "",
                "  public static TestComponent.Factory factory() {",
                "    return new Factory();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final Bar argParam) {",
                "    this.argProvider = InstanceFactory.create(argParam);",
                "    this.fooProvider = Foo_Factory.create(argProvider);",
                "    this.fooFactoryProvider = FooFactory_Impl.create(fooProvider);",
                "  }",
                "",
                "  @Override",
                "  public FooFactory getFooFactory() {",
                "    return fooFactoryProvider.get();",
                "  }",
                "",
                "  private static final class Factory implements TestComponent.Factory {",
                "    @Override",
                "    public TestComponent create(Bar arg) {",
                "      Preconditions.checkNotNull(arg);",
                "      return new DaggerTestComponent(arg);",
                "    }",
                "  }",
                "}")
            .build();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(componentSrc, factorySrc, barSrc, injectSrc);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedSrc);
  }

  @Test
  public void testAssistParamDuplicateNames() {
    JavaFileObject componentSrc =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @Component.Factory",
            "  interface Factory {",
            "    TestComponent create(@BindsInstance Bar arg);",
            "  }",
            "  Bar getBar();",
            "  FooFactory getFooFactory();",
            "}");
    JavaFileObject factorySrc =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory {",
            "  Foo create(Integer arg);",
            "}");
    JavaFileObject barSrc =
        JavaFileObjects.forSourceLines("test.Bar", "package test;", "", "interface Bar {}");
    JavaFileObject injectSrc =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(Bar bar, @Assisted Integer arg) {}",
            "}");
    JavaFileObject generatedSrc =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;", "", GeneratedLines.generatedAnnotations())
            .addLinesIn(
                FAST_INIT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private final Bar arg;",
                "",
                "  private final DaggerTestComponent testComponent = this;",
                "",
                "  private DaggerTestComponent(Bar argParam) {",
                "    this.arg = argParam;",
                "",
                "  }",
                "",
                "  public static TestComponent.Factory factory() {",
                "    return new Factory();",
                "  }",
                "",
                "  private Foo foo(Integer arg2) {",
                "    return new Foo(arg, arg2);",
                "  }",
                "",
                "  @Override",
                "  public Bar getBar() {",
                "    return arg;",
                "  }",
                "",
                "  @Override",
                "  public FooFactory getFooFactory() {",
                "    return new FooFactory() {",
                "      @Override",
                "      public Foo create(Integer arg) {",
                "        return testComponent.foo(arg);",
                "      }",
                "    };",
                "  }",
                "",
                "  private static final class Factory implements TestComponent.Factory {",
                "    @Override",
                "    public TestComponent create(Bar arg) {",
                "      Preconditions.checkNotNull(arg);",
                "      return new DaggerTestComponent(arg);",
                "    }",
                "  }",
                "}")
            .addLinesIn(
                DEFAULT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private final Bar arg;",
                "  private final DaggerTestComponent testComponent = this;",
                "  private Provider<Bar> argProvider;",
                "  private Foo_Factory fooProvider;",
                "  private Provider<FooFactory> fooFactoryProvider;",
                "",
                "  private DaggerTestComponent(Bar argParam) {",
                "    this.arg = argParam;",
                "    initialize(argParam);",
                "  }",
                "",
                "  public static TestComponent.Factory factory() {",
                "    return new Factory();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final Bar argParam) {",
                "    this.argProvider = InstanceFactory.create(argParam);",
                "    this.fooProvider = Foo_Factory.create(argProvider);",
                "    this.fooFactoryProvider = FooFactory_Impl.create(fooProvider);",
                "  }",
                "",
                "  @Override",
                "  public Bar getBar() {",
                "    return arg;",
                "  }",
                "",
                "  @Override",
                "  public FooFactory getFooFactory() {",
                "    return fooFactoryProvider.get();",
                "  }",
                "",
                "  private static final class Factory implements TestComponent.Factory {",
                "    @Override",
                "    public TestComponent create(Bar arg) {",
                "      Preconditions.checkNotNull(arg);",
                "      return new DaggerTestComponent(arg);",
                "    }",
                "  }",
                "}")
            .build();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(componentSrc, factorySrc, barSrc, injectSrc);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedSrc);
  }

  @Test
  public void testFactoryGeneratorDuplicatedParamNames() {
    JavaFileObject componentSrc =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @Component.Factory",
            "  interface Factory {",
            "    TestComponent create(@BindsInstance Bar arg);",
            "}",
            "  FooFactory getFooFactory();",
            "}");
    JavaFileObject factorySrc =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory {",
            "  Foo create(Integer arg);",
            "}");
    JavaFileObject barSrc =
        JavaFileObjects.forSourceLines("test.Bar", "package test;", "", "interface Bar {}");
    JavaFileObject injectSrc =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo {",
            "  @AssistedInject",
            "  Foo(Bar arg, @Assisted Integer argProvider) {}",
            "}");
    JavaFileObject generatedSrc =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;", "", GeneratedLines.generatedAnnotations())
            .addLinesIn(
                FAST_INIT_MODE,
                "public final class Foo_Factory {",
                "  private final Provider<Bar> argProvider;",
                "",
                "  public Foo_Factory(Provider<Bar> argProvider) {",
                "    this.argProvider = argProvider;",
                "  }",
                "",
                "  public Foo get(Integer argProvider2) {",
                "    return newInstance(argProvider.get(), argProvider2);",
                "  }",
                "",
                "  public static Foo_Factory create(Provider<Bar> argProvider) {",
                "    return new Foo_Factory(argProvider);",
                "  }",
                "",
                "  public static Foo newInstance(Object arg, Integer argProvider) {",
                "    return new Foo((Bar) arg, argProvider);",
                "  }",
                "}")
            .addLinesIn(
                DEFAULT_MODE,
                "public final class Foo_Factory {",
                "  private final Provider<Bar> argProvider;",
                "",
                "  public Foo_Factory(Provider<Bar> argProvider) {",
                "    this.argProvider = argProvider;",
                "  }",
                "",
                "  public Foo get(Integer argProvider2) {",
                "    return newInstance(argProvider.get(), argProvider2);",
                "  }",
                "",
                "  public static Foo_Factory create(Provider<Bar> argProvider) {",
                "    return new Foo_Factory(argProvider);",
                "  }",
                "",
                "  public static Foo newInstance(Object arg, Integer argProvider) {",
                "    return new Foo((Bar) arg, argProvider);",
                "  }",
                "}")
            .build();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(componentSrc, factorySrc, barSrc, injectSrc);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.Foo_Factory")
        .containsElementsIn(generatedSrc);
  }

  @Test
  public void testParameterizedAssistParam() {
    JavaFileObject componentSrc =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  FooFactory<String> getFooFactory();",
            "}");
    JavaFileObject factorySrc =
        JavaFileObjects.forSourceLines(
            "test.FooFactory",
            "package test;",
            "",
            "import dagger.assisted.AssistedFactory;",
            "",
            "@AssistedFactory",
            "public interface FooFactory<T> {",
            "  Foo<T> create(T arg);",
            "}");
    JavaFileObject injectSrc =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.assisted.Assisted;",
            "import dagger.assisted.AssistedInject;",
            "",
            "class Foo<T> {",
            "  @AssistedInject",
            "  Foo(@Assisted T arg) {}",
            "}");
    JavaFileObject generatedSrc =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines("package test;", "", GeneratedLines.generatedAnnotations())
            .addLinesIn(
                FAST_INIT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private final DaggerTestComponent testComponent = this;",
                "",
                "  private DaggerTestComponent() {",
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
                "private Foo<String> fooOfString(String arg) {",
                "  return new Foo<String>(arg);",
                "}",
                "",
                "  @Override",
                "  public FooFactory<String> getFooFactory() {",
                "    return new FooFactory<String>() {",
                "      @Override",
                "      public Foo<String> create(String arg) {",
                "        return testComponent.fooOfString(arg);",
                "      }",
                "    };",
                "  }",
                "}")
            .addLinesIn(
                DEFAULT_MODE,
                "final class DaggerTestComponent implements TestComponent {",
                "  private final DaggerTestComponent testComponent = this;",
                "  private Foo_Factory<String> fooProvider;",
                "  private Provider<FooFactory<String>> fooFactoryProvider;",
                "",
                "  private DaggerTestComponent() {",
                "    initialize();",
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
                "  private void initialize() {",
                "    this.fooProvider = Foo_Factory.create();",
                "    this.fooFactoryProvider = FooFactory_Impl.create(fooProvider);",
                "  }",
                "",
                "  @Override",
                "  public FooFactory<String> getFooFactory() {",
                "    return fooFactoryProvider.get();",
                "  }",
                "}")
            .build();
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(componentSrc, factorySrc, injectSrc);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedSrc);
  }
}
