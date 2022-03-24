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
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComponentShardTest {
  private static final int BINDINGS_PER_SHARD = 2;

  @Parameters(name = "{0}")
  public static ImmutableCollection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ComponentShardTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void testNewShardCreated() {
    // Add all bindings.
    //
    //     1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7
    //          ^--------/
    //
    ImmutableList.Builder<JavaFileObject> javaFileObjects = ImmutableList.builder();
    javaFileObjects
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
    javaFileObjects.add(
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = compiler().compile(javaFileObjects.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("dagger.internal.codegen.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("dagger.internal.codegen.DaggerTestComponent")
                .addLines(
                    "package dagger.internal.codegen;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent {",
                    "  private static final class TestComponentImpl implements TestComponent {",
                    "    private TestComponentImplShard testComponentImplShard;",
                    "    private TestComponentImplShard2 testComponentImplShard2;",
                    "    private final TestComponentImpl testComponentImpl = this;",
                    "    private Provider<Binding7> binding7Provider;",
                    "    private Provider<Binding6> binding6Provider;",
                    "",
                    "    private TestComponentImpl() {",
                    "      initialize();",
                    "      testComponentImplShard =",
                    "          new TestComponentImplShard(testComponentImpl);",
                    "      testComponentImplShard2 =",
                    "          new TestComponentImplShard2(testComponentImpl);",
                    "    }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding7Provider =",
                    "          DoubleCheck.provider(Binding7_Factory.create());",
                    "      this.binding6Provider =",
                    "          DoubleCheck.provider(Binding6_Factory.create(binding7Provider));",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding7Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding7>(testComponentImpl, 1));",
                    "      this.binding6Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding6>(testComponentImpl, 0));",
                    "    }")
                .addLines(
                    "    @Override",
                    "    public Binding1 binding1() {",
                    "      return testComponentImpl",
                    "          .testComponentImplShard2.binding1Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding2 binding2() {",
                    "      return testComponentImpl.testComponentImplShard.binding2Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding3 binding3() {",
                    "      return testComponentImpl.testComponentImplShard.binding3Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding4 binding4() {",
                    "      return testComponentImpl.testComponentImplShard.binding4Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding5 binding5() {",
                    "      return testComponentImpl.testComponentImplShard.binding5Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding6 binding6() {",
                    "      return binding6Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding7 binding7() {",
                    "      return binding7Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding1> providerBinding1() {",
                    "      return testComponentImpl.testComponentImplShard2.binding1Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding2> providerBinding2() {",
                    "      return testComponentImpl.testComponentImplShard.binding2Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding3> providerBinding3() {",
                    "      return testComponentImpl.testComponentImplShard.binding3Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding4> providerBinding4() {",
                    "      return testComponentImpl.testComponentImplShard.binding4Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding5> providerBinding5() {",
                    "      return testComponentImpl.testComponentImplShard.binding5Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding6> providerBinding6() {",
                    "      return binding6Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding7> providerBinding7() {",
                    "      return binding7Provider;",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    private static final class SwitchingProvider<T> implements Provider<T> {",
                    "      @SuppressWarnings(\"unchecked\")",
                    "      @Override",
                    "      public T get() {",
                    "        switch (id) {",
                    "          case 0: return (T) new Binding6(",
                    "              testComponentImpl.binding7Provider.get());",
                    "          case 1: return (T) new Binding7();",
                    "          default: throw new AssertionError(id);",
                    "        }",
                    "      }",
                    "    }")
                .addLines(
                    "  }",
                    "",
                    "  private static final class TestComponentImplShard {",
                    "    private final TestComponentImpl testComponentImpl;",
                    "    private Provider<Binding5> binding5Provider;",
                    "    private Provider<Binding2> binding2Provider;",
                    "    private Provider<Binding4> binding4Provider;",
                    "    private Provider<Binding3> binding3Provider;",
                    "",
                    "    private TestComponentImplShard(TestComponentImpl testComponentImpl) {",
                    "      this.testComponentImpl = testComponentImpl;",
                    "      initialize();",
                    "    }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding5Provider =",
                    "          DoubleCheck.provider(",
                    "              Binding5_Factory.create(testComponentImpl.binding6Provider));",
                    "      this.binding2Provider = new DelegateFactory<>();",
                    "      this.binding4Provider =",
                    "          DoubleCheck.provider(",
                    "              Binding4_Factory.create(binding5Provider, binding2Provider));",
                    "      this.binding3Provider =",
                    "          DoubleCheck.provider(Binding3_Factory.create(binding4Provider));",
                    "      DelegateFactory.setDelegate(",
                    "          binding2Provider,",
                    "          DoubleCheck.provider(Binding2_Factory.create(binding3Provider)));",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding5Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding5>(testComponentImpl, 3));",
                    "      this.binding4Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding4>(testComponentImpl, 2));",
                    "      this.binding3Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding3>(testComponentImpl, 1));",
                    "      this.binding2Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding2>(testComponentImpl, 0));",
                    "    }",
                    "",
                    "    private static final class SwitchingProvider<T> implements Provider<T> {",
                    "      @SuppressWarnings(\"unchecked\")",
                    "      @Override",
                    "      public T get() {",
                    "        switch (id) {",
                    "          case 0: return (T) new Binding2(",
                    "              testComponentImpl.testComponentImplShard",
                    "                  .binding3Provider.get());",
                    "          case 1: return (T) new Binding3(",
                    "              testComponentImpl.testComponentImplShard",
                    "                  .binding4Provider.get());",
                    "          case 2: return (T) new Binding4(",
                    "              testComponentImpl.testComponentImplShard",
                    "                  .binding5Provider.get(),",
                    "              testComponentImpl.testComponentImplShard.binding2Provider);",
                    "          case 3: return (T) new Binding5(",
                    "              testComponentImpl.binding6Provider.get());",
                    "          default: throw new AssertionError(id);",
                    "        }",
                    "      }",
                    "    }")
                .addLines(
                    "  }",
                    "",
                    "  private static final class TestComponentImplShard2 {",
                    "    private final TestComponentImpl testComponentImpl;",
                    "    private Provider<Binding1> binding1Provider;",
                    "",
                    "    private TestComponentImplShard2(TestComponentImpl testComponentImpl) {",
                    "      this.testComponentImpl = testComponentImpl;",
                    "      initialize();",
                    "    }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding1Provider =",
                    "          DoubleCheck.provider(",
                    "              Binding1_Factory.create(",
                    "                  testComponentImpl",
                    "                      .testComponentImplShard.binding2Provider));",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding1Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding1>(testComponentImpl, 0));",
                    "    }",
                    "",
                    "    private static final class SwitchingProvider<T> implements Provider<T> {",
                    "      @SuppressWarnings(\"unchecked\")",
                    "      @Override",
                    "      public T get() {",
                    "        switch (id) {",
                    "          case 0: return (T) new Binding1(",
                    "              testComponentImpl.testComponentImplShard",
                    "                  .binding2Provider.get());",
                    "          default: throw new AssertionError(id);",
                    "        }",
                    "      }",
                    "    }")
                .build());
  }

  @Test
  public void testNewShardCreatedWithDependencies() {
    ImmutableList.Builder<JavaFileObject> javaFileObjects = ImmutableList.builder();
    javaFileObjects.add(
        createBinding("Binding1"),
        createBinding("Binding2"),
        JavaFileObjects.forSourceLines(
            "dagger.internal.codegen.Binding3",
            "package dagger.internal.codegen;",
            "",
            "class Binding3 {}"),
        JavaFileObjects.forSourceLines(
            "dagger.internal.codegen.Dependency",
            "package dagger.internal.codegen;",
            "",
            "interface Dependency {",
            "  Binding3 binding3();",
            "}"),
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = compiler().compile(javaFileObjects.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("dagger.internal.codegen.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("dagger.internal.codegen.DaggerTestComponent")
                .addLines(
                    "package dagger.internal.codegen;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent {",
                    "  private static final class TestComponentImpl implements TestComponent {",
                    "    private TestComponentImplShard testComponentImplShard;",
                    "    private final Dependency dependency;",
                    "    private final TestComponentImpl testComponentImpl = this;",
                    "    private Provider<Binding1> binding1Provider;",
                    "    private Provider<Binding2> binding2Provider;",
                    "",
                    "    private TestComponentImpl(Dependency dependencyParam) {",
                    "      this.dependency = dependencyParam;",
                    "      initialize(dependencyParam);",
                    "      testComponentImplShard =",
                    "          new TestComponentImplShard(testComponentImpl, dependencyParam);",
                    "    }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize(final Dependency dependencyParam) {",
                    "      this.binding1Provider =",
                    "          DoubleCheck.provider(Binding1_Factory.create());",
                    "      this.binding2Provider =",
                    "          DoubleCheck.provider(Binding2_Factory.create());",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize(final Dependency dependencyParam) {",
                    "      this.binding1Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding1>(testComponentImpl, 0));",
                    "      this.binding2Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding2>(testComponentImpl, 1));",
                    "    }")
                .addLines(
                    "    @Override",
                    "    public Binding1 binding1() {",
                    "      return binding1Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding2 binding2() {",
                    "      return binding2Provider.get();",
                    "    }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    @Override",
                    "    public Binding3 binding3() {",
                    "      return Preconditions.checkNotNullFromComponent(dependency.binding3());",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    @Override",
                    "    public Binding3 binding3() {",
                    "      return testComponentImpl.testComponentImplShard.binding3Provider.get();",
                    "    }")
                .addLines(
                    "    @Override",
                    "    public Provider<Binding1> providerBinding1() {",
                    "      return binding1Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding2> providerBinding2() {",
                    "      return binding2Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding3> providerBinding3() {",
                    "      return testComponentImpl.testComponentImplShard.binding3Provider;",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    private static final class SwitchingProvider<T> implements Provider<T> {",
                    "      @SuppressWarnings(\"unchecked\")",
                    "      @Override",
                    "      public T get() {",
                    "        switch (id) {",
                    "          case 0: return (T) new Binding1();",
                    "          case 1: return (T) new Binding2();",
                    "          default: throw new AssertionError(id);",
                    "        }",
                    "      }",
                    "    }")
                .addLines(
                    "  }",
                    "",
                    "  private static final class TestComponentImplShard {",
                    "    private final TestComponentImpl testComponentImpl;",
                    "    private Provider<Binding3> binding3Provider;",
                    "",
                    "    private TestComponentImplShard(",
                    "        TestComponentImpl testComponentImpl, Dependency dependencyParam) {",
                    "      this.testComponentImpl = testComponentImpl;",
                    "      initialize(dependencyParam);",
                    "    }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize(final Dependency dependencyParam) {",
                    "      this.binding3Provider =",
                    "          new TestComponentImpl.Binding3Provider(",
                    "              testComponentImpl.dependency);",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize(final Dependency dependencyParam) {",
                    "      this.binding3Provider = new SwitchingProvider<>(testComponentImpl, 0);",
                    "    }",
                    "",
                    "    private static final class SwitchingProvider<T> implements Provider<T> {",
                    "      @SuppressWarnings(\"unchecked\")",
                    "      @Override",
                    "      public T get() {",
                    "        switch (id) {",
                    "          case 0: return (T) Preconditions.checkNotNullFromComponent(",
                    "              testComponentImpl.dependency.binding3());",
                    "          default: throw new AssertionError(id);",
                    "        }",
                    "      }",
                    "    }")
                .build());
  }

  @Test
  public void testNewShardSubcomponentCreated() {
    ImmutableList.Builder<JavaFileObject> javaFileObjects = ImmutableList.builder();
    javaFileObjects.add(
        JavaFileObjects.forSourceLines(
            "dagger.internal.codegen.SubcomponentScope",
            "package dagger.internal.codegen;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "public @interface SubcomponentScope {}"),
        JavaFileObjects.forSourceLines(
            "dagger.internal.codegen.Binding1",
            "package dagger.internal.codegen;",
            "",
            "@SubcomponentScope",
            "final class Binding1 {",
            "  @javax.inject.Inject Binding1() {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "dagger.internal.codegen.Binding2",
            "package dagger.internal.codegen;",
            "",
            "@SubcomponentScope",
            "final class Binding2 {",
            "  @javax.inject.Inject Binding2() {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "dagger.internal.codegen.Binding3",
            "package dagger.internal.codegen;",
            "",
            "@SubcomponentScope",
            "final class Binding3 {",
            "  @javax.inject.Inject Binding3() {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "dagger.internal.codegen.TestComponent",
            "package dagger.internal.codegen;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  TestSubcomponent subcomponent();",
            "}"),
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = compiler().compile(javaFileObjects.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("dagger.internal.codegen.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("dagger.internal.codegen.DaggerTestComponent")
                .addLines(
                    "package dagger.internal.codegen;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent {",
                    "  private static final class TestSubcomponentImpl",
                    "      implements TestSubcomponent {",
                    "    private TestSubcomponentImplShard testSubcomponentImplShard;",
                    "    private final TestComponentImpl testComponentImpl;",
                    "    private final TestSubcomponentImpl testSubcomponentImpl = this;",
                    "    private Provider<Binding1> binding1Provider;",
                    "    private Provider<Binding2> binding2Provider;",
                    "",
                    "    private TestSubcomponentImpl(TestComponentImpl testComponentImpl) {",
                    "      this.testComponentImpl = testComponentImpl;",
                    "      initialize();",
                    "      testSubcomponentImplShard =",
                    "          new TestSubcomponentImplShard(",
                    "              testComponentImpl, testSubcomponentImpl);",
                    "    }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding1Provider =",
                    "          DoubleCheck.provider(Binding1_Factory.create());",
                    "      this.binding2Provider =",
                    "          DoubleCheck.provider(Binding2_Factory.create());",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding1Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding1>(",
                    "              testComponentImpl, testSubcomponentImpl, 0));",
                    "      this.binding2Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding2>(",
                    "              testComponentImpl, testSubcomponentImpl, 1));",
                    "    }")
                .addLines(
                    "    @Override",
                    "    public Binding1 binding1() {",
                    "      return binding1Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding2 binding2() {",
                    "      return binding2Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Binding3 binding3() {",
                    "      return testSubcomponentImpl.testSubcomponentImplShard",
                    "          .binding3Provider.get();",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding1> providerBinding1() {",
                    "      return binding1Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding2> providerBinding2() {",
                    "      return binding2Provider;",
                    "    }",
                    "",
                    "    @Override",
                    "    public Provider<Binding3> providerBinding3() {",
                    "      return testSubcomponentImpl.testSubcomponentImplShard.binding3Provider;",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    private static final class SwitchingProvider<T> implements Provider<T> {",
                    "      @SuppressWarnings(\"unchecked\")",
                    "      @Override",
                    "      public T get() {",
                    "        switch (id) {",
                    "          case 0: return (T) new Binding1();",
                    "          case 1: return (T) new Binding2();",
                    "          default: throw new AssertionError(id);",
                    "        }",
                    "      }",
                    "    }")
                .addLines(
                    "  }",
                    "",
                    "  private static final class TestSubcomponentImplShard {",
                    "    private final TestComponentImpl testComponentImpl;",
                    "    private final TestSubcomponentImpl testSubcomponentImpl;",
                    "    private Provider<Binding3> binding3Provider;",
                    "",
                    "    private TestSubcomponentImplShard(",
                    "        TestComponentImpl testComponentImpl,",
                    "        TestSubcomponentImpl testSubcomponentImpl) {",
                    "      this.testComponentImpl = testComponentImpl;",
                    "      this.testSubcomponentImpl = testSubcomponentImpl;",
                    "      initialize();",
                    "    }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding3Provider =",
                    "          DoubleCheck.provider(Binding3_Factory.create());",
                    "    }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "    @SuppressWarnings(\"unchecked\")",
                    "    private void initialize() {",
                    "      this.binding3Provider = DoubleCheck.provider(",
                    "          new SwitchingProvider<Binding3>(",
                    "              testComponentImpl, testSubcomponentImpl, 0));",
                    "    }",
                    "",
                    "    private static final class SwitchingProvider<T> implements Provider<T> {",
                    "      @SuppressWarnings(\"unchecked\")",
                    "      @Override",
                    "      public T get() {",
                    "        switch (id) {",
                    "          case 0: return (T) new Binding3();",
                    "          default: throw new AssertionError(id);",
                    "        }",
                    "      }",
                    "    }")
                .build());
  }

  private static JavaFileObject createBinding(String bindingName, String... deps) {
    return JavaFileObjects.forSourceLines(
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

  private Compiler compiler() {
    return compilerWithOptions(
        ImmutableSet.<String>builder()
            .add("-Adagger.generatedClassExtendsComponent=DISABLED")
            .add("-Adagger.keysPerComponentShard=" + BINDINGS_PER_SHARD)
            .addAll(compilerMode.javacopts())
            .build());
  }
}
