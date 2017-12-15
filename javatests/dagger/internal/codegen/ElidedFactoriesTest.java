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
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ElidedFactoriesTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ElidedFactoriesTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void simpleComponent() {
    JavaFileObject injectedType =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class InjectedType {",
            "  @Inject InjectedType() {}",
            "}");

    JavaFileObject dependsOnInjected =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class DependsOnInjected {",
            "  @Inject DependsOnInjected(InjectedType injected) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  DependsOnInjected dependsOnInjected();",
            "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerSimpleComponent",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerSimpleComponent implements SimpleComponent {",
            "  private DaggerSimpleComponent(Builder builder) {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static SimpleComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public DependsOnInjected dependsOnInjected() {",
            "    return new DependsOnInjected(new InjectedType());",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public SimpleComponent build() {",
            "      return new DaggerSimpleComponent(this);",
            "    }",
            "  }",
            "}");

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(injectedType, dependsOnInjected, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void simpleComponent_injectsProviderOf_dependsOnScoped() {
    JavaFileObject scopedType =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "final class ScopedType {",
            "  @Inject ScopedType() {}",
            "}");

    JavaFileObject dependsOnScoped =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class DependsOnScoped {",
            "  @Inject DependsOnScoped(ScopedType scoped) {}",
            "}");

    JavaFileObject needsProvider =
        JavaFileObjects.forSourceLines(
            "test.NeedsProvider",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class NeedsProvider {",
            "  @Inject NeedsProvider(Provider<DependsOnScoped> provider) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface SimpleComponent {",
            "  NeedsProvider needsProvider();",
            "}");
    JavaFileObject generatedComponent;
    switch (compilerMode) {
      case EXPERIMENTAL_ANDROID_MODE:
        generatedComponent =
            JavaFileObjects.forSourceLines(
                "test.DaggerSimpleComponent",
                "package test;",
                "",
                "import dagger.internal.MemoizedSentinel;",
                IMPORT_GENERATED_ANNOTATION,
                "import javax.inject.Provider;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerSimpleComponent implements SimpleComponent {",
                "  private volatile Object scopedType = new MemoizedSentinel();",
                "",
                "  private DaggerSimpleComponent(Builder builder) {}",
                "",
                "  public static Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  public static SimpleComponent create() {",
                "    return new Builder().build();",
                "  }",
                "",
                "  private ScopedType getScopedType() {",
                "    Object local = scopedType;",
                "    if (local instanceof MemoizedSentinel) {",
                "      synchronized (local) {",
                "        if (local == scopedType) {",
                "          scopedType = new ScopedType();",
                "        }",
                "        local = scopedType;",
                "      }",
                "    }",
                "    return (ScopedType) local;",
                "  }",
                "",
                "  private DependsOnScoped getDependsOnScoped() {",
                "    return new DependsOnScoped(getScopedType());",
                "  }",
                "",
                "  private Provider<DependsOnScoped> getDependsOnScopedProvider() {",
                "    return new Provider<DependsOnScoped>() {",
                "      @Override",
                "      public DependsOnScoped get() {",
                "        return getDependsOnScoped();",
                "      }",
                "    };",
                "  }",
                "",
                "  @Override",
                "  public NeedsProvider needsProvider() {",
                "    return new NeedsProvider(getDependsOnScopedProvider());",
                "  }",
                "",
                "  public static final class Builder {",
                "    private Builder() {}",
                "",
                "    public SimpleComponent build() {",
                "      return new DaggerSimpleComponent(this);",
                "    }",
                "  }",
                "}");
        break;
      default:
        generatedComponent =
            JavaFileObjects.forSourceLines(
                "test.DaggerSimpleComponent",
                "package test;",
                "",
                "import dagger.internal.DoubleCheck;",
                IMPORT_GENERATED_ANNOTATION,
                "import javax.inject.Provider;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerSimpleComponent implements SimpleComponent {",
                "  private Provider<ScopedType> scopedTypeProvider;",
                "  private DependsOnScoped_Factory dependsOnScopedProvider;",
                "  private DaggerSimpleComponent(Builder builder) {",
                "    initialize(builder);",
                "  }",
                "",
                "  public static Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  public static SimpleComponent create() {",
                "    return new Builder().build();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final Builder builder) {",
                "    this.scopedTypeProvider = DoubleCheck.provider(ScopedType_Factory.create());",
                "    this.dependsOnScopedProvider = ",
                "        DependsOnScoped_Factory.create(scopedTypeProvider);",
                "  }",
                "",
                "  @Override",
                "  public NeedsProvider needsProvider() {",
                "    return new NeedsProvider(dependsOnScopedProvider);",
                "  }",
                "",
                "  public static final class Builder {",
                "    private Builder() {",
                "    }",
                "",
                "    public SimpleComponent build() {",
                "      return new DaggerSimpleComponent(this);",
                "    }",
                "  }",
                "}");
    }
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(scopedType, dependsOnScoped, componentFile, needsProvider);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void scopedBinding_onlyUsedInSubcomponent() {
    JavaFileObject scopedType =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "final class ScopedType {",
            "  @Inject ScopedType() {}",
            "}");

    JavaFileObject dependsOnScoped =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class DependsOnScoped {",
            "  @Inject DependsOnScoped(ScopedType scoped) {}",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface SimpleComponent {",
            "  Sub sub();",
            "}");
    JavaFileObject subcomponentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Sub {",
            "  DependsOnScoped dependsOnScoped();",
            "}");

    JavaFileObject generatedComponent;
    switch (compilerMode) {
      case EXPERIMENTAL_ANDROID_MODE:
        generatedComponent =
            JavaFileObjects.forSourceLines(
                "test.DaggerSimpleComponent",
                "package test;",
                "",
                "import dagger.internal.MemoizedSentinel;",
                IMPORT_GENERATED_ANNOTATION,
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerSimpleComponent implements SimpleComponent {",
                "  private volatile Object scopedType = new MemoizedSentinel();",
                "",
                "  private DaggerSimpleComponent(Builder builder) {}",
                "",
                "  public static Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  public static SimpleComponent create() {",
                "    return new Builder().build();",
                "  }",
                "",
                "  private ScopedType getScopedType() {",
                "    Object local = scopedType;",
                "    if (local instanceof MemoizedSentinel) {",
                "      synchronized (local) {",
                "        if (local == scopedType) {",
                "          scopedType = new ScopedType();",
                "        }",
                "        local = scopedType;",
                "      }",
                "    }",
                "    return (ScopedType) local;",
                "  }",
                "",
                "  @Override",
                "  public Sub sub() {",
                "    return new SubImpl();",
                "  }",
                "",
                "  public static final class Builder {",
                "    private Builder() {}",
                "",
                "    public SimpleComponent build() {",
                "      return new DaggerSimpleComponent(this);",
                "    }",
                "  }",
                "",
                "  private final class SubImpl implements Sub {",
                "    private SubImpl() {}",
                "",
                "    @Override",
                "    public DependsOnScoped dependsOnScoped() {",
                "      return new DependsOnScoped(DaggerSimpleComponent.this.getScopedType());",
                "    }",
                "  }",
                "}");
        break;
      default:
        generatedComponent =
            JavaFileObjects.forSourceLines(
                "test.DaggerSimpleComponent",
                "package test;",
                "",
                "import dagger.internal.DoubleCheck;",
                IMPORT_GENERATED_ANNOTATION,
                "import javax.inject.Provider;",
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerSimpleComponent implements SimpleComponent {",
                "  private Provider<ScopedType> scopedTypeProvider;",
                "",
                "  private DaggerSimpleComponent(Builder builder) {",
                "    initialize(builder);",
                "  }",
                "",
                "  public static Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  public static SimpleComponent create() {",
                "    return new Builder().build();",
                "  }",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final Builder builder) {",
                "    this.scopedTypeProvider = DoubleCheck.provider(ScopedType_Factory.create());",
                "  }",
                "",
                "  @Override",
                "  public Sub sub() {",
                "    return new SubImpl();",
                "  }",
                "",
                "  public static final class Builder {",
                "    private Builder() {}",
                "",
                "    public SimpleComponent build() {",
                "      return new DaggerSimpleComponent(this);",
                "    }",
                "  }",
                "",
                "  private final class SubImpl implements Sub {",
                "    private SubImpl() {}",
                "",
                "    @Override",
                "    public DependsOnScoped dependsOnScoped() {",
                "      return new DependsOnScoped(",
                "          DaggerSimpleComponent.this.scopedTypeProvider.get());",
                "    }",
                "  }",
                "}");
    }
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(scopedType, dependsOnScoped, componentFile, subcomponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }
}
