/*
 * Copyright (C) 2018 The Dagger Authors.
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
import static dagger.internal.codegen.CompilerMode.AHEAD_OF_TIME_SUBCOMPONENTS_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.GENERATION_OPTIONS_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ObjectArrays;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AheadOfTimeSubcomponentsTest {
  private static final String PRUNED_METHOD_BODY =
      "throw new UnsupportedOperationException(\"This binding is not part of the final binding "
          + "graph. The key was requested by a binding that was believed to possibly be part of "
          + "the graph, but is no longer requested. If this exception is thrown, it is the result "
          + "of a Dagger bug.\");";

  @Test
  public void missingBindings_fromComponentMethod() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "MissingInLeaf");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  MissingInLeaf missingFromComponentMethod();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public abstract MissingInLeaf missingFromComponentMethod();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  static MissingInLeaf satisfiedInAncestor() { return new MissingInLeaf(); }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public final MissingInLeaf missingFromComponentMethod() {",
            "      return AncestorModule_SatisfiedInAncestorFactory.satisfiedInAncestor();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  @Test
  public void missingBindings_dependsOnBindingWithMatchingComponentMethod() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "MissingInLeaf");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  MissingInLeaf missingComponentMethod();",
            "  DependsOnComponentMethod dependsOnComponentMethod();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.DependsOnComponentMethod",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class DependsOnComponentMethod {",
            "  @Inject DependsOnComponentMethod(MissingInLeaf missingInLeaf) {}",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public abstract MissingInLeaf missingComponentMethod();",
            "",
            "  @Override",
            "  public DependsOnComponentMethod dependsOnComponentMethod() {",
            "    return new DependsOnComponentMethod(missingComponentMethod());",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);
  }

  @Test
  public void missingBindings_dependsOnMissingBinding() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "MissingInLeaf");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  DependsOnMissingBinding dependsOnMissingBinding();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.DependsOnMissingBinding",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class DependsOnMissingBinding {",
            "  @Inject DependsOnMissingBinding(MissingInLeaf missing) {}",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public DependsOnMissingBinding dependsOnMissingBinding() {",
            "    return new DependsOnMissingBinding((MissingInLeaf) getMissingInLeaf());",
            "  }",
            "",
            "  protected abstract Object getMissingInLeaf();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  static MissingInLeaf satisfiedInAncestor() { return new MissingInLeaf(); }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    protected final Object getMissingInLeaf() {",
            "      return AncestorModule_SatisfiedInAncestorFactory.satisfiedInAncestor();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  @Test
  public void missingBindings_satisfiedInGreatAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "MissingInLeaf");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  DependsOnMissingBinding dependsOnMissingBinding();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.DependsOnMissingBinding",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class DependsOnMissingBinding {",
            "  @Inject DependsOnMissingBinding(MissingInLeaf missing) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.GreatAncestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = SatisfiesMissingBindingModule.class)",
            "interface GreatAncestor {",
            "  Ancestor ancestor();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.SatisfiesMissingBindingModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class SatisfiesMissingBindingModule {",
            "  @Provides",
            "  static MissingInLeaf satisfy() { return new MissingInLeaf(); }",
            "}"));
    // DaggerLeaf+DaggerAncestor generated types are ignored - they're not the focus of this test
    // and are tested elsewhere
    JavaFileObject generatedGreatAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerGreatAncestor implements GreatAncestor {",
            "  protected DaggerGreatAncestor() {}",
            "",
            "  protected abstract class AncestorImpl extends DaggerAncestor {",
            "    protected AncestorImpl() {}",
            "",
            "    protected abstract class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      protected LeafImpl() {}",
            "",
            "      @Override",
            "      protected final Object getMissingInLeaf() {",
            "        return SatisfiesMissingBindingModule_SatisfyFactory.satisfy();",
            "      }",
            "    }",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGreatAncestor")
        .hasSourceEquivalentTo(generatedGreatAncestor);
  }

  @Test
  public void moduleInstanceDependency() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface Leaf {",
            "  String string();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class TestModule {",
            "  @Provides String provideString() { return \"florp\"; }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public String string() {",
            "    return TestModule_ProvideStringFactory.provideString(testModule());",
            "  }",
            "",
            "  protected abstract TestModule testModule();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Root {",
            "  Ancestor ancestor();",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Ancestor ancestor() {",
            "    return new AncestorImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  protected final class AncestorImpl extends DaggerAncestor {",
            "    private AncestorImpl() {}",
            "",
            "    @Override",
            "    public Leaf leaf() {",
            "      return new LeafImpl();",
            "    }",
            "",
            "    protected final class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      private final TestModule testModule;",
            "",
            "      private LeafImpl() {",
            "        this.testModule = new TestModule();",
            "      }",
            "",
            "      @Override",
            "      protected TestModule testModule() {",
            "        return testModule;",
            "      }",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void moduleInstanceDependency_withModuleParams() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface Leaf {",
            "  int getInt();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  private int i;",
            "",
            "  TestModule(int i) {}",
            "",
            "  @Provides int provideInt() {",
            "    return i++;",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public int getInt() {",
            "    return testModule().provideInt();",
            "  }",
            "",
            "  protected abstract TestModule testModule();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Ancestor {",
            "  Leaf leaf(TestModule module);",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Root {",
            "  Ancestor ancestor();",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Ancestor ancestor() {",
            "    return new AncestorImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  protected final class AncestorImpl extends DaggerAncestor {",
            "    private AncestorImpl() {}",
            "",
            "    @Override",
            "    public Leaf leaf(TestModule module) {",
            "      Preconditions.checkNotNull(module);",
            "      return new LeafImpl(module);",
            "    }",
            "",
            "    protected final class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      private final TestModule testModule;",
            "",
            "      private LeafImpl(TestModule module) {",
            "        this.testModule = module;",
            "      }",
            "",
            "      @Override",
            "      protected TestModule testModule() {",
            "        return testModule;",
            "      }",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void generatedInstanceBinding() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Leaf build();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Ancestor {",
            "  Leaf.Builder leaf();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  @Override",
            "  public abstract Leaf.Builder leaf();",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Root {",
            "  Ancestor ancestor();",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Ancestor ancestor() {",
            "    return new AncestorImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  protected final class AncestorImpl extends DaggerAncestor {",
            "    private AncestorImpl() {}",
            "",
            "    @Override",
            "    public Leaf.Builder leaf() {",
            "      return new LeafBuilder();",
            "    }",
            "",
            "    private final class LeafBuilder implements Leaf.Builder {",
            "      @Override",
            "      public Leaf build() {",
            "        return new LeafImpl();",
            "      }",
            "    }",
            "",
            "    protected final class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      private LeafImpl() {}",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void prunedGeneratedInstanceBinding() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.PrunedSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface PrunedSubcomponent {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    PrunedSubcomponent build();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.InstallsPrunedSubcomponentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(subcomponents = PrunedSubcomponent.class)",
            "interface InstallsPrunedSubcomponentModule {}"),
        JavaFileObjects.forSourceLines(
            "test.DependsOnPrunedSubcomponentBuilder",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class DependsOnPrunedSubcomponentBuilder {",
            "  @Inject DependsOnPrunedSubcomponentBuilder(PrunedSubcomponent.Builder builder) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.MaybeLeaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = InstallsPrunedSubcomponentModule.class)",
            "interface MaybeLeaf {",
            "  DependsOnPrunedSubcomponentBuilder dependsOnPrunedSubcomponentBuilder();",
            "}"));
    JavaFileObject generatedMaybeLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerMaybeLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerMaybeLeaf implements MaybeLeaf {",
            "  protected DaggerMaybeLeaf() {}",
            "",
            "  @Override",
            "  public DependsOnPrunedSubcomponentBuilder dependsOnPrunedSubcomponentBuilder() {",
            "    return new DependsOnPrunedSubcomponentBuilder(",
            "        (PrunedSubcomponent.Builder) getPrunedSubcomponentBuilder());",
            "  }",
            "",
            "  protected abstract Object getPrunedSubcomponentBuilder();",
            "",
            "  protected abstract class PrunedSubcomponentImpl extends DaggerPrunedSubcomponent {",
            "    protected PrunedSubcomponentImpl() {}",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerMaybeLeaf")
        .hasSourceEquivalentTo(generatedMaybeLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.PrunesGeneratedInstanceModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface PrunesGeneratedInstanceModule {",
            "  @Provides",
            "  static DependsOnPrunedSubcomponentBuilder pruneGeneratedInstance() {",
            "    return new DependsOnPrunedSubcomponentBuilder(null);",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = PrunesGeneratedInstanceModule.class)",
            "interface Root {",
            "  MaybeLeaf actuallyLeaf();",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public MaybeLeaf actuallyLeaf() {",
            "    return new MaybeLeafImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  protected final class MaybeLeafImpl extends DaggerMaybeLeaf {",
            "    private MaybeLeafImpl() {}",
            "",
            "    @Override",
            "    protected Object getPrunedSubcomponentBuilder() {",
            "      " + PRUNED_METHOD_BODY,
            "    }",
            "",
            "    @Override",
            "    public DependsOnPrunedSubcomponentBuilder dependsOnPrunedSubcomponentBuilder() {",
            "      return PrunesGeneratedInstanceModule_PruneGeneratedInstanceFactory",
            "          .pruneGeneratedInstance();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void optionalBindings_boundAndSatisfiedInSameSubcomponent() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "SatisfiedInSub");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Optional;",
            "",
            "@Subcomponent(modules = {SubModule.class, BindsSatisfiedInSubModule.class})",
            "interface Sub {",
            "  Optional<SatisfiedInSub> satisfiedInSub();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.SubModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class SubModule {",
            "  @BindsOptionalOf abstract SatisfiedInSub optionalSatisfiedInSub();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.BindsSatisfiedInSubModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class BindsSatisfiedInSubModule {",
            "  @Provides static SatisfiedInSub provideSatisfiedInSub() {",
            "      return new SatisfiedInSub();",
            "  }",
            "}"));
    JavaFileObject generatedSubcomponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerSub",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerSub implements Sub {",
            "  protected DaggerSub() {}",
            "",
            "  @Override",
            "  public Optional<SatisfiedInSub> satisfiedInSub() {",
            "    return Optional.of(",
            "        BindsSatisfiedInSubModule_ProvideSatisfiedInSubFactory",
            "            .provideSatisfiedInSub());",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSub")
        .hasSourceEquivalentTo(generatedSubcomponent);
  }

  @Test
  public void optionalBindings_satisfiedInAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "SatisfiedInAncestor");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Optional;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Optional<SatisfiedInAncestor> satisfiedInAncestor();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class LeafModule {",
            "  @BindsOptionalOf abstract SatisfiedInAncestor optionalSatisfiedInAncestor();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Optional<SatisfiedInAncestor> satisfiedInAncestor() {",
            "    return Optional.<SatisfiedInAncestor>empty();",
            "  }",
            "}");
    Compilation compilation =
        compile(
            filesToCompile.build()
            , CompilerMode.JAVA7
            );
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class AncestorModule {",
            "  @Provides",
            "  static SatisfiedInAncestor satisfiedInAncestor(){",
            "    return new SatisfiedInAncestor();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public final Optional<SatisfiedInAncestor> satisfiedInAncestor() {",
            "      return Optional.of(AncestorModule_SatisfiedInAncestorFactory",
            "          .satisfiedInAncestor());",
            "    }",
            "",
            "  }",
            "}");
    compilation =
        compile(
            filesToCompile.build()
            , CompilerMode.JAVA7
            );
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  @Test
  public void optionalBindings_satisfiedInGrandAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "SatisfiedInGrandAncestor");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Optional;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Optional<SatisfiedInGrandAncestor> satisfiedInGrandAncestor();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class LeafModule {",
            "  @BindsOptionalOf",
            "  abstract SatisfiedInGrandAncestor optionalSatisfiedInGrandAncestor();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Optional<SatisfiedInGrandAncestor> satisfiedInGrandAncestor() {",
            "    return Optional.<SatisfiedInGrandAncestor>empty();",
            "  }",
            "}");
    Compilation compilation =
        compile(
            filesToCompile.build()
            , CompilerMode.JAVA7
            );
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "  }",
            "}");
    compilation =
        compile(
            filesToCompile.build()
            , CompilerMode.JAVA7
            );
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatAncestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GreatAncestorModule.class)",
            "interface GreatAncestor {",
            "  Ancestor ancestor();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.GreatAncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class GreatAncestorModule {",
            "  @Provides",
            "  static SatisfiedInGrandAncestor satisfiedInGrandAncestor(){",
            "    return new SatisfiedInGrandAncestor();",
            "  }",
            "}"));
    JavaFileObject generatedGreatAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerGreatAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerGreatAncestor implements GreatAncestor {",
            "  protected DaggerGreatAncestor() {}",
            "",
            "  protected abstract class AncestorImpl extends DaggerAncestor {",
            "    protected AncestorImpl() {}",
            "",
            "    protected abstract class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      protected LeafImpl() {}",
            "",
            "      @Override",
            "      public final Optional<SatisfiedInGrandAncestor> satisfiedInGrandAncestor() {",
            "        return Optional.of(",
            "            GreatAncestorModule_SatisfiedInGrandAncestorFactory",
            "                .satisfiedInGrandAncestor());",
            "      }",
            "    }",
            "  }",
            "}");
    compilation =
        compile(
            filesToCompile.build()
            , CompilerMode.JAVA7
            );
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGreatAncestor")
        .hasSourceEquivalentTo(generatedGreatAncestor);
  }

  @Test
  public void optionalBindings_nonComponentMethodDependencySatisfiedInAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(
        filesToCompile, "SatisfiedInAncestor", "RequiresOptionalSatisfiedInAncestor");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Optional;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  RequiresOptionalSatisfiedInAncestor requiresOptionalSatisfiedInAncestor();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "abstract class LeafModule {",
            "  @Provides static RequiresOptionalSatisfiedInAncestor",
            "      provideRequiresOptionalSatisfiedInAncestor(",
            "          Optional<SatisfiedInAncestor> satisfiedInAncestor) {",
            "    return new RequiresOptionalSatisfiedInAncestor();",
            "  }",
            "",
            "  @BindsOptionalOf abstract SatisfiedInAncestor optionalSatisfiedInAncestor();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public RequiresOptionalSatisfiedInAncestor",
            "      requiresOptionalSatisfiedInAncestor() {",
            "    return LeafModule_ProvideRequiresOptionalSatisfiedInAncestorFactory",
            "        .provideRequiresOptionalSatisfiedInAncestor(",
            "            getOptionalOfSatisfiedInAncestor());",
            "  }",
            "",
            "  protected Optional getOptionalOfSatisfiedInAncestor() {",
            "    return Optional.<SatisfiedInAncestor>empty();",
            "  }",
            "}");
    Compilation compilation =
        compile(
            filesToCompile.build()
            , CompilerMode.JAVA7
            );
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class AncestorModule {",
            "  @Provides",
            "  static SatisfiedInAncestor satisfiedInAncestor(){",
            "    return new SatisfiedInAncestor();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    protected final Optional getOptionalOfSatisfiedInAncestor() {",
            "      return Optional.of(",
            "          AncestorModule_SatisfiedInAncestorFactory.satisfiedInAncestor());",
            "    }",
            "  }",
            "}");
    compilation =
        compile(
            filesToCompile.build()
            , CompilerMode.JAVA7
            );
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  @Test
  public void optionalBindings_boundInAncestorAndSatisfiedInGrandAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "SatisfiedInGrandAncestor");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Optional;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  Optional<SatisfiedInGrandAncestor> boundInAncestorSatisfiedInGrandAncestor();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public abstract Optional<SatisfiedInGrandAncestor>",
            "      boundInAncestorSatisfiedInGrandAncestor();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class AncestorModule {",
            "  @BindsOptionalOf",
            "  abstract SatisfiedInGrandAncestor optionalSatisfiedInGrandAncestor();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public Optional<SatisfiedInGrandAncestor>",
            "        boundInAncestorSatisfiedInGrandAncestor() {",
            "      return Optional.<SatisfiedInGrandAncestor>empty();",
            "    }",
            "  }",
            "}");
    compilation =
        compile(
            filesToCompile.build()
            , CompilerMode.JAVA7
            );
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GrandAncestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandAncestorModule.class)",
            "interface GrandAncestor {",
            "  Ancestor ancestor();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.GrandAncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class GrandAncestorModule {",
            "  @Provides static SatisfiedInGrandAncestor provideSatisfiedInGrandAncestor() {",
            "    return new SatisfiedInGrandAncestor();",
            "  }",
            "}"));
    JavaFileObject generatedGrandAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandAncestor implements GrandAncestor {",
            "  protected DaggerGrandAncestor() {}",
            "",
            "  protected abstract class AncestorImpl extends DaggerAncestor {",
            "    protected AncestorImpl() {}",
            "",
            "    protected abstract class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      protected LeafImpl() {}",
            "",
            "      @Override",
            "      public final Optional<SatisfiedInGrandAncestor>",
            "          boundInAncestorSatisfiedInGrandAncestor() {",
            "        return Optional.of(",
            "            GrandAncestorModule_ProvideSatisfiedInGrandAncestorFactory",
            "                .provideSatisfiedInGrandAncestor());",
            "      }",
            "    }",
            "  }",
            "}");
    compilation =
        compile(
            filesToCompile.build()
            , CompilerMode.JAVA7
            );
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGrandAncestor")
        .hasSourceEquivalentTo(generatedGrandAncestor);
  }

  @Test
  public void provisionOverInjection_providedInAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.ProvidedInAncestor",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class ProvidedInAncestor {",
            "  @Inject",
            "  ProvidedInAncestor(String string) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  ProvidedInAncestor injectedInLeaf();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public ProvidedInAncestor injectedInLeaf() {",
            "    return new ProvidedInAncestor(getString());",
            "  }",
            "",
            "  protected abstract String getString();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  static ProvidedInAncestor provideProvidedInAncestor() {",
            "    return new ProvidedInAncestor(\"static\");",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public final ProvidedInAncestor injectedInLeaf() {",
            "      return AncestorModule_ProvideProvidedInAncestorFactory",
            "          .provideProvidedInAncestor();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  @Test
  public void provisionOverInjection_providedInGrandAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.ProvidedInGrandAncestor",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class ProvidedInGrandAncestor {",
            "  @Inject",
            "  ProvidedInGrandAncestor(String string) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  ProvidedInGrandAncestor injectedInLeaf();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public ProvidedInGrandAncestor injectedInLeaf() {",
            "    return new ProvidedInGrandAncestor(getString());",
            "  }",
            "",
            "  protected abstract String getString();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GrandAncestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandAncestorModule.class)",
            "interface GrandAncestor {",
            "  Ancestor ancestor();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.GrandAncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class GrandAncestorModule {",
            "  @Provides",
            "  static ProvidedInGrandAncestor provideProvidedInGrandAncestor() {",
            "    return new ProvidedInGrandAncestor(\"static\");",
            "  }",
            "}"));
    JavaFileObject generatedGrandAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandAncestor implements GrandAncestor {",
            "  protected DaggerGrandAncestor() {}",
            "",
            "  protected abstract class AncestorImpl extends DaggerAncestor {",
            "    protected AncestorImpl() {}",
            "",
            "    protected abstract class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      protected LeafImpl() {}",
            "",
            "      @Override",
            "      public final ProvidedInGrandAncestor injectedInLeaf() {",
            "        return GrandAncestorModule_ProvideProvidedInGrandAncestorFactory",
            "            .provideProvidedInGrandAncestor();",
            "      }",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGrandAncestor")
        .hasSourceEquivalentTo(generatedGrandAncestor);
  }

  @Test
  public void provisionOverInjection_indirectDependency() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.ProvidedInAncestor",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class ProvidedInAncestor {",
            "  @Inject",
            "  ProvidedInAncestor(String string) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.InjectedInLeaf",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class InjectedInLeaf {",
            "  @Inject",
            "  InjectedInLeaf(ProvidedInAncestor providedInAncestor) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  InjectedInLeaf injectedInLeaf();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public InjectedInLeaf injectedInLeaf() {",
            "    return new InjectedInLeaf((ProvidedInAncestor) getProvidedInAncestor());",
            "  }",
            "",
            "  protected abstract String getString();",
            "",
            "  protected Object getProvidedInAncestor() {",
            "    return new ProvidedInAncestor(getString());",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  static ProvidedInAncestor provideProvidedInAncestor() {",
            "    return new ProvidedInAncestor(\"static\");",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    protected final Object getProvidedInAncestor() {",
            "      return AncestorModule_ProvideProvidedInAncestorFactory",
            "          .provideProvidedInAncestor();",
            "    }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  @Test
  public void provisionOverInjection_prunedIndirectDependency() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "PrunedDependency");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.InjectsPrunedDependency",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class InjectsPrunedDependency {",
            "  @Inject",
            "  InjectsPrunedDependency(PrunedDependency prunedDependency) {}",
            "",
            "  private InjectsPrunedDependency() { }",
            "",
            "  static InjectsPrunedDependency create() { return new InjectsPrunedDependency(); }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  InjectsPrunedDependency injectsPrunedDependency();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public InjectsPrunedDependency injectsPrunedDependency() {",
            "    return new InjectsPrunedDependency((PrunedDependency) getPrunedDependency());",
            "  }",
            "",
            "  protected abstract Object getPrunedDependency();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = RootModule.class)",
            "interface Root {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.RootModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class RootModule {",
            "  @Provides",
            "  static InjectsPrunedDependency injectsPrunedDependency() {",
            "    return InjectsPrunedDependency.create();",
            "  }",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Leaf leaf() {",
            "    return new LeafImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    @Deprecated",
            "    public Builder rootModule(RootModule rootModule) {",
            "      Preconditions.checkNotNull(rootModule);",
            "      return this;",
            "    }",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    private LeafImpl() {}",
            "",
            "    @Override",
            "    protected Object getPrunedDependency() {",
            "      " + PRUNED_METHOD_BODY,
            "    }",
            "",
            "    @Override",
            "    public InjectsPrunedDependency injectsPrunedDependency() {",
            "      return RootModule_InjectsPrunedDependencyFactory",
            "          .injectsPrunedDependency();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void provisionOverInjection_prunedDirectDependency_prunedInConcreteImplementation() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        // The binding for PrunedDependency will always exist, but will change from
        // ModifiableBindingType.INJECTION to ModifiableBindingType.MISSING. We should correctly
        // ignore this change leave the modifiable binding method alone
        JavaFileObjects.forSourceLines(
            "test.PrunedDependency",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class PrunedDependency {",
            "  @Inject PrunedDependency() {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.InjectsPrunedDependency",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class InjectsPrunedDependency {",
            "  @Inject",
            "  InjectsPrunedDependency(PrunedDependency prunedDependency) {}",
            "",
            "  private InjectsPrunedDependency() { }",
            "",
            "  static InjectsPrunedDependency create() { return new InjectsPrunedDependency(); }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  InjectsPrunedDependency injectsPrunedDependency();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public InjectsPrunedDependency injectsPrunedDependency() {",
            "    return new InjectsPrunedDependency((PrunedDependency) getPrunedDependency());",
            "  }",
            "",
            "  protected Object getPrunedDependency() {",
            "    return new PrunedDependency();",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = RootModule.class)",
            "interface Root {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.RootModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class RootModule {",
            "  @Provides",
            "  static InjectsPrunedDependency injectsPrunedDependency() {",
            "    return InjectsPrunedDependency.create();",
            "  }",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Leaf leaf() {",
            "    return new LeafImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    @Deprecated",
            "    public Builder rootModule(RootModule rootModule) {",
            "      Preconditions.checkNotNull(rootModule);",
            "      return this;",
            "    }",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    private LeafImpl() {}",
            "",
            "    @Override",
            "    public InjectsPrunedDependency injectsPrunedDependency() {",
            "      return RootModule_InjectsPrunedDependencyFactory",
            "          .injectsPrunedDependency();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void provisionOverInjection_prunedDirectDependency_prunedInAbstractImplementation() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        // The binding for PrunedDependency will always exist, but will change from
        // ModifiableBindingType.INJECTION to ModifiableBindingType.MISSING. We should correctly
        // ignore this change leave the modifiable binding method alone
        JavaFileObjects.forSourceLines(
            "test.PrunedDependency",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class PrunedDependency {",
            "  @Inject PrunedDependency() {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.InjectsPrunedDependency",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class InjectsPrunedDependency {",
            "  @Inject",
            "  InjectsPrunedDependency(PrunedDependency prunedDependency) {}",
            "",
            "  private InjectsPrunedDependency() { }",
            "",
            "  static InjectsPrunedDependency create() { return new InjectsPrunedDependency(); }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  InjectsPrunedDependency injectsPrunedDependency();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public InjectsPrunedDependency injectsPrunedDependency() {",
            "    return new InjectsPrunedDependency((PrunedDependency) getPrunedDependency());",
            "  }",
            "",
            "  protected Object getPrunedDependency() {",
            "    return new PrunedDependency();",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  static InjectsPrunedDependency injectsPrunedDependency() {",
            "    return InjectsPrunedDependency.create();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public final InjectsPrunedDependency injectsPrunedDependency() {",
            "      return AncestorModule_InjectsPrunedDependencyFactory",
            "          .injectsPrunedDependency();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Root {",
            "  Ancestor ancestor();",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Ancestor ancestor() {",
            "    return new AncestorImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  protected final class AncestorImpl extends DaggerAncestor {",
            "    private AncestorImpl() {}",
            "",
            "    @Override",
            "    public Leaf leaf() {",
            "      return new LeafImpl();",
            "    }",
            "",
            "    protected final class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      private LeafImpl() {}",
            // even though DaggerAncestor.LeafImpl.getPrunedDependency() was
            // ModifiableBindingType.MISSING, it doesn't need to be reimplemented because there was
            // a base implementation
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void productionSubcomponentAndModifiableFrameworkInstance() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "Response", "ResponseDependency");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProductionSubcomponent;",
            "import java.util.Set;",
            "",
            "@ProductionSubcomponent(modules = ResponseProducerModule.class)",
            "interface Leaf {",
            "  ListenableFuture<Set<Response>> responses();",
            "",
            "  @ProductionSubcomponent.Builder",
            "  interface Builder {",
            "    Leaf build();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.ResponseProducerModule",
            "package test;",
            "",
            "import dagger.multibindings.IntoSet;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "",
            "@ProducerModule",
            "final class ResponseProducerModule {",
            "  @Produces",
            "  @IntoSet",
            "  static Response response(ResponseDependency responseDependency) {",
            "    return new Response();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.internal.GenerationOptions;",
            "import dagger.producers.Producer;",
            "import dagger.producers.internal.CancellationListener;",
            "import dagger.producers.internal.Producers;",
            "import dagger.producers.internal.SetProducer;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.Set;",
            "import java.util.concurrent.Executor;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf, CancellationListener {",
            "  private Producer<Set<Response>> responsesEntryPoint;",
            "  private Producer<Response> responseProducer;",
            "  private Producer<Set<Response>> setOfResponseProducer;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization() {",
            "    initialize();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.responseProducer =",
            "        ResponseProducerModule_ResponseFactory.create(",
            "            getProductionImplementationExecutorProvider(),",
            "            getProductionComponentMonitorProvider(),",
            "            getResponseDependencyProducer());",
            "    this.setOfResponseProducer =",
            "        SetProducer.<Response>builder(1, 0)",
            "            .addProducer(responseProducer).build();",
            "    this.responsesEntryPoint =",
            "        Producers.entryPointViewOf(getSetOfResponseProducer(), this);",
            "  }",
            "",
            "  @Override",
            "  public ListenableFuture<Set<Response>> responses() {",
            "    return responsesEntryPoint.get();",
            "  }",
            "",
            "  protected abstract Provider<Executor>",
            "      getProductionImplementationExecutorProvider();",
            "",
            "  protected abstract Provider<ProductionComponentMonitor>",
            "      getProductionComponentMonitorProvider();",
            "",
            "  protected abstract Producer getResponseDependencyProducer();",
            "",
            "  protected Producer getSetOfResponseProducer() {",
            "    return setOfResponseProducer;",
            "  }",
            "",
            "  @Override",
            "  public void onProducerFutureCancelled(boolean mayInterruptIfRunning) {",
            "    Producers.cancel(getSetOfResponseProducer(), mayInterruptIfRunning);",
            "    Producers.cancel(responseProducer, mayInterruptIfRunning);",
            "  }",
            "}");

    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.ExecutorModule",
            "package test;",
            "",
            "import com.google.common.util.concurrent.MoreExecutors;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.producers.Production;",
            "import java.util.concurrent.Executor;",
            "",
            "@Module",
            "final class ExecutorModule {",
            "  @Provides",
            "  @Production",
            "  static Executor executor() {",
            "    return MoreExecutors.directExecutor();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProductionComponent;",
            "",
            "@ProductionComponent(",
            "  modules = {",
            "      ExecutorModule.class,",
            "      ResponseDependencyProducerModule.class,",
            "      RootMultibindingModule.class,",
            "  })",
            "interface Root {",
            "  Leaf.Builder leaf();",
            "",
            "  @ProductionComponent.Builder",
            "  interface Builder {",
            "    Root build();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.ResponseDependencyProducerModule",
            "package test;",
            "",
            "import com.google.common.util.concurrent.Futures;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "",
            "@ProducerModule",
            "final class ResponseDependencyProducerModule {",
            "  @Produces",
            "  static ListenableFuture<ResponseDependency> responseDependency() {",
            "    return Futures.immediateFuture(new ResponseDependency());",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.RootMultibindingModule",
            "package test;",
            "",
            "import dagger.multibindings.IntoSet;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "",
            "@ProducerModule",
            "final class RootMultibindingModule {",
            "  @Produces",
            "  @IntoSet",
            "  static Response response() {",
            "    return new Response();",
            "  }",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            "import dagger.internal.DoubleCheck;",
            "import dagger.internal.InstanceFactory;",
            "import dagger.internal.SetFactory;",
            "import dagger.producers.Producer;",
            "import dagger.producers.internal.CancellationListener;",
            "import dagger.producers.internal.DelegateProducer;",
            "import dagger.producers.internal.Producers;",
            "import dagger.producers.internal.SetProducer;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.Set;",
            "import java.util.concurrent.Executor;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root, CancellationListener {",
            "  private Provider<Executor> productionImplementationExecutorProvider;",
            "  private Provider<Root> rootProvider;",
            "  private Provider<ProductionComponentMonitor> monitorProvider;",
            "  private Producer<ResponseDependency> responseDependencyProducer;",
            "  private Producer<Response> responseProducer;",
            "",
            "  private DaggerRoot() {",
            "    initialize();",
            "  }",
            "",
            "  public static Root.Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.productionImplementationExecutorProvider =",
            "        DoubleCheck.provider((Provider) ExecutorModule_ExecutorFactory.create());",
            "    this.rootProvider = InstanceFactory.create((Root) this);",
            "    this.monitorProvider =",
            "        DoubleCheck.provider(",
            "            Root_MonitoringModule_MonitorFactory.create(",
            "                rootProvider,",
            "                SetFactory.<ProductionComponentMonitor.Factory>empty()));",
            "    this.responseDependencyProducer =",
            "        ResponseDependencyProducerModule_ResponseDependencyFactory.create(",
            "            productionImplementationExecutorProvider, monitorProvider);",
            "    this.responseProducer =",
            "        RootMultibindingModule_ResponseFactory.create(",
            "            productionImplementationExecutorProvider, monitorProvider);",
            "  }",
            "",
            "  @Override",
            "  public Leaf.Builder leaf() {",
            "    return new LeafBuilder();",
            "  }",
            "",
            "  @Override",
            "  public void onProducerFutureCancelled(boolean mayInterruptIfRunning) {",
            "    Producers.cancel(responseProducer, mayInterruptIfRunning);",
            "    Producers.cancel(responseDependencyProducer, mayInterruptIfRunning);",
            "  }",
            "",
            "  private static final class Builder implements Root.Builder {",
            "    @Override",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  private final class LeafBuilder implements Leaf.Builder {",
            "    @Override",
            "    public Leaf build() {",
            "      return new LeafImpl();",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf implements CancellationListener {",
            "    private Producer<Set<Response>> setOfResponseProducer = new DelegateProducer<>();",
            "",
            "    private LeafImpl() {",
            "      configureInitialization();",
            "      initialize();",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize() {",
            "      DelegateProducer.setDelegate(",
            "          setOfResponseProducer,",
            "          SetProducer.<Response>builder(1, 1)",
            "              .addCollectionProducer(super.getSetOfResponseProducer())",
            "              .addProducer(DaggerRoot.this.responseProducer)",
            "              .build());",
            "    }",
            "",
            "    @Override",
            "    protected Provider<Executor> getProductionImplementationExecutorProvider() {",
            "      return DaggerRoot.this.productionImplementationExecutorProvider;",
            "    }",
            "",
            "    @Override",
            "    protected Provider<ProductionComponentMonitor>",
            "        getProductionComponentMonitorProvider() {",
            "      return DaggerRoot.this.monitorProvider;",
            "    }",
            "",
            "    @Override",
            "    protected Producer getResponseDependencyProducer() {",
            "      return DaggerRoot.this.responseDependencyProducer;",
            "    }",
            "",
            "    @Override",
            "    protected Producer getSetOfResponseProducer() {",
            "      return setOfResponseProducer;",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void lazyOfModifiableBinding() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "MissingInLeaf");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import dagger.Subcomponent;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  Lazy<MissingInLeaf> lazy();",
            "  Provider<Lazy<MissingInLeaf>> providerOfLazy();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import dagger.internal.DoubleCheck;",
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.ProviderOfLazy;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Lazy<MissingInLeaf> lazy() {",
            "    return DoubleCheck.lazy(getMissingInLeafProvider());",
            "  }",
            "",
            "  @Override",
            "  public Provider<Lazy<MissingInLeaf>> providerOfLazy() {",
            "    return ProviderOfLazy.create(getMissingInLeafProvider());",
            "  }",
            "",
            "  protected abstract Provider getMissingInLeafProvider();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  static MissingInLeaf satisfiedInAncestor() { return new MissingInLeaf(); }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    protected final Provider getMissingInLeafProvider() {",
            "      return AncestorModule_SatisfiedInAncestorFactory.create();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  @Test
  public void missingBindingAccessInLeafAndAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(
        filesToCompile, "Missing", "DependsOnMissing", "ProvidedInAncestor_InducesSetBinding");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.Provides;",
            "import javax.inject.Provider;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  static DependsOnMissing test(",
            "      Missing missing,",
            "      Provider<Missing> missingProvider,",
            "      ProvidedInAncestor_InducesSetBinding missingInLeaf) {",
            "    return new DependsOnMissing();",
            "  }",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static Object unresolvedSetBinding(",
            "      Missing missing, Provider<Missing> missingProvider) {",
            "    return new Object();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  DependsOnMissing instance();",
            "  Provider<DependsOnMissing> frameworkInstance();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private Provider<DependsOnMissing> testProvider;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization() {",
            "    initialize();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.testProvider =",
            "        LeafModule_TestFactory.create(",
            "            getMissingProvider(), getProvidedInAncestor_InducesSetBindingProvider());",
            "  }",
            "",
            "  @Override",
            "  public DependsOnMissing instance() {",
            "    return LeafModule_TestFactory.test(",
            // TODO(b/117833324): remove these unnecessary casts
            "        (Missing) getMissing(),",
            "        getMissingProvider(),",
            "        (ProvidedInAncestor_InducesSetBinding)",
            "            getProvidedInAncestor_InducesSetBinding());",
            "  }",
            "",
            "  @Override",
            "  public Provider<DependsOnMissing> frameworkInstance() {",
            "    return testProvider;",
            "  }",
            "",
            "  protected abstract Object getMissing();",
            "  protected abstract Provider getMissingProvider();",
            "  protected abstract Object getProvidedInAncestor_InducesSetBinding();",
            "  protected abstract Provider getProvidedInAncestor_InducesSetBindingProvider();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "",
            "@Module",
            "interface AncestorModule {",
            "  @Provides",
            "  static ProvidedInAncestor_InducesSetBinding providedInAncestor(",
            "      Set<Object> setThatInducesMissingBindingInChildSubclassImplementation) {",
            "    return new ProvidedInAncestor_InducesSetBinding();",
            "  }",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static Object setContribution() {",
            "    return new Object();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import dagger.internal.DelegateFactory;",
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.SetFactory;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    private Provider<Object> unresolvedSetBindingProvider;",
            "    private Provider<Set<Object>> setOfObjectProvider;",
            "    private Provider<ProvidedInAncestor_InducesSetBinding> ",
            "        providedInAncestorProvider = ",
            "            new DelegateFactory<>();",
            "",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    protected void configureInitialization() {",
            "      super.configureInitialization();",
            "      initialize();",
            "    }",
            "",
            "    private Object getUnresolvedSetBinding() {",
            "      return LeafModule_UnresolvedSetBindingFactory.unresolvedSetBinding(",
            // TODO(b/117833324): remove this unnecessary cast
            "          (Missing) getMissing(), getMissingProvider());",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize() {",
            "      this.unresolvedSetBindingProvider =",
            "          LeafModule_UnresolvedSetBindingFactory.create(getMissingProvider());",
            "      this.setOfObjectProvider =",
            "          SetFactory.<Object>builder(2, 0)",
            "              .addProvider(AncestorModule_SetContributionFactory.create())",
            "              .addProvider(unresolvedSetBindingProvider)",
            "              .build();",
            "      DelegateFactory.setDelegate(",
            "          providedInAncestorProvider,",
            "          AncestorModule_ProvidedInAncestorFactory.create(getSetOfObjectProvider()));",
            "    }",
            "",
            "    protected Set<Object> getSetOfObject() {",
            "      return ImmutableSet.<Object>of(",
            "          AncestorModule_SetContributionFactory.setContribution(),",
            "          getUnresolvedSetBinding());",
            "    }",
            "",
            "    @Override",
            "    protected final Object getProvidedInAncestor_InducesSetBinding() {",
            "      return AncestorModule_ProvidedInAncestorFactory.providedInAncestor(",
            "          getSetOfObject());",
            "    }",
            "",
            "    protected Provider<Set<Object>> getSetOfObjectProvider() {",
            "      return setOfObjectProvider;",
            "    }",
            "",
            "    @Override",
            "    protected final Provider getProvidedInAncestor_InducesSetBindingProvider() {",
            "      return providedInAncestorProvider;",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  @Test
  public void subcomponentBuilders() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "InducesDependenciesOnBuilderFields");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class LeafModule {",
            "  private final Object object;",
            "",
            "  LeafModule(Object object) {",
            "    this.object = object;",
            "  }",
            "",
            "  @Provides",
            "  Object fromModule() {",
            "    return object;",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.MultibindingsModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "interface MultibindingsModule {",
            "  @Binds",
            "  @IntoSet",
            "  String string(String string);",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Subcomponent;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent(modules = {LeafModule.class, MultibindingsModule.class})",
            "interface Leaf {",
            "  int bindsInstance();",
            "  Object fromModule();",
            "  InducesDependenciesOnBuilderFields inducesDependenciesOnBuilderFields();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder bindsInstance(int boundInstance);",
            "    @BindsInstance Builder inducedInSubclass(String induced);",
            "    Builder module(LeafModule module);",
            "",
            "    Leaf build();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private Integer bindsInstance;",
            "  private LeafModule leafModule;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization(",
            "      LeafModule leafModuleParam, Integer bindsInstanceParam) {",
            "    this.bindsInstance = bindsInstanceParam;",
            "    this.leafModule = leafModuleParam;",
            "  }",
            "",
            "  @Override",
            "  public int bindsInstance() {",
            "    return bindsInstance;",
            "  }",
            "",
            "  @Override",
            "  public Object fromModule() {",
            "    return LeafModule_FromModuleFactory.fromModule(leafModule());",
            "  }",
            "",
            "  @Override",
            "  public abstract InducesDependenciesOnBuilderFields",
            "      inducesDependenciesOnBuilderFields();",
            "",
            "  protected LeafModule leafModule() {",
            "    return leafModule;",
            "  }",
            "",
            "  public abstract static class Builder implements Leaf.Builder {",
            "    protected Integer bindsInstance;",
            "    protected String inducedInSubclass;",
            "    protected LeafModule leafModule;",
            "",
            "    @Override",
            "    public Builder bindsInstance(int boundInstance) {",
            "      this.bindsInstance = Preconditions.checkNotNull(boundInstance);",
            "      return this;",
            "    }",
            "",
            "    @Override",
            "    public Builder inducedInSubclass(String induced) {",
            "      this.inducedInSubclass = Preconditions.checkNotNull(induced);",
            "      return this;",
            "    }",
            "",
            "    @Override",
            "    public Builder module(LeafModule module) {",
            "      this.leafModule = Preconditions.checkNotNull(module);",
            "      return this;",
            "    }",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = MultibindingInducingModule.class)",
            "interface Ancestor {",
            "  Leaf.Builder leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.MultibindingInducingModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.Multibinds;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "",
            "@Module",
            "interface MultibindingInducingModule {",
            "  @Provides",
            "  static InducesDependenciesOnBuilderFields induce(",
            "      Set<String> multibindingWithBuilderFieldDeps) { ",
            "    return new InducesDependenciesOnBuilderFields();",
            "  }",
            "",
            "  @Multibinds",
            "  Set<String> multibinding();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  @Override",
            "  public abstract Leaf.Builder leaf();",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    private String inducedInSubclass;",
            "",
            "    protected LeafImpl() {}",
            "",
            "    protected void configureInitialization(",
            "        LeafModule leafModule,",
            "        Integer bindsInstance,",
            "        String inducedInSubclassParam) {",
            "      this.inducedInSubclass = inducedInSubclassParam;",
            "      configureInitialization(leafModule, bindsInstance);",
            "    }",
            "",
            "    protected Set<String> getSetOfString() {",
            "      return ImmutableSet.<String>of(inducedInSubclass);",
            "    }",
            "",
            "    @Override",
            "    public final InducesDependenciesOnBuilderFields",
            "        inducesDependenciesOnBuilderFields() {",
            "      return MultibindingInducingModule_InduceFactory.induce(getSetOfString());",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Root {",
            "  Ancestor ancestor();",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Ancestor ancestor() {",
            "    return new AncestorImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  protected final class AncestorImpl extends DaggerAncestor {",
            "    private AncestorImpl() {}",
            "",
            "    @Override",
            "    public Leaf.Builder leaf() {",
            "      return new LeafBuilder();",
            "    }",
            "",
            "    private final class LeafBuilder extends DaggerLeaf.Builder {",
            "      @Override",
            "      public Leaf build() {",
            // TODO(b/117833324): Can we stick the validations into a method on the base class
            // builder so that the contents of this method are just call to that and then new
            // FooImpl? But repeated modules may make this more complicated, since those *should*
            // be null
            "        Preconditions.checkBuilderRequirement(bindsInstance, Integer.class);",
            "        Preconditions.checkBuilderRequirement(inducedInSubclass, String.class);",
            "        Preconditions.checkBuilderRequirement(leafModule, LeafModule.class);",
            "        return new LeafImpl(leafModule, bindsInstance, inducedInSubclass);",
            "      }",
            "    }",
            "",
            "    protected final class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      private final LeafModule leafModule;",
            "",
            "      private LeafImpl(",
            "          LeafModule leafModuleParam,",
            "          Integer bindsInstance,",
            "          String inducedInSubclass) {",
            "        this.leafModule = leafModuleParam;",
            "        configureInitialization(leafModuleParam, bindsInstance, inducedInSubclass);",
            "      }",
            "",
            "      @Override",
            "      protected LeafModule leafModule() {",
            "        return leafModule;",
            "      }",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void subcomponentBuilders_moduleWithUnusedInstanceBindings() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "Used", "Unused");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.ModuleWithUsedBinding",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ModuleWithUsedBinding {",
            "  @Provides",
            "  Used used() {",
            "    return new Used();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.ModuleWithUnusedBinding",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ModuleWithUnusedBinding {",
            "  @Provides",
            "  Unused unused() {",
            "    return new Unused();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = {ModuleWithUsedBinding.class, ModuleWithUnusedBinding.class})",
            "interface Leaf {",
            "  Used used();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Builder setUsed(ModuleWithUsedBinding module);",
            "    Builder setUnused(ModuleWithUnusedBinding module);",
            "    Leaf build();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private ModuleWithUsedBinding moduleWithUsedBinding;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization(",
            "      ModuleWithUsedBinding moduleWithUsedBindingParam) {",
            "    this.moduleWithUsedBinding = moduleWithUsedBindingParam;",
            "  }",
            "",
            "  @Override",
            "  public Used used() {",
            "    return ModuleWithUsedBinding_UsedFactory.used(",
            "        moduleWithUsedBinding());",
            "  }",
            "",
            "  protected ModuleWithUsedBinding moduleWithUsedBinding() {",
            "    return moduleWithUsedBinding;",
            "  }",
            "",
            "  public abstract static class Builder implements Leaf.Builder {",
            "    protected ModuleWithUsedBinding moduleWithUsedBinding;",
            "    protected ModuleWithUnusedBinding moduleWithUnusedBinding;",
            "",
            "    @Override",
            "    public Builder setUsed(ModuleWithUsedBinding module) {",
            "      this.moduleWithUsedBinding = Preconditions.checkNotNull(module);",
            "      return this;",
            "    }",
            "",
            "    @Override",
            "    public Builder setUnused(ModuleWithUnusedBinding module) {",
            "      this.moduleWithUnusedBinding = Preconditions.checkNotNull(module);",
            "      return this;",
            "    }",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Root {",
            "  Leaf.Builder leaf();",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Leaf.Builder leaf() {",
            "    return new LeafBuilder();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  private final class LeafBuilder extends DaggerLeaf.Builder {",
            "    @Override",
            "    public Leaf build() {",
            "      if (moduleWithUsedBinding == null) {",
            "        this.moduleWithUsedBinding = new ModuleWithUsedBinding();",
            "      }",
            // ModuleWithUnusedBinding is not verified since it's not used
            "      return new LeafImpl(moduleWithUsedBinding);",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    private final ModuleWithUsedBinding moduleWithUsedBinding;",
            "",
            "    private LeafImpl(ModuleWithUsedBinding moduleWithUsedBindingParam) {",
            "      this.moduleWithUsedBinding = moduleWithUsedBindingParam;",
            "      configureInitialization(moduleWithUsedBindingParam);",
            "    }",
            "",
            "    @Override",
            "    protected ModuleWithUsedBinding moduleWithUsedBinding() {",
            "      return moduleWithUsedBinding;",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void subcomponentBuilders_repeatedModule() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.RepeatedModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class RepeatedModule {",
            "  @Provides",
            "  int i() {",
            "    return 1;",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = RepeatedModule.class)",
            "interface Leaf {",
            "  int i();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Builder repeatedModule(RepeatedModule repeatedModule);",
            "    Leaf build();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private RepeatedModule repeatedModule;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization(RepeatedModule repeatedModuleParam) {",
            "    this.repeatedModule = repeatedModuleParam;",
            "  }",
            "",
            "  @Override",
            "  public int i() {",
            "    return repeatedModule().i();",
            "  }",
            "",
            "  protected RepeatedModule repeatedModule() {",
            "    return repeatedModule;",
            "  }",
            "",
            "  public abstract static class Builder implements Leaf.Builder {",
            "    protected RepeatedModule repeatedModule;",
            "",
            "    @Override",
            "    public Builder repeatedModule(RepeatedModule repeatedModule) {",
            "      this.repeatedModule = Preconditions.checkNotNull(repeatedModule);",
            "      return this;",
            "    }",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = RepeatedModule.class)",
            "interface Root {",
            "  Leaf.Builder leaf();",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private final RepeatedModule repeatedModule;",
            "",
            "  private DaggerRoot(RepeatedModule repeatedModuleParam) {",
            "    this.repeatedModule = repeatedModuleParam;",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Leaf.Builder leaf() {",
            "    return new LeafBuilder();",
            "  }",
            "",
            "  static final class Builder {",
            "    private RepeatedModule repeatedModule;",
            "",
            "    private Builder() {}",
            "",
            "    public Builder repeatedModule(RepeatedModule repeatedModule) {",
            "      this.repeatedModule = Preconditions.checkNotNull(repeatedModule);",
            "      return this;",
            "    }",
            "",
            "    public Root build() {",
            "      if (repeatedModule == null) {",
            "        this.repeatedModule = new RepeatedModule();",
            "      }",
            "      return new DaggerRoot(repeatedModule);",
            "    }",
            "  }",
            "",
            "  private final class LeafBuilder extends DaggerLeaf.Builder {",
            "    @Override",
            "    public LeafBuilder repeatedModule(RepeatedModule repeatedModule) {",
            "      throw new UnsupportedOperationException(",
            "        String.format(",
            "          \"%s cannot be set because it is inherited from the enclosing component\",",
            "          RepeatedModule.class.getCanonicalName()));",
            "    }",
            "",
            "    @Override",
            "    public Leaf build() {",
            "      return new LeafImpl();",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    private LeafImpl() {",
            "      configureInitialization(null);",
            "    }",
            "",
            "    @Override",
            "    protected RepeatedModule repeatedModule() {",
            "      return DaggerRoot.this.repeatedModule;",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void bindsWithMissingDependency() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "MissingInLeaf");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Binds;",
            "",
            "@Module",
            "interface LeafModule {",
            "  @Binds Object missingBindsDependency(MissingInLeaf missing);",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Object bindsWithMissingDependencyInLeaf();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public abstract Object bindsWithMissingDependencyInLeaf();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.MissingInLeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface MissingInLeafModule {",
            "  @Provides",
            "  static MissingInLeaf boundInRoot() {",
            "    return new MissingInLeaf();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = MissingInLeafModule.class)",
            "interface Root {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Leaf leaf() {",
            "    return new LeafImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    private LeafImpl() {}",
            "",
            "    @Override",
            "    public Object bindsWithMissingDependencyInLeaf() {",
            "      return MissingInLeafModule_BoundInRootFactory.boundInRoot();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void bindsWithMissingDependency_pruned() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "MissingInLeaf");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Binds;",
            "",
            "@Module",
            "interface LeafModule {",
            "  @Binds Object missingBindsDependency(MissingInLeaf missing);",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.DependsOnBindsWithMissingDep",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class DependsOnBindsWithMissingDep {",
            "  @Inject DependsOnBindsWithMissingDep(Object bindsWithMissingDep) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  DependsOnBindsWithMissingDep DependsOnBindsWithMissingDep();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public DependsOnBindsWithMissingDep DependsOnBindsWithMissingDep() {",
            "    return new DependsOnBindsWithMissingDep(getObject());",
            "  }",
            "",
            "  protected abstract Object getObject();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.PrunesInjectConstructorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface PrunesInjectConstructorModule {",
            "  @Provides",
            "  static DependsOnBindsWithMissingDep pruneInjectConstructor() {",
            "    return new DependsOnBindsWithMissingDep(new Object());",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = PrunesInjectConstructorModule.class)",
            "interface Root {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Leaf leaf() {",
            "    return new LeafImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    private LeafImpl() {}",
            "",
            "    @Override",
            "    protected Object getObject() {",
            "      " + PRUNED_METHOD_BODY,
            "    }",
            "",
            "    @Override",
            "    public DependsOnBindsWithMissingDep DependsOnBindsWithMissingDep() {",
            "      return PrunesInjectConstructorModule_PruneInjectConstructorFactory",
            "          .pruneInjectConstructor();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void modifiedProducerFromProvider() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "DependsOnModifiedProducerFromProvider");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.multibindings.IntoSet;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "",
            "@ProducerModule",
            "interface LeafModule {",
            "  @Produces",
            "  static DependsOnModifiedProducerFromProvider dependsOnModified(Set<String> set) {",
            "    return new DependsOnModifiedProducerFromProvider();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.producers.Producer;",
            "import dagger.producers.ProductionSubcomponent;",
            "import java.util.Set;",
            "",
            "@ProductionSubcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Producer<DependsOnModifiedProducerFromProvider>",
            "      dependsOnModifiedProducerFromProvider();",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import dagger.producers.Producer;",
            "import dagger.producers.internal.CancellationListener;",
            "import dagger.producers.internal.Producers;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.Set;",
            "import java.util.concurrent.Executor;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf, CancellationListener {",
            "  private Producer<DependsOnModifiedProducerFromProvider>",
            "      dependsOnModifiedProducerFromProviderEntryPoint;",
            "  private Producer<DependsOnModifiedProducerFromProvider> dependsOnModifiedProducer;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization() {",
            "    initialize();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.dependsOnModifiedProducer =",
            "        LeafModule_DependsOnModifiedFactory.create(",
            "            getProductionImplementationExecutorProvider(),",
            "            getProductionComponentMonitorProvider(),",
            "            getSetOfStringProducer());",
            "    this.dependsOnModifiedProducerFromProviderEntryPoint =",
            "        Producers.entryPointViewOf(dependsOnModifiedProducer, this);",
            "  }",
            "",
            "  @Override",
            "  public Producer<DependsOnModifiedProducerFromProvider> ",
            "      dependsOnModifiedProducerFromProvider() {",
            "    return dependsOnModifiedProducerFromProviderEntryPoint;",
            "  }",
            "",
            "  protected abstract Provider<Executor> ",
            "      getProductionImplementationExecutorProvider();",
            "",
            "  protected abstract Provider<ProductionComponentMonitor>",
            "      getProductionComponentMonitorProvider();",
            "",
            "  protected abstract Producer<Set<String>> getSetOfStringProducer();",
            "",
            "  @Override",
            "  public void onProducerFutureCancelled(boolean mayInterruptIfRunning) {",
            "    Producers.cancel(dependsOnModifiedProducer, mayInterruptIfRunning);",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.RootModule",
            "package test;",
            "",
            "import dagger.multibindings.IntoSet;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.producers.Production;",
            "import java.util.Set;",
            "import java.util.concurrent.Executor;",
            "",
            "@Module",
            "interface RootModule {",
            "  @Provides",
            "  @IntoSet",
            "  static String induceModificationInLeaf() {",
            "    return new String();",
            "  }",
            "",
            "  @Provides",
            "  @Production",
            "  static Executor productionExecutor() {",
            "    return null;",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = RootModule.class)",
            "interface Root {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            "import dagger.internal.DelegateFactory;",
            "import dagger.internal.DoubleCheck;",
            "import dagger.internal.InstanceFactory;",
            "import dagger.internal.SetFactory;",
            "import dagger.producers.Producer;",
            "import dagger.producers.internal.CancellationListener;",
            "import dagger.producers.internal.DelegateProducer;",
            "import dagger.producers.internal.Producers;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.Set;",
            "import java.util.concurrent.Executor;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private DaggerRoot() {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Root create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public Leaf leaf() {",
            "    return new LeafImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot();",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf implements CancellationListener {",
            "    private Provider<Executor> productionImplementationExecutorProvider =",
            "        new DelegateFactory<>();",
            "    private Provider<Leaf> leafProvider;",
            "    private Provider<ProductionComponentMonitor> monitorProvider =",
            "        new DelegateFactory<>();",
            "    private Provider<Set<String>> setOfStringProvider;",
            "    private Producer<Set<String>> setOfStringProducer = new DelegateProducer<>();",
            "",
            "    private LeafImpl() {",
            "      configureInitialization();",
            "      initialize();",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize() {",
            "      DelegateFactory.setDelegate(",
            "          productionImplementationExecutorProvider,",
            "          DoubleCheck.provider(",
            "              (Provider) RootModule_ProductionExecutorFactory.create()));",
            "      this.leafProvider = InstanceFactory.create((Leaf) this);",
            "      DelegateFactory.setDelegate(",
            "          monitorProvider,",
            "          DoubleCheck.provider(",
            "              Leaf_MonitoringModule_MonitorFactory.create(",
            "                  leafProvider,",
            "                  getSetOfProductionComponentMonitorFactoryProvider())));",
            "      this.setOfStringProvider =",
            "          SetFactory.<String>builder(1, 0)",
            "              .addProvider(RootModule_InduceModificationInLeafFactory.create())",
            "              .build();",
            "      DelegateProducer.setDelegate(",
            "          setOfStringProducer,",
            "          Producers.producerFromProvider(getSetOfStringProvider()));",
            "    }",
            "",
            "    @Override",
            "    protected Provider<Executor> getProductionImplementationExecutorProvider() {",
            "      return productionImplementationExecutorProvider;",
            "    }",
            "",
            "    protected Provider<Set<ProductionComponentMonitor.Factory>> ",
            "        getSetOfProductionComponentMonitorFactoryProvider() {",
            "      return SetFactory.<ProductionComponentMonitor.Factory>empty();",
            "    }",
            "",
            "    @Override",
            "    protected Provider<ProductionComponentMonitor> ",
            "        getProductionComponentMonitorProvider() {",
            "      return monitorProvider;",
            "    }",
            "",
            "    protected Provider<Set<String>> getSetOfStringProvider() {",
            "      return setOfStringProvider;",
            "    }",
            "",
            "    @Override",
            "    protected Producer<Set<String>> getSetOfStringProducer() {",
            "      return setOfStringProducer;",
            "    }",
            "",
            "    @Override",
            "    public void onProducerFutureCancelled(boolean mayInterruptIfRunning) {",
            "      super.onProducerFutureCancelled(mayInterruptIfRunning);",
            "      Producers.cancel(getSetOfStringProducer(), mayInterruptIfRunning);",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .hasSourceEquivalentTo(generatedRoot);
  }

  @Test
  public void modifiableBindingMethods_namesDedupedAcrossImplementations() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "foo.Thing",
            "package foo;",
            "", // force multi-line format
            "public interface Thing extends CharSequence {}"),
        JavaFileObjects.forSourceLines(
            "bar.Thing",
            "package bar;",
            "", // force multi-line format
            "public interface Thing extends Runnable {}"),
        JavaFileObjects.forSourceLines(
            "test.WillInduceSetOfRunnable",
            "package test;",
            "", // force multi-line format
            "class WillInduceSetOfRunnable {}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "interface LeafModule {",
            "  @Provides",
            "  static CharSequence depOnFooThing(foo.Thing thing) {",
            "    return thing.toString();",
            "  }",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static Runnable depOnBarThing(bar.Thing thing) {",
            "    return () -> {};",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  CharSequence inducesFoo();",
            "  WillInduceSetOfRunnable willInduceSetOfRunnable();",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import foo.Thing;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public CharSequence inducesFoo() {",
            "    return LeafModule_DepOnFooThingFactory.depOnFooThing(getThing());",
            "  }",
            "",
            "  @Override",
            "  public abstract WillInduceSetOfRunnable willInduceSetOfRunnable();",
            "",
            "  protected abstract Thing getThing();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.Set;",
            "",
            "@Module",
            "interface AncestorModule {",
            "  @Provides",
            "  static WillInduceSetOfRunnable induce(Set<Runnable> set) {",
            "    return null;",
            "  }",
            "",
            "  @Multibinds Set<Runnable> runnables();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"));

    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import bar.Thing;",
            "import com.google.common.collect.ImmutableSet;",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    private Runnable getDepOnBarThing() {",
            "      return LeafModule_DepOnBarThingFactory.depOnBarThing(getThing2());",
            "    }",
            "",
            "    protected abstract Thing getThing2();",
            "",
            "    protected Set<Runnable> getSetOfRunnable() {",
            "      return ImmutableSet.<Runnable>of(getDepOnBarThing());",
            "    }",
            "",
            "    @Override",
            "    public final WillInduceSetOfRunnable willInduceSetOfRunnable() {",
            "      return AncestorModule_InduceFactory.induce(getSetOfRunnable());",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  /**
   * This test verifies that Dagger can find the appropriate child subcomponent
   * super-implementation, even if it is not enclosed in the current component's
   * super-implementation. This can happen if a subcomponent is installed with a module's {@code
   * subcomponents} attribute, but the binding is not accessed in a super-implementation. To exhibit
   * this, we use multibindings that reference the pruned subcomponent, but make the multibinding
   * also unresolved in the base implementation. An ancestor component defines a binding that
   * depends on the multibinding, which induces the previously unresolved multibinding
   * contributions, which itself induces the previously unresolved subcomponent.
   */
  @Test
  public void subcomponentInducedFromAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "Inducer");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.InducedSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface InducedSubcomponent {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    InducedSubcomponent build();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.MaybeLeaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = InducedSubcomponentModule.class)",
            "interface MaybeLeaf {",
            "  Inducer inducer();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.MaybeLeaf",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module(subcomponents = InducedSubcomponent.class)",
            "interface InducedSubcomponentModule {",
            "  @Provides",
            "  @IntoSet",
            "  static Object inducedSet(InducedSubcomponent.Builder builder) {",
            "    return new Object();",
            "  }",
            "}"));

    JavaFileObject generatedMaybeLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerMaybeLeaf implements MaybeLeaf {",
            "  protected DaggerMaybeLeaf() {}",
            "",
            "  @Override",
            "  public abstract Inducer inducer();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerMaybeLeaf")
        .hasSourceEquivalentTo(generatedMaybeLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.Set;",
            "",
            "@Module",
            "interface AncestorModule {",
            "  @Provides",
            "  static Inducer inducer(Set<Object> set) {",
            "    return null;",
            "  }",
            "",
            "  @Multibinds Set<Object> set();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  MaybeLeaf noLongerLeaf();",
            "}"));

    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class MaybeLeafImpl extends DaggerMaybeLeaf {",
            "    protected MaybeLeafImpl() {}",
            "",
            "    private Object getInducedSet() {",
            "      return InducedSubcomponentModule_InducedSetFactory.inducedSet(",
            // TODO(b/117833324): remove this unnecessary cast
            "          (InducedSubcomponent.Builder) getInducedSubcomponentBuilder());",
            "    }",
            "",
            "    protected abstract Object getInducedSubcomponentBuilder();",
            "",
            "    protected Set<Object> getSetOfObject() {",
            "      return ImmutableSet.<Object>of(getInducedSet());",
            "    }",
            "",
            "    @Override",
            "    public final Inducer inducer() {",
            "      return AncestorModule_InducerFactory.inducer(getSetOfObject());",
            "    }",
            "",
            "    protected abstract class InducedSubcomponentImpl extends",
            "        DaggerInducedSubcomponent {",
            //       ^ Note that this is DaggerInducedSubcomponent, not
            //         DaggerMaybeLeaf.InducedSubcomponentImpl
            "      protected InducedSubcomponentImpl() {}",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  @Test
  public void rootScopedAtInjectConstructor_effectivelyMissingInSubcomponent() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "ProvidesMethodRootScoped");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.RootScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "public @interface RootScope {}"),
        JavaFileObjects.forSourceLines(
            "test.AtInjectRootScoped",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "@RootScope",
            "class AtInjectRootScoped {",
            "  @Inject AtInjectRootScoped() {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  AtInjectRootScoped shouldBeEffectivelyMissingInLeaf();",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public abstract AtInjectRootScoped shouldBeEffectivelyMissingInLeaf();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@RootScope",
            "@Component",
            "interface Root {",
            "  Leaf leaf();",
            "}"));

    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    @Override",
            "    public AtInjectRootScoped shouldBeEffectivelyMissingInLeaf() {",
            "      return DaggerRoot.this.atInjectRootScopedProvider.get();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .containsElementsIn(generatedRoot);
  }

  @Test
  public void prunedModuleWithInstanceState() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "Pruned");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Modified",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Modified {",
            "  @Inject Modified(Pruned pruned) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  Pruned pruned() {",
            "    return new Pruned();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Modified modified();",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Modified modified() {",
            "    return new Modified(LeafModule_PrunedFactory.pruned(leafModule()));",
            "  }",
            "",
            "  protected abstract LeafModule leafModule();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.RootModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class RootModule {",
            "  @Provides",
            "  static Modified modified() {",
            "    return new Modified(null);",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = RootModule.class)",
            "interface Root {",
            "  Leaf leaf();",
            "}"));

    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    @Override",
            "    public Modified modified() {",
            "      return RootModule_ModifiedFactory.modified();",
            "    }",
            "",
            "    @Override",
            "    protected LeafModule leafModule() {",
            "      return null;",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .containsElementsIn(generatedRoot);
  }

  @Test
  public void modifiableCycles() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class A {",
            "  @Inject A(B b) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class B {",
            "  @Inject B(Provider<A> a) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  Provider<A> frameworkInstanceCycle();",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.DelegateFactory;",
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private Provider<A> aProvider;",
            "  private Provider<B> bProvider;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization() {",
            "    initialize();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.aProvider = new DelegateFactory<>();",
            "    this.bProvider = B_Factory.create(frameworkInstanceCycle());",
            "    DelegateFactory.setDelegate(aProvider, A_Factory.create(getBProvider()));",
            "  }",
            "",
            "  @Override",
            "  public Provider<A> frameworkInstanceCycle() {",
            "    return aProvider;",
            "  }",
            "",
            "  protected Provider getBProvider() {",
            "    return bProvider;",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);
  }

  /**
   * This tests a regression case where the component builder in the base implementation used one
   * set of disambiguated names from all of the {@link ComponentDescriptor#requirements()}, and the
   * final implementation used a different set of disambiguated names from the resolved {@link
   * BindingGraph#componentRequirements()}. This resulted in generated output that didn't compile,
   * as the builder implementation attempted to use the new names in validation, which didn't line
   * up with the old names.
   */
  @Test
  public void componentBuilderFields_consistencyAcrossImplementations() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "a.Mod",
            "package a;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Named;",
            "",
            "@Module",
            "public class Mod {",
            "  @Provides",
            "  @Named(\"a\")",
            "  int i() { return 0; }",
            "}"),
        JavaFileObjects.forSourceLines(
            "b.Mod",
            "package b;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Named;",
            "",
            "@Module",
            "public class Mod {",
            "  @Provides",
            "  @Named(\"b\")",
            "  int i() { return 0; }",
            "}"),
        JavaFileObjects.forSourceLines(
            "c.Mod",
            "package c;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Named;",
            "",
            "@Module",
            "public class Mod {",
            "  @Provides",
            "  @Named(\"c\")",
            "  int i() { return 0; }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.HasUnusedModuleLeaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import javax.inject.Named;",
            "",
            "@Subcomponent(modules = {a.Mod.class, b.Mod.class, c.Mod.class})",
            "interface HasUnusedModuleLeaf {",
            "  @Named(\"a\") int a();",
            // b omitted intentionally
            "  @Named(\"c\") int c();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Builder setAMod(a.Mod mod);",
            "    Builder setBMod(b.Mod mod);",
            "    Builder setCMod(c.Mod mod);",
            "    HasUnusedModuleLeaf build();",
            "  }",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import a.Mod;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerHasUnusedModuleLeaf implements HasUnusedModuleLeaf {",
            "  public abstract static class Builder implements HasUnusedModuleLeaf.Builder {",
            "    protected Mod mod;",
            "    protected b.Mod mod2;",
            "    protected c.Mod mod3;",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerHasUnusedModuleLeaf")
        .containsElementsIn(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Root {",
            "  HasUnusedModuleLeaf.Builder leaf();",
            "}"));

    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import a.Mod;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  private final class HasUnusedModuleLeafBuilder",
            "      extends DaggerHasUnusedModuleLeaf.Builder {",
            "    @Override",
            "    public HasUnusedModuleLeaf build() {",
            "      if (mod == null) {",
            "        this.mod = new Mod();",
            "      }",
            // Before this regression was fixed, `mod3` was instead `mod2`, since the `b.Mod` was
            // pruned from the graph and did not need validation.
            "      if (mod3 == null) {",
            "        this.mod3 = new c.Mod();",
            "      }",
            "      return new HasUnusedModuleLeafImpl(mod, mod3);",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .containsElementsIn(generatedRoot);
  }

  @Test
  public void dependencyExpressionCasting() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.PublicType",
            "package test;",
            "", //
            "public class PublicType {}"),
        JavaFileObjects.forSourceLines(
            "test.ModifiableNonPublicSubclass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class ModifiableNonPublicSubclass extends PublicType {",
            "  @Inject ModifiableNonPublicSubclass() {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Parameterized",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Parameterized<T extends PublicType> {",
            "  @Inject Parameterized(T t) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  Parameterized<ModifiableNonPublicSubclass> parameterizedWithNonPublicSubtype();",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  @Override",
            "  public Parameterized<ModifiableNonPublicSubclass> ",
            "      parameterizedWithNonPublicSubtype() {",
            "    return Parameterized_Factory.newInstance(",
            "        (ModifiableNonPublicSubclass) getModifiableNonPublicSubclass());",
            "  }",
            "",
            "  protected Object getModifiableNonPublicSubclass() {",
            "    return new ModifiableNonPublicSubclass();",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .containsElementsIn(generatedLeaf);
  }

  @Test
  public void multipleComponentMethodsForSameBindingRequest() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  String string1();",
            "  String string2();",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  @Override",
            "  public final String string2() {",
            "    return string1();",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .containsElementsIn(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.RootModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface RootModule {",
            "  @Provides",
            "  static String string() {",
            "    return new String();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = RootModule.class)",
            "interface Root {",
            "  Leaf leaf();",
            "}"));

    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    private LeafImpl() {}",
            "",
            "    @Override",
            "    public String string1() {",
            "      return RootModule_StringFactory.string();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .containsElementsIn(generatedRoot);
  }

  @Test
  public void boundInstanceUsedOnlyInInitialize() {
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Subcomponent;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent",
            "interface Sub {",
            "  Provider<String> stringProvider();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    @BindsInstance",
            "    Builder string(String string);",
            "    Sub build();",
            "  }",
            "}");

    JavaFileObject generated  =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.internal.InstanceFactory;",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerSub implements Sub {",
            "  private Provider<String> stringProvider;",
            "",
            "  protected DaggerSub() {}",
            "",
            "  protected void configureInitialization(String stringParam) {",
            "    initialize(stringParam);",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final String stringParam) {",
            "    this.stringProvider = InstanceFactory.create(stringParam);",
            "  }",
            "",
            "  @Override",
            "  public Provider<String> stringProvider() {",
            "    return stringProvider;",
            "  }",
            "}");

    Compilation compilation = compile(ImmutableList.of(subcomponent));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSub")
        .containsElementsIn(generated);
  }

  @Test
  public void packagePrivate_derivedFromFrameworkInstance_ComponentMethod() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.PackagePrivate",
            "package test;",
            "",
            "import dagger.Reusable;",
            "import javax.inject.Inject;",
            "",
            "@Reusable", // Use @Reusable to force a framework field
            "class PackagePrivate {",
            "  @Inject PackagePrivate() {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  PackagePrivate packagePrivate();",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private Provider<PackagePrivate> packagePrivateProvider;",
            "",
            "  @Override",
            "  public PackagePrivate packagePrivate() {",
            "    return (PackagePrivate) getPackagePrivateProvider().get();",
            "  }",
            "",
            "  protected Provider getPackagePrivateProvider() {",
            "    return packagePrivateProvider;",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .containsElementsIn(generatedLeaf);
  }

  @Test
  public void castModifiableMethodAccessedInFinalImplementation() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "PackagePrivate");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.PublicBaseType",
            "package test;",
            "", //
            "public class PublicBaseType {}"),
        JavaFileObjects.forSourceLines(
            "test.PackagePrivateSubtype",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            // Force this to be a modifiable binding resolved in the ancestor even though the
            // binding is requested in the leaf.
            "@AncestorScope",
            "class PackagePrivateSubtype extends PublicBaseType {",
            "  @Inject PackagePrivateSubtype() {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.AncestorScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface AncestorScope {}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface LeafModule {",
            "  @Binds PublicBaseType publicBaseType(PackagePrivateSubtype subtype);",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.InjectsOptionalOfModifiable",
            "package test;",
            "",
            "import java.util.Optional;",
            "import javax.inject.Inject;",
            "",
            "class InjectsOptionalOfModifiable {",
            "  @Inject InjectsOptionalOfModifiable(",
            "      Optional<PublicBaseType> optionalOfModifiable) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  InjectsOptionalOfModifiable injectsOptionalOfModifiable();",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected abstract Optional<PublicBaseType> getOptionalOfPublicBaseType();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .containsElementsIn(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.InjectsPackagePrivateSubtype",
            "package test;",
            "",
            "import java.util.Optional;",
            "import javax.inject.Inject;",
            "",
            "class InjectsPackagePrivateSubtype {",
            "  @Inject InjectsPackagePrivateSubtype(",
            //     Force a modifiable binding method for PackagePrivateSubtype in Ancestor. The
            //     final Leaf implementation will refer to this method, but will need to cast it
            //     since the PackagePrivateSubtype is accessible from the current package, but the
            //     method returns Object
            "      PackagePrivateSubtype packagePrivateSubtype) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface AncestorModule {",
            "  @Provides",
            "  static PackagePrivateSubtype packagePrivateSubtype() {",
            "    return new PackagePrivateSubtype();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@AncestorScope",
            "@Subcomponent",
            "interface Ancestor {",
            "  InjectsPackagePrivateSubtype injectsPackagePrivateSubtype();",
            "  Leaf leaf();",
            "}"));

    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected Object getPackagePrivateSubtype() {",
            "    return getPackagePrivateSubtypeProvider().get();",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .containsElementsIn(generatedAncestor);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.RootModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface RootModule {",
            "  @BindsOptionalOf",
            "  PublicBaseType optionalPublicBaseType();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = RootModule.class)",
            "interface Root {",
            "  Ancestor ancestor();",
            "}"));

    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root {",
            "  protected final class AncestorImpl extends DaggerAncestor {",
            "    protected final class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      @Override",
            "      protected Optional<PublicBaseType> getOptionalOfPublicBaseType() {",
            "        return Optional.of(",
            "            (PublicBaseType) AncestorImpl.this.getPackagePrivateSubtype());",
            "      }",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .containsElementsIn(generatedRoot);
  }

  @Test
  public void injectInLeaf_ProductionInRoot() {
    // most of this is also covered in ProducesMethodShadowsInjectConstructorTest, but this test
    // asserts that the correct PrunedConcreteBindingExpression is used
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "Dependency", "Missing");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Injected",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Injected {",
            "  @Inject Injected(Dependency dependency, Missing missing) {}",
            "",
            "  Injected(Dependency dependency) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "",
            "@ProducerModule",
            "interface LeafModule {",
            "  @Produces",
            "  static Object dependsOnInjectReplacedWithProduces(Injected injected) {",
            "    return new Object();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.producers.Producer;",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Producer<Object> objectProducer();",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf, CancellationListener {",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.injectedProvider = Injected_Factory.create(",
            "        getDependencyProvider(), getMissingProvider());",
            "    this.injectedProducer = Producers.producerFromProvider(getInjectedProvider());",
            "    this.dependsOnInjectReplacedWithProducesProducer =",
            "        LeafModule_DependsOnInjectReplacedWithProducesFactory.create(",
            "            getProductionImplementationExecutorProvider(),",
            "            getProductionComponentMonitorProvider(),",
            "            getInjectedProducer());",
            "    this.objectProducerEntryPoint =",
            "        Producers.entryPointViewOf(",
            "            dependsOnInjectReplacedWithProducesProducer, this);",
            "  }",
            "",
            "  protected abstract Provider getDependencyProvider();",
            "  protected abstract Provider getMissingProvider();",
            "",
            "  protected Provider getInjectedProvider() {",
            "    return injectedProvider;",
            "  }",
            "",
            "  protected Producer getInjectedProducer() {",
            "    return injectedProducer;",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .containsElementsIn(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.RootModule",
            "package test;",
            "",
            "import com.google.common.util.concurrent.MoreExecutors;",
            "import dagger.Provides;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "import dagger.producers.Production;",
            "import java.util.concurrent.Executor;",
            "",
            "@ProducerModule",
            "interface RootModule {",
            "  @Produces",
            "  static Injected replaceInjectWithProduces(Dependency dependency) {",
            "    return new Injected(dependency);",
            "  }",
            "",
            "  @Produces",
            "  static Dependency dependency() {",
            "    return new Dependency();",
            "  }",
            "",
            "  @Provides",
            "  @Production",
            "  static Executor executor() {",
            "    return MoreExecutors.directExecutor();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Root",
            "package test;",
            "",
            "import dagger.producers.ProductionComponent;",
            "",
            "@ProductionComponent(modules = RootModule.class)",
            "interface Root {",
            "  Leaf leaf();",
            "}"));

    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerRoot implements Root, CancellationListener {",
            "  private Producer<Dependency> dependencyProducer;",
            "  private Producer<Injected> replaceInjectWithProducesProducer;",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.productionImplementationExecutorProvider =",
            "        DoubleCheck.provider((Provider) RootModule_ExecutorFactory.create());",
            "    this.rootProvider = InstanceFactory.create((Root) this);",
            "    this.monitorProvider =",
            "        DoubleCheck.provider(",
            "            Root_MonitoringModule_MonitorFactory.create(",
            "                rootProvider,",
            "                SetFactory.<ProductionComponentMonitor.Factory>empty()));",
            "    this.dependencyProducer =",
            "        RootModule_DependencyFactory.create(",
            "            productionImplementationExecutorProvider, monitorProvider);",
            "    this.replaceInjectWithProducesProducer =",
            "        RootModule_ReplaceInjectWithProducesFactory.create(",
            "            productionImplementationExecutorProvider,",
            "            monitorProvider,",
            "            dependencyProducer);",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf",
            "      implements CancellationListener {",
            "    @Override",
            "    protected Provider getDependencyProvider() {",
            "      return MissingBindingFactory.create();",
            "    }",
            "",
            "    @Override",
            "    protected Provider getMissingProvider() {",
            "      return MissingBindingFactory.create();",
            "    }",
            "",
            "    @Override",
            "    protected Producer getInjectedProducer() {",
            "      return DaggerRoot.this.replaceInjectWithProducesProducer;",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRoot")
        .containsElementsIn(generatedRoot);
  }

  // TODO(ronshapiro): remove copies from AheadOfTimeSubcomponents*Test classes
  private void createSimplePackagePrivateClasses(
      ImmutableList.Builder<JavaFileObject> filesBuilder, String... ancillaryClasses) {
    for (String className : ancillaryClasses) {
      filesBuilder.add(
          JavaFileObjects.forSourceLines(
              String.format("test.%s", className),
              "package test;",
              "",
              String.format("class %s { }", className)));
    }
  }

  private static Compilation compile(Iterable<JavaFileObject> files, CompilerMode... modes) {
    return compilerWithOptions(
            ObjectArrays.concat(
                new CompilerMode[] {AHEAD_OF_TIME_SUBCOMPONENTS_MODE}, modes, CompilerMode.class))
        .compile(files);
  }
}
