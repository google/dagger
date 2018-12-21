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
import static dagger.internal.codegen.Compilers.CLASS_PATH_WITHOUT_GUAVA_OPTION;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.common.collect.ImmutableList;
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
    createAncillaryClasses(filesToCompile, "MissingInLeaf");
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public final MissingInLeaf missingFromComponentMethod() {",
            "      return AncestorModule_SatisfiedInAncestorFactory.proxySatisfiedInAncestor();",
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
    createAncillaryClasses(filesToCompile, "MissingInLeaf");
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
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
    createAncillaryClasses(filesToCompile, "MissingInLeaf");
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public DependsOnMissingBinding dependsOnMissingBinding() {",
            "    return DependsOnMissingBinding_Factory.newDependsOnMissingBinding(",
            "        getMissingInLeaf());",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    protected final Object getMissingInLeaf() {",
            "      return AncestorModule_SatisfiedInAncestorFactory.proxySatisfiedInAncestor();",
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
    createAncillaryClasses(filesToCompile, "MissingInLeaf");
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "        return SatisfiesMissingBindingModule_SatisfyFactory.proxySatisfy();",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public String string() {",
            "    return TestModule_ProvideStringFactory.proxyProvideString(testModule());",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
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
            "      private TestModule testModule;",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
            "    }",
            "  }",
            "",
            "  protected final class AncestorImpl extends DaggerAncestor {",
            "    private AncestorImpl() {}",
            "",
            "    @Override",
            "    public Leaf leaf(TestModule module) {",
            "      return new LeafImpl(module);",
            "    }",
            "",
            "    protected final class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      private TestModule testModule;",
            "",
            "      private LeafImpl(TestModule module) {",
            "        this.testModule = Preconditions.checkNotNull(module);",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  public abstract static class Builder implements Leaf.Builder {}",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
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
            "        return new LeafImpl(this);",
            "      }",
            "    }",
            "",
            "    protected final class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      private LeafImpl(LeafBuilder builder) {}",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerMaybeLeaf implements MaybeLeaf {",
            "  protected DaggerMaybeLeaf() {}",
            "",
            "  @Override",
            "  public DependsOnPrunedSubcomponentBuilder dependsOnPrunedSubcomponentBuilder() {",
            "    return DependsOnPrunedSubcomponentBuilder_Factory",
            "        .newDependsOnPrunedSubcomponentBuilder(",
            "            getPrunedSubcomponentBuilder());",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
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
            "          .proxyPruneGeneratedInstance();",
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
    createAncillaryClasses(filesToCompile, "SatisfiedInSub");
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
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerSub implements Sub {",
            "  protected DaggerSub() {}",
            "",
            "  @Override",
            "  public Optional<SatisfiedInSub> satisfiedInSub() {",
            "    return Optional.of(",
            "        BindsSatisfiedInSubModule_ProvideSatisfiedInSubFactory",
            "            .proxyProvideSatisfiedInSub());",
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
    createAncillaryClasses(filesToCompile, "SatisfiedInAncestor");
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
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Optional<SatisfiedInAncestor> satisfiedInAncestor() {",
            "    return Optional.<SatisfiedInAncestor>empty();",
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
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "          .proxySatisfiedInAncestor());",
            "    }",
            "",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  @Test
  public void optionalBindings_satisfiedInGrandAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "SatisfiedInGrandAncestor");
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
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Optional<SatisfiedInGrandAncestor> satisfiedInGrandAncestor() {",
            "    return Optional.<SatisfiedInGrandAncestor>empty();",
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
            "@Subcomponent",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "                .proxySatisfiedInGrandAncestor());",
            "      }",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGreatAncestor")
        .hasSourceEquivalentTo(generatedGreatAncestor);
  }

  @Test
  public void optionalBindings_nonComponentMethodDependencySatisfiedInAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(
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
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public RequiresOptionalSatisfiedInAncestor",
            "      requiresOptionalSatisfiedInAncestor() {",
            "    return LeafModule_ProvideRequiresOptionalSatisfiedInAncestorFactory",
            "        .proxyProvideRequiresOptionalSatisfiedInAncestor(",
            "            getOptionalOfSatisfiedInAncestor());",
            "  }",
            "",
            "  protected Optional getOptionalOfSatisfiedInAncestor() {",
            "    return Optional.<SatisfiedInAncestor>empty();",
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
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "          AncestorModule_SatisfiedInAncestorFactory.proxySatisfiedInAncestor());",
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
  public void optionalBindings_boundInAncestorAndSatisfiedInGrandAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "SatisfiedInGrandAncestor");
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "  @Provides static SatisfiedInGrandAncestor provideSatisfiedInGrandAncestor() {",
            "    return new SatisfiedInGrandAncestor();",
            "  }",
            "}"));
    JavaFileObject generatedGrandAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandAncestor",
            "package test;",
            "",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "                .proxyProvideSatisfiedInGrandAncestor());",
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
  public void setMultibindings_contributionsInLeaf() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InLeaf");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Set<InLeaf> contributionsInLeaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoSet",
            "  static InLeaf provideInLeaf() {",
            "    return new InLeaf();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Set<InLeaf> contributionsInLeaf() {",
            "    return ImmutableSet.<InLeaf>of(",
            "        LeafModule_ProvideInLeafFactory.proxyProvideInLeaf());",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);
  }

  @Test
  public void setMultibindings_contributionsInAncestorOnly() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InAncestor");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  Set<InAncestor> contributionsInAncestor();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @ElementsIntoSet",
            "  static Set<InAncestor> provideInAncestors() {",
            "    return ImmutableSet.of(new InAncestor(), new InAncestor());",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public Set<InAncestor> contributionsInAncestor() {",
            "      return ImmutableSet.<InAncestor>copyOf(",
            "          AncestorModule_ProvideInAncestorsFactory.proxyProvideInAncestors());",
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
  public void setMultibindings_contributionsInLeafAndAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InEachSubcomponent");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Set<InEachSubcomponent> contributionsInEachSubcomponent();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoSet",
            "  static InEachSubcomponent provideInLeaf() {",
            "    return new InEachSubcomponent();",
            "  }",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static InEachSubcomponent provideAnotherInLeaf() {",
            "    return new InEachSubcomponent();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Set<InEachSubcomponent> contributionsInEachSubcomponent() {",
            "    return ImmutableSet.<InEachSubcomponent>of(",
            "        LeafModule_ProvideInLeafFactory.proxyProvideInLeaf(),",
            "        LeafModule_ProvideAnotherInLeafFactory.proxyProvideAnotherInLeaf());",
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
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @ElementsIntoSet",
            "  static Set<InEachSubcomponent> provideInAncestor() {",
            "    return ImmutableSet.of(new InEachSubcomponent(), new InEachSubcomponent());",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public Set<InEachSubcomponent> contributionsInEachSubcomponent() {",
            "      return ImmutableSet.<InEachSubcomponent>builderWithExpectedSize(3)",
            "          .addAll(AncestorModule_ProvideInAncestorFactory.proxyProvideInAncestor())",
            "          .addAll(super.contributionsInEachSubcomponent())",
            "          .build();",
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
  public void setMultibindings_contributionsInLeafAndGrandAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InLeafAndGrandAncestor");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Set<InLeafAndGrandAncestor> contributionsInLeafAndGrandAncestor();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoSet",
            "  static InLeafAndGrandAncestor provideInLeaf() {",
            "    return new InLeafAndGrandAncestor();",
            "  }",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static InLeafAndGrandAncestor provideAnotherInLeaf() {",
            "    return new InLeafAndGrandAncestor();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Set<InLeafAndGrandAncestor> contributionsInLeafAndGrandAncestor() {",
            "    return ImmutableSet.<InLeafAndGrandAncestor>of(",
            "        LeafModule_ProvideInLeafFactory.proxyProvideInLeaf(),",
            "        LeafModule_ProvideAnotherInLeafFactory.proxyProvideAnotherInLeaf());",
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
            "import java.util.Set;",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = GrandAncestorModule.class)",
            "interface GrandAncestor {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.GrandAncestorModule",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class GrandAncestorModule {",
            "  @Provides",
            "  @ElementsIntoSet",
            "  static Set<InLeafAndGrandAncestor> provideInGrandAncestor() {",
            "    return ImmutableSet.of(new InLeafAndGrandAncestor(),",
            "        new InLeafAndGrandAncestor());",
            "  }",
            "}"));
    JavaFileObject generatedGrandAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandAncestor implements GrandAncestor {",
            "  protected DaggerGrandAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public Set<InLeafAndGrandAncestor> contributionsInLeafAndGrandAncestor() {",
            "      return ImmutableSet.<InLeafAndGrandAncestor>builderWithExpectedSize(3)",
            "          .addAll(GrandAncestorModule_ProvideInGrandAncestorFactory",
            "              .proxyProvideInGrandAncestor())",
            "          .addAll(super.contributionsInLeafAndGrandAncestor())",
            "          .build();",
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
  public void setMultibindings_nonComponentMethodDependency() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InAllSubcomponents", "RequresInAllSubcomponentsSet");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  RequresInAllSubcomponentsSet requiresNonComponentMethod();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoSet",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "    return new InAllSubcomponents();",
            "  }",
            "",
            "  @Provides",
            "  static RequresInAllSubcomponentsSet providesRequresInAllSubcomponentsSet(",
            "      Set<InAllSubcomponents> inAllSubcomponents) {",
            "    return new RequresInAllSubcomponentsSet();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public RequresInAllSubcomponentsSet requiresNonComponentMethod() {",
            "    return LeafModule_ProvidesRequresInAllSubcomponentsSetFactory",
            "        .proxyProvidesRequresInAllSubcomponentsSet(getSetOfInAllSubcomponents());",
            "  }",
            "",
            "  protected Set getSetOfInAllSubcomponents() {",
            "    return ImmutableSet.<InAllSubcomponents>of(",
            "        LeafModule_ProvideInAllSubcomponentsFactory",
            "            .proxyProvideInAllSubcomponents());",
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
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @IntoSet",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "      return new InAllSubcomponents();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    protected Set getSetOfInAllSubcomponents() {",
            "      return ImmutableSet.<InAllSubcomponents>builderWithExpectedSize(2)",
            "          .add(AncestorModule_ProvideInAllSubcomponentsFactory",
            "              .proxyProvideInAllSubcomponents())",
            "          .addAll(super.getSetOfInAllSubcomponents())",
            "          .build();",
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
  public void setMultibindings_newSubclass() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InAncestor", "RequiresInAncestorSet");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  RequiresInAncestorSet missingWithSetDependency();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class AncestorModule {",
            "",
            "  @Provides",
            "  static RequiresInAncestorSet provideRequiresInAncestorSet(",
            "      Set<InAncestor> inAncestors) {",
            "    return new RequiresInAncestorSet();",
            "  }",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static InAncestor provideInAncestor() {",
            "    return new InAncestor();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            // TODO(b/117833324): because this is a private method, the return type shouldn't need
            // to be the publicly accessible type. This may be easier to detect if we fold
            // BindingMethodImplementation into MethodBindingExpression
            "  private Object getRequiresInAncestorSet() {",
            "    return AncestorModule_ProvideRequiresInAncestorSetFactory",
            "        .proxyProvideRequiresInAncestorSet(getSetOfInAncestor());",
            "  }",
            "",
            "  protected Set getSetOfInAncestor() {",
            "    return ImmutableSet.<InAncestor>of(",
            "        AncestorModule_ProvideInAncestorFactory.proxyProvideInAncestor());",
            "  }",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public final RequiresInAncestorSet missingWithSetDependency() {",
            "      return (RequiresInAncestorSet) DaggerAncestor.this.getRequiresInAncestorSet();",
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
  public void setMultibinding_requestedAsInstanceInLeaf_requestedAsFrameworkInstanceFromAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(
        filesToCompile, "Multibound", "MissingInLeaf_WillDependOnFrameworkInstance");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Set<Multibound> instance();",
            "  MissingInLeaf_WillDependOnFrameworkInstance willDependOnFrameworkInstance();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoSet",
            "  static Multibound contribution() {",
            "    return new Multibound();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Set<Multibound> instance() {",
            "    return ImmutableSet.<Multibound>of(",
            "        LeafModule_ContributionFactory.proxyContribution());",
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
            "import dagger.multibindings.Multibinds;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "",
            "@Module",
            "interface AncestorModule {",
            "  @Provides",
            "  static MissingInLeaf_WillDependOnFrameworkInstance providedInAncestor(",
            "      Provider<Set<Multibound>> frameworkInstance) {",
            "    return null;",
            "  }",
            "",
            "  @Multibinds Set<Multibound> multibinds();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.SetFactory;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    private Provider<Set<Multibound>> setOfMultiboundProvider;",
            "",
            "    protected LeafImpl() {}",
            "",
            "    protected void configureInitialization() { ",
            "      initialize();",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize() { ",
            "      this.setOfMultiboundProvider =",
            "          SetFactory.<Multibound>builder(1, 0)",
            "              .addProvider(LeafModule_ContributionFactory.create())",
            "              .build();",
            "    }",
            "",
            "    protected Provider getSetOfMultiboundProvider() {",
            "      return setOfMultiboundProvider;",
            "    }",
            "",
            "    @Override",
            "    public final MissingInLeaf_WillDependOnFrameworkInstance ",
            "        willDependOnFrameworkInstance() {",
            "      return AncestorModule_ProvidedInAncestorFactory.proxyProvidedInAncestor(",
            "          getSetOfMultiboundProvider());",
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
  public void setMultibindings_contributionsInLeafAndAncestor_frameworkInstances() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InEachSubcomponent");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Provider<Set<InEachSubcomponent>> contributionsInEachSubcomponent();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoSet",
            "  static InEachSubcomponent provideInLeaf() {",
            "    return new InEachSubcomponent();",
            "  }",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static InEachSubcomponent provideAnotherInLeaf() {",
            "    return new InEachSubcomponent();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.SetFactory;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private Provider<Set<InEachSubcomponent>> setOfInEachSubcomponentProvider;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization() {",
            "    initialize();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.setOfInEachSubcomponentProvider =",
            "        SetFactory.<InEachSubcomponent>builder(2, 0)",
            "            .addProvider(LeafModule_ProvideInLeafFactory.create())",
            "            .addProvider(LeafModule_ProvideAnotherInLeafFactory.create())",
            "            .build();",
            "  }",
            "",
            "  @Override",
            "  public Provider<Set<InEachSubcomponent>> contributionsInEachSubcomponent() {",
            "    return setOfInEachSubcomponentProvider;",
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
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = AncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.AncestorModule",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @ElementsIntoSet",
            "  static Set<InEachSubcomponent> provideInAncestor() {",
            "    return ImmutableSet.of(new InEachSubcomponent(), new InEachSubcomponent());",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.DelegateFactory;",
            "import dagger.internal.SetFactory;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    private Provider<Set<InEachSubcomponent>> setOfInEachSubcomponentProvider = ",
            "        new DelegateFactory<>();",
            "",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    protected void configureInitialization() {",
            "      super.configureInitialization();",
            "      initialize();",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize() {",
            "      DelegateFactory.setDelegate(",
            "          setOfInEachSubcomponentProvider,",
            "          SetFactory.<InEachSubcomponent>builder(0, 2)",
            "              .addCollectionProvider(super.contributionsInEachSubcomponent())",
            "              .addCollectionProvider(",
            "                  AncestorModule_ProvideInAncestorFactory.create())",
            "              .build());",
            "    }",
            "",
            "    @Override",
            "    public Provider<Set<InEachSubcomponent>> contributionsInEachSubcomponent() {",
            "      return setOfInEachSubcomponentProvider;",
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
  public void mapMultibindings_contributionsInLeaf() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InLeaf");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Map<String, InLeaf> contributionsInLeaf();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"leafmodule\")",
            "  static InLeaf provideInLeaf() {",
            "    return new InLeaf();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Map<String, InLeaf> contributionsInLeaf() {",
            "    return ImmutableMap.<String, InLeaf>of(",
            "        \"leafmodule\",",
            "        LeafModule_ProvideInLeafFactory.proxyProvideInLeaf());",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);
  }

  @Test
  public void mapMultibindings_contributionsInAncestorOnly() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InAncestor");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  Map<String, InAncestor> contributionsInAncestor();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"ancestormodule\")",
            "  static InAncestor provideInAncestor() {",
            "    return new InAncestor();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public Map<String, InAncestor> contributionsInAncestor() {",
            "      return ImmutableMap.<String, InAncestor>of(\"ancestormodule\",",
            "          AncestorModule_ProvideInAncestorFactory.proxyProvideInAncestor());",
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
  public void mapMultibindings_contributionsInLeafAndAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InEachSubcomponent");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Map<String, InEachSubcomponent> contributionsInEachSubcomponent();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"leafmodule\")",
            "  static InEachSubcomponent provideInLeaf() {",
            "    return new InEachSubcomponent();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Map<String, InEachSubcomponent> contributionsInEachSubcomponent() {",
            "    return ImmutableMap.<String, InEachSubcomponent>of(",
            "        \"leafmodule\", LeafModule_ProvideInLeafFactory.proxyProvideInLeaf());",
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
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"ancestormodule\")",
            "  static InEachSubcomponent provideInAncestor() {",
            "    return new InEachSubcomponent();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public Map<String, InEachSubcomponent> contributionsInEachSubcomponent() {",
            "      return ImmutableMap.<String, InEachSubcomponent>builderWithExpectedSize(2)",
            "          .put(\"ancestormodule\",",
            "              AncestorModule_ProvideInAncestorFactory.proxyProvideInAncestor())",
            "          .putAll(super.contributionsInEachSubcomponent())",
            "          .build();",
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
  public void mapMultibindings_contributionsInLeafAndAncestor_frameworkInstance() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InEachSubcomponent");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Provider<Map<String, InEachSubcomponent>> contributionsInEachSubcomponent();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"leafmodule\")",
            "  static InEachSubcomponent provideInLeaf() {",
            "    return new InEachSubcomponent();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.MapFactory;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private Provider<Map<String, InEachSubcomponent>> ",
            "    mapOfStringAndInEachSubcomponentProvider;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization() {",
            "    initialize();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.mapOfStringAndInEachSubcomponentProvider =",
            "        MapFactory.<String, InEachSubcomponent>builder(1)",
            "            .put(\"leafmodule\", LeafModule_ProvideInLeafFactory.create())",
            "            .build();",
            "  }",
            "",
            "  @Override",
            "  public Provider<Map<String, InEachSubcomponent>> ",
            "      contributionsInEachSubcomponent() {",
            "    return mapOfStringAndInEachSubcomponentProvider;",
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
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"ancestormodule\")",
            "  static InEachSubcomponent provideInAncestor() {",
            "    return new InEachSubcomponent();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.DelegateFactory;",
            "import dagger.internal.MapFactory;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    private Provider<Map<String, InEachSubcomponent>> ",
            "      mapOfStringAndInEachSubcomponentProvider = new DelegateFactory<>();",
            "",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    protected void configureInitialization() { ",
            "      super.configureInitialization();",
            "      initialize();",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize() { ",
            "      DelegateFactory.setDelegate(",
            "          mapOfStringAndInEachSubcomponentProvider,",
            "          MapFactory.<String, InEachSubcomponent>builder(2)",
            "              .putAll(super.contributionsInEachSubcomponent())",
            "              .put(",
            "                  \"ancestormodule\",",
            "                  AncestorModule_ProvideInAncestorFactory.create())",
            "              .build());",
            "    }",
            "",
            "    @Override",
            "    public Provider<Map<String, InEachSubcomponent>> ",
            "        contributionsInEachSubcomponent() {",
            "      return mapOfStringAndInEachSubcomponentProvider;",
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
  public void mapMultibindings_contributionsInLeafAndGrandAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InLeafAndGrandAncestor");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Map<String, InLeafAndGrandAncestor> contributionsInLeafAndGrandAncestor();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"leafmodule\")",
            "  static InLeafAndGrandAncestor provideInLeaf() {",
            "    return new InLeafAndGrandAncestor();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Map<String, InLeafAndGrandAncestor> contributionsInLeafAndGrandAncestor() {",
            "    return ImmutableMap.<String, InLeafAndGrandAncestor>of(",
            "        \"leafmodule\", LeafModule_ProvideInLeafFactory.proxyProvideInLeaf());",
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
            "@Subcomponent",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class GrandAncestorModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"grandancestormodule\")",
            "  static InLeafAndGrandAncestor provideInGrandAncestor() {",
            "    return new InLeafAndGrandAncestor();",
            "  }",
            "}"));
    JavaFileObject generatedGrandAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "      public Map<String, InLeafAndGrandAncestor>",
            "          contributionsInLeafAndGrandAncestor() {",
            "        return",
            "            ImmutableMap.<String, InLeafAndGrandAncestor>builderWithExpectedSize(2)",
            "                .put(\"grandancestormodule\",",
            "                    GrandAncestorModule_ProvideInGrandAncestorFactory",
            "                        .proxyProvideInGrandAncestor())",
            "                .putAll(super.contributionsInLeafAndGrandAncestor())",
            "                .build();",
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
  public void mapMultibindings_contributionsInLeafAndAncestorWithoutGuava() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InEachSubcomponent");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Map<String, InEachSubcomponent> contributionsInEachSubcomponent();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"leafmodule\")",
            "  static InEachSubcomponent provideInLeaf() {",
            "    return new InEachSubcomponent();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import java.util.Collections;",
            "import java.util.Map",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Map<String, InEachSubcomponent> contributionsInEachSubcomponent() {",
            "    return Collections.<String, InEachSubcomponent>singletonMap(",
            "        \"leafmodule\", LeafModule_ProvideInLeafFactory.proxyProvideInLeaf());",
            "  }",
            "}");
    Compilation compilation = compileWithoutGuava(filesToCompile.build());
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
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"ancestormodule\")",
            "  static InEachSubcomponent provideInAncestor() {",
            "    return new InEachSubcomponent();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.MapBuilder;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public Map<String, InEachSubcomponent> contributionsInEachSubcomponent() {",
            "      return MapBuilder.<String, InEachSubcomponent>newMapBuilder(2)",
            "          .put(\"ancestormodule\",",
            "              AncestorModule_ProvideInAncestorFactory.proxyProvideInAncestor())",
            "          .putAll(super.contributionsInEachSubcomponent())",
            "          .build();",
            "    }",
            "  }",
            "}");
    compilation = compileWithoutGuava(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
  }

  @Test
  public void mapMultibinding_requestedAsInstanceInLeaf_requestedAsFrameworkInstanceFromAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(
        filesToCompile, "Multibound", "MissingInLeaf_WillDependOnFrameworkInstance");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Map<Integer, Multibound> instance();",
            "  MissingInLeaf_WillDependOnFrameworkInstance willDependOnFrameworkInstance();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntKey;",
            "import dagger.multibindings.IntoMap;",
            "import java.util.Map;",
            "",
            "@Module",
            "class LeafModule {",
            "  @Provides",
            "  @IntoMap",
            "  @IntKey(111)",
            "  static Multibound contribution() {",
            "    return new Multibound();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Map<Integer, Multibound> instance() {",
            "    return ImmutableMap.<Integer, Multibound>of(",
            "        111, LeafModule_ContributionFactory.proxyContribution());",
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
            "import dagger.multibindings.Multibinds;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "",
            "@Module",
            "interface AncestorModule {",
            "  @Provides",
            "  static MissingInLeaf_WillDependOnFrameworkInstance providedInAncestor(",
            "      Provider<Map<Integer, Multibound>> frameworkInstance) {",
            "    return null;",
            "  }",
            "",
            "  @Multibinds Map<Integer, Multibound> multibinds();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.MapFactory;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    private Provider<Map<Integer, Multibound>> mapOfIntegerAndMultiboundProvider;",
            "",
            "    protected LeafImpl() {}",
            "",
            "    protected void configureInitialization() { ",
            "      initialize();",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize() { ",
            "      this.mapOfIntegerAndMultiboundProvider =",
            "          MapFactory.<Integer, Multibound>builder(1)",
            "              .put(111, LeafModule_ContributionFactory.create())",
            "              .build();",
            "    }",
            "",
            "    protected Provider getMapOfIntegerAndMultiboundProvider() {",
            "      return mapOfIntegerAndMultiboundProvider;",
            "    }",
            "",
            "    @Override",
            "    public final MissingInLeaf_WillDependOnFrameworkInstance ",
            "        willDependOnFrameworkInstance() {",
            "      return AncestorModule_ProvidedInAncestorFactory.proxyProvidedInAncestor(",
            "          getMapOfIntegerAndMultiboundProvider());",
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
  public void emptyMultibinds_set() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "Multibound");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.Set;",
            "",
            "@Module",
            "interface LeafModule {",
            "  @Multibinds",
            "  Set<Multibound> set();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Set<Multibound> set();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Set<Multibound> set() {",
            "    return ImmutableSet.<Multibound>of();",
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
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @IntoSet",
            "  static Multibound fromAncestor() {",
            "    return new Multibound();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public Set<Multibound> set() {",
            "      return ImmutableSet.<Multibound>of(",
            "          AncestorModule_FromAncestorFactory.proxyFromAncestor());",
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
  public void emptyMultibinds_set_frameworkInstance() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "Multibound");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.Set;",
            "",
            "@Module",
            "interface LeafModule {",
            "  @Multibinds",
            "  Set<Multibound> set();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Provider<Set<Multibound>> set();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.SetFactory;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Provider<Set<Multibound>> set() {",
            "    return SetFactory.<Multibound>empty();",
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
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @IntoSet",
            "  static Multibound fromAncestor() {",
            "    return new Multibound();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.DelegateFactory;",
            "import dagger.internal.SetFactory;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    private Provider<Set<Multibound>> setOfMultiboundProvider =",
            "        new DelegateFactory<>();",
            "",
            "    protected LeafImpl() {}",
            "",
            "    protected void configureInitialization() {",
            "      initialize();",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize() {",
            "      DelegateFactory.setDelegate(",
            "          setOfMultiboundProvider,",
            "          SetFactory.<Multibound>builder(1, 0)",
            "              .addProvider(AncestorModule_FromAncestorFactory.create())",
            "              .build());",
            "    }",
            "",
            "    @Override",
            "    public Provider<Set<Multibound>> set() {",
            "      return setOfMultiboundProvider;",
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
  public void emptyMultibinds_map() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "Multibound");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.Map;",
            "",
            "@Module",
            "interface LeafModule {",
            "  @Multibinds",
            "  Map<Integer, Multibound> map();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Map<Integer, Multibound> map();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Map<Integer, Multibound> map() {",
            "    return ImmutableMap.<Integer, Multibound>of();",
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
            "import dagger.multibindings.IntKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @IntoMap",
            "  @IntKey(111)",
            "  static Multibound fromAncestor() {",
            "    return new Multibound();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public Map<Integer, Multibound> map() {",
            "      return ImmutableMap.<Integer, Multibound>of(",
            "          111, AncestorModule_FromAncestorFactory.proxyFromAncestor());",
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
  public void emptyMultibinds_map_frameworkInstance() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "Multibound");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.Map;",
            "",
            "@Module",
            "interface LeafModule {",
            "  @Multibinds",
            "  Map<Integer, Multibound> map();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Provider<Map<Integer, Multibound>> map();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.MapFactory;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Provider<Map<Integer, Multibound>> map() {",
            "    return MapFactory.<Integer, Multibound>emptyMapProvider();",
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
            "import dagger.multibindings.IntKey;",
            "import dagger.multibindings.IntoMap;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @IntoMap",
            "  @IntKey(111)",
            "  static Multibound fromAncestor() {",
            "    return new Multibound();",
            "  }",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.internal.DelegateFactory;",
            "import dagger.internal.MapFactory;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    private Provider<Map<Integer, Multibound>> mapOfIntegerAndMultiboundProvider =",
            "        new DelegateFactory<>()",
            "",
            "    protected LeafImpl() {}",
            "",
            "    protected void configureInitialization() {",
            "      initialize();",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize() {",
            "      DelegateFactory.setDelegate(",
            "          mapOfIntegerAndMultiboundProvider,",
            "          MapFactory.<Integer, Multibound>builder(1)",
            "              .put(111, AncestorModule_FromAncestorFactory.create())",
            "              .build());",
            "    }",
            "",
            "    @Override",
            "    public Provider<Map<Integer, Multibound>> map() {",
            "      return mapOfIntegerAndMultiboundProvider;",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "          .proxyProvideProvidedInAncestor();",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "            .proxyProvideProvidedInGrandAncestor();",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public InjectedInLeaf injectedInLeaf() {",
            "    return InjectedInLeaf_Factory.newInjectedInLeaf(getProvidedInAncestor());",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "          .proxyProvideProvidedInAncestor();",
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
    createAncillaryClasses(filesToCompile, "PrunedDependency");
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public InjectsPrunedDependency injectsPrunedDependency() {",
            "    return InjectsPrunedDependency_Factory.newInjectsPrunedDependency(",
            "        getPrunedDependency());",
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
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder rootModule(RootModule rootModule) {",
            "      Preconditions.checkNotNull(rootModule);",
            "      return this;",
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
            "          .proxyInjectsPrunedDependency();",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public InjectsPrunedDependency injectsPrunedDependency() {",
            "    return InjectsPrunedDependency_Factory.newInjectsPrunedDependency(",
            "        getPrunedDependency());",
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
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder rootModule(RootModule rootModule) {",
            "      Preconditions.checkNotNull(rootModule);",
            "      return this;",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    private LeafImpl() {}",
            "",
            "    @Override",
            "    public InjectsPrunedDependency injectsPrunedDependency() {",
            "      return RootModule_InjectsPrunedDependencyFactory",
            "          .proxyInjectsPrunedDependency();",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public InjectsPrunedDependency injectsPrunedDependency() {",
            "    return InjectsPrunedDependency_Factory.newInjectsPrunedDependency(",
            "        getPrunedDependency());",
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "          .proxyInjectsPrunedDependency();",
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
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
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
    createAncillaryClasses(filesToCompile, "Response", "ResponseDependency");
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
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf, CancellationListener {",
            "  private Producer<Set<Response>> responsesEntryPoint;",
            "",
            "  private ResponseProducerModule_ResponseFactory responseProducer;",
            "",
            "  private Producer<Set<Response>> setOfResponseProducer;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization(Builder builder) {",
            "    initialize(builder);",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.responseProducer =",
            "        ResponseProducerModule_ResponseFactory.create(",
            "            getProductionImplementationExecutorProvider(),",
            "            getProductionComponentMonitorProvider(),",
            "            getResponseDependencyProducer());",
            "    this.setOfResponseProducer =",
            "        SetProducer.<Response>builder(1, 0)",
            "            .addProducer(getResponseProducer()).build();",
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
            "  protected Producer getResponseProducer() {",
            "    return responseProducer;",
            "  }",
            "",
            "  protected Producer getSetOfResponseProducer() {",
            "    return setOfResponseProducer;",
            "  }",
            "",
            "  @Override",
            "  public void onProducerFutureCancelled(boolean mayInterruptIfRunning) {",
            "    Producers.cancel(getSetOfResponseProducer(), mayInterruptIfRunning);",
            "    Producers.cancel(getResponseProducer(), mayInterruptIfRunning);",
            "  }",
            "",
            "  public abstract static class Builder implements Leaf.Builder {}",
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
            "public final class DaggerRoot implements Root, CancellationListener {",
            "  private Provider<Executor> productionImplementationExecutorProvider;",
            "",
            "  private Provider<Root> rootProvider;",
            "",
            "  private Provider<ProductionComponentMonitor> monitorProvider;",
            "",
            "  private ResponseDependencyProducerModule_ResponseDependencyFactory",
            "      responseDependencyProducer;",
            "",
            "  private RootMultibindingModule_ResponseFactory responseProducer;",
            "",
            "  private DaggerRoot(Builder builder) {",
            "    initialize(builder);",
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
            "  private void initialize(final Builder builder) {",
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
            "      return new DaggerRoot(this);",
            "    }",
            "  }",
            "",
            "  private final class LeafBuilder extends DaggerLeaf.Builder {",
            "    @Override",
            "    public Leaf build() {",
            "      return new LeafImpl(this);",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf implements CancellationListener {",
            "    private Producer<Set<Response>> setOfResponseProducer = new DelegateProducer<>();",
            "",
            "    private LeafImpl(LeafBuilder builder) {",
            "      configureInitialization(builder);",
            "      initialize(builder);",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize(final LeafBuilder builder) {",
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
  public void producesMethodInstalledInLeafAndAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.producers.Producer;",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent(modules = InstalledInLeafAndAncestorModule.class)",
            "interface Leaf {",
            "  Producer<Object> producer();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.InstalledInLeafAndAncestorModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "",
            "@ProducerModule",
            "final class InstalledInLeafAndAncestorModule {",
            "  @Produces",
            "  static Object producer() {",
            "    return new Object();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.producers.Producer;",
            "import dagger.producers.internal.CancellationListener;",
            "import dagger.producers.internal.Producers;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.concurrent.Executor;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf, CancellationListener {",
            "  private Producer<Object> producerEntryPoint;",
            "",
            "  private InstalledInLeafAndAncestorModule_ProducerFactory producerProducer;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization() {",
            "    initialize();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.producerProducer =",
            "        InstalledInLeafAndAncestorModule_ProducerFactory.create(",
            "            getProductionImplementationExecutorProvider(),",
            "            getProductionComponentMonitorProvider());",
            "    this.producerEntryPoint = Producers.entryPointViewOf(getObjectProducer(), this);",
            "  }",
            "",
            "  @Override",
            "  public Producer<Object> producer() {",
            "    return producerEntryPoint;",
            "  }",
            "",
            "  protected abstract Provider<Executor>",
            "    getProductionImplementationExecutorProvider();",
            "",
            "  protected abstract Provider<ProductionComponentMonitor> ",
            "      getProductionComponentMonitorProvider();",
            "",
            "  protected Producer<Object> getObjectProducer() {",
            "    return producerProducer;",
            "  }",
            "",
            "  @Override",
            "  public void onProducerFutureCancelled(boolean mayInterruptIfRunning) {",
            "    Producers.cancel(getObjectProducer(), mayInterruptIfRunning);",
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
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent(modules = InstalledInLeafAndAncestorModule.class)",
            "interface Ancestor {",
            "  Leaf leaf();",
            "}"));
    JavaFileObject generatedAncestor =
        JavaFileObjects.forSourceLines(
            "test.DaggerAncestor",
            "package test;",
            "",
            "import dagger.producers.Producer;",
            "import dagger.producers.internal.CancellationListener;",
            "import dagger.producers.internal.Producers;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.concurrent.Executor;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor, CancellationListener {",
            "  private InstalledInLeafAndAncestorModule_ProducerFactory producerProducer;",
            "",
            "  protected DaggerAncestor() {}",
            "",
            "  protected void configureInitialization() {",
            "    initialize();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.producerProducer =",
            "        InstalledInLeafAndAncestorModule_ProducerFactory.create(",
            "            getProductionImplementationExecutorProvider(),",
            "            getProductionComponentMonitorProvider());",
            "  }",
            "",
            "  protected abstract Provider<Executor>",
            "    getProductionImplementationExecutorProvider();",
            "",
            "  protected abstract Provider<ProductionComponentMonitor>",
            "      getProductionComponentMonitorProvider();",
            "",
            "  protected Producer<Object> getObjectProducer() {",
            "    return producerProducer;",
            "  }",
            "",
            "  protected Producer<Object> getObjectProducer2() {",
            "    return getObjectProducer();",
            "  }",
            "",
            "  @Override",
            "  public void onProducerFutureCancelled(boolean mayInterruptIfRunning) {",
            "    Producers.cancel(getObjectProducer(), mayInterruptIfRunning);",
            "  }",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf ",
            "      implements CancellationListener {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    protected final Producer<Object> getObjectProducer() {",
            "      return DaggerAncestor.this.getObjectProducer();",
            "    }",
            "",
            "    @Override",
            "    public final Producer<Object> producer() {",
            "      return DaggerAncestor.this.getObjectProducer2();",
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
  public void lazyOfModifiableBinding() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "MissingInLeaf");
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
            "import dagger.internal.ProviderOfLazy;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
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
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
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
    createAncillaryClasses(
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
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private LeafModule_TestFactory testProvider;",
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
            "    return LeafModule_TestFactory.proxyTest(",
            "        getMissing(),",
            "        getMissingProvider(),",
            "        getProvidedInAncestor_InducesSetBinding());",
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
            "import dagger.internal.SetFactory;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    private LeafModule_UnresolvedSetBindingFactory unresolvedSetBindingProvider;",
            "",
            "    private Provider<Set<Object>> setOfObjectProvider;",
            "",
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
            "    private Object getObject() {",
            "      return LeafModule_UnresolvedSetBindingFactory.proxyUnresolvedSetBinding(",
            "          getMissing(), getMissingProvider());",
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
            "          AncestorModule_SetContributionFactory.proxySetContribution(), getObject());",
            "    }",
            "",
            "    @Override",
            "    protected final Object getProvidedInAncestor_InducesSetBinding() {",
            "      return AncestorModule_ProvidedInAncestorFactory.proxyProvidedInAncestor(",
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
    createAncillaryClasses(filesToCompile, "InducesDependenciesOnBuilderFields");
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
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private Integer bindsInstance;",
            "  private LeafModule leafModule;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization(Builder builder) {",
            "    this.bindsInstance = builder.bindsInstance;",
            "    this.leafModule = builder.leafModule;",
            "  }",
            "",
            "  @Override",
            "  public int bindsInstance() {",
            "    return bindsInstance;",
            "  }",
            "",
            "  @Override",
            "  public Object fromModule() {",
            "    return LeafModule_FromModuleFactory.proxyFromModule(leafModule());",
            "  }",
            "",
            "  protected LeafModule leafModule() {",
            "    return leafModule;",
            "  }",
            "",
            "  public abstract static class Builder implements Leaf.Builder {",
            "    public LeafModule leafModule;",
            "    public Integer bindsInstance;",
            "    public String inducedInSubclass;",
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
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    private String inducedInSubclass;",
            "",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    protected void configureInitialization(DaggerLeaf.Builder builder) {",
            "      this.inducedInSubclass = builder.inducedInSubclass;",
            "      super.configureInitialization(builder);",
            "    }",
            "",
            "    protected Set<String> getSetOfString() {",
            "      return ImmutableSet.<String>of(inducedInSubclass);",
            "    }",
            "",
            "    @Override",
            "    public final InducesDependenciesOnBuilderFields",
            "        inducesDependenciesOnBuilderFields() {",
            "      return MultibindingInducingModule_InduceFactory.proxyInduce(getSetOfString());",
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
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
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
            "        Preconditions.checkBuilderRequirement(leafModule, LeafModule.class);",
            "        Preconditions.checkBuilderRequirement(bindsInstance, Integer.class);",
            "        Preconditions.checkBuilderRequirement(inducedInSubclass, String.class);",
            "        return new LeafImpl(this);",
            "      }",
            "    }",
            "",
            "    protected final class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      private LeafModule leafModule;",
            "",
            "      private LeafImpl(LeafBuilder builder) {",
            "        this.leafModule = builder.leafModule;",
            "        configureInitialization(builder);",
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
    createAncillaryClasses(filesToCompile, "Used", "Unused");
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
            "    Leaf build();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private ModuleWithUsedBinding moduleWithUsedBinding;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization(Builder builder) {",
            "    this.moduleWithUsedBinding = builder.moduleWithUsedBinding;",
            "  }",
            "",
            "  @Override",
            "  public Used used() {",
            "    return ModuleWithUsedBinding_UsedFactory.proxyUsed(",
            "        moduleWithUsedBinding());",
            "  }",
            "",
            "  protected ModuleWithUsedBinding moduleWithUsedBinding() {",
            "    return moduleWithUsedBinding;",
            "  }",
            "",
            "  public abstract static class Builder implements Leaf.Builder {",
            "    public ModuleWithUsedBinding moduleWithUsedBinding;",
            "    public ModuleWithUnusedBinding moduleWithUnusedBinding;",
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
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
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
            "      return new LeafImpl(this);",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    private ModuleWithUsedBinding moduleWithUsedBinding;",
            "",
            "    private LeafImpl(LeafBuilder builder) {",
            "      this.moduleWithUsedBinding = builder.moduleWithUsedBinding;",
            "      configureInitialization(builder);",
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
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private RepeatedModule repeatedModule;",
            "",
            "  protected DaggerLeaf() {}",
            "",
            "  protected void configureInitialization(Builder builder) {",
            "    this.repeatedModule = builder.repeatedModule;",
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
            "    public RepeatedModule repeatedModule;",
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
            "public final class DaggerRoot implements Root {",
            "  private RepeatedModule repeatedModule;",
            "",
            "  private DaggerRoot(Builder builder) {",
            "    this.repeatedModule = builder.repeatedModule;",
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
            "  public static final class Builder {",
            "    private RepeatedModule repeatedModule;",
            "",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      if (repeatedModule == null) {",
            "        this.repeatedModule = new RepeatedModule();",
            "      }",
            "      return new DaggerRoot(this);",
            "    }",
            "",
            "    public Builder repeatedModule(RepeatedModule repeatedModule) {",
            "      this.repeatedModule = Preconditions.checkNotNull(repeatedModule);",
            "      return this;",
            "    }",
            "  }",
            "",
            "  private final class LeafBuilder extends DaggerLeaf.Builder {",
            "    @Override",
            "    public Leaf build() {",
            "      return new LeafImpl(this);",
            "    }",
            "",
            "    @Override",
            "    public LeafBuilder repeatedModule(RepeatedModule repeatedModule) {",
            "      throw new UnsupportedOperationException(",
            "        String.format(",
            "          \"%s cannot be set because it is inherited from the enclosing component\",",
            "          RepeatedModule.class.getCanonicalName()));",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    private LeafImpl(LeafBuilder builder) {",
            "      configureInitialization(builder);",
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
    createAncillaryClasses(filesToCompile, "MissingInLeaf");
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
            "    }",
            "  }",
            "",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    private LeafImpl() {}",
            "",
            "    @Override",
            "    public Object bindsWithMissingDependencyInLeaf() {",
            "      return MissingInLeafModule_BoundInRootFactory.proxyBoundInRoot();",
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
    createAncillaryClasses(filesToCompile, "MissingInLeaf");
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
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
            "          .proxyPruneInjectConstructor();",
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
    createAncillaryClasses(filesToCompile, "DependsOnModifiedProducerFromProvider");
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
            "import dagger.producers.Producer;",
            "import dagger.producers.internal.CancellationListener;",
            "import dagger.producers.internal.Producers;",
            "import dagger.producers.monitoring.ProductionComponentMonitor;",
            "import java.util.Set;",
            "import java.util.concurrent.Executor;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf, CancellationListener {",
            "  private Producer<DependsOnModifiedProducerFromProvider>",
            "      dependsOnModifiedProducerFromProviderEntryPoint;",
            "  private LeafModule_DependsOnModifiedFactory dependsOnModifiedProducer;",
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
            "        Producers.entryPointViewOf(",
            "            getDependsOnModifiedProducerFromProviderProducer(), this);",
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
            "  protected Producer getDependsOnModifiedProducerFromProviderProducer() {",
            "    return dependsOnModifiedProducer;",
            "  }",
            "",
            "  @Override",
            "  public void onProducerFutureCancelled(boolean mayInterruptIfRunning) {",
            "    Producers.cancel(",
            "      getDependsOnModifiedProducerFromProviderProducer(), mayInterruptIfRunning);",
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
            "public final class DaggerRoot implements Root {",
            "  private DaggerRoot(Builder builder) {}",
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
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Root build() {",
            "      return new DaggerRoot(this);",
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
            "                  leafProvider, getSetOfFactoryProvider())));",
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
            "        getSetOfFactoryProvider() {",
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
  public void bindsMissingDep_Multibindings() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.LeafModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "interface LeafModule {",
            "  @Binds",
            "  @IntoSet",
            "  CharSequence bindsMultibindingWithMissingDep(String string);",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = LeafModule.class)",
            "interface Leaf {",
            "  Set<CharSequence> set();",
            "}"));

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Set<CharSequence> set() {",
            "    return ImmutableSet.<CharSequence>of(getCharSequence());",
            "  }",
            "",
            // The expected output here is subtle: the Key of
            // LeafModule.bindsMultibindingWithMissingDep() is Set<CharSequence>, but the binding
            // method should only be returning an individual CharSequence. Otherwise the
            // ImmutableSet factory method above will fail.
            // TODO(b/117833324): It would be great to get this method name to match the binding
            // element name
            "  protected abstract CharSequence getCharSequence();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);
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
            "import foo.Thing;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public CharSequence inducesFoo() {",
            "    return LeafModule_DepOnFooThingFactory.proxyDepOnFooThing(getThing());",
            "  }",
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
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    private Runnable getRunnable() {",
            "      return LeafModule_DepOnBarThingFactory.proxyDepOnBarThing(getThing2());",
            "    }",
            "",
            "    protected abstract Thing getThing2();",
            "",
            "    protected Set<Runnable> getSetOfRunnable() {",
            "      return ImmutableSet.<Runnable>of(getRunnable());",
            "    }",
            "",
            "    @Override",
            "    public final WillInduceSetOfRunnable willInduceSetOfRunnable() {",
            "      return AncestorModule_InduceFactory.proxyInduce(getSetOfRunnable());",
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
    createAncillaryClasses(filesToCompile, "Inducer");
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerMaybeLeaf implements MaybeLeaf {",
            "  protected DaggerMaybeLeaf() {}",
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
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  protected abstract class MaybeLeafImpl extends DaggerMaybeLeaf {",
            "    protected MaybeLeafImpl() {}",
            "",
            "    private Object getObject() {",
            "      return InducedSubcomponentModule_InducedSetFactory.proxyInducedSet(",
            "          getInducedSubcomponentBuilder());",
            "    }",
            "",
            "    protected abstract Object getInducedSubcomponentBuilder();",
            "",
            "    protected Set<Object> getSetOfObject() {",
            "      return ImmutableSet.<Object>of(getObject());",
            "    }",
            "",
            "    @Override",
            "    public final Inducer inducer() {",
            "      return AncestorModule_InducerFactory.proxyInducer(getSetOfObject());",
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
    createAncillaryClasses(filesToCompile, "ProvidesMethodRootScoped");
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
            IMPORT_GENERATED_ANNOTATION,
            "",
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
            "public final class DaggerRoot implements Root {",
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
    createAncillaryClasses(filesToCompile, "Pruned");
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Modified modified() {",
            "    return new Modified(LeafModule_PrunedFactory.proxyPruned(leafModule()));",
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

    String exceptionText =
        " has been pruned from the final resolved binding graph. If this exception is thrown, it "
            + "is a cause of a Dagger bug - please report it!";
    JavaFileObject generatedRoot =
        JavaFileObjects.forSourceLines(
            "test.DaggerRoot",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerRoot implements Root {",
            "  protected final class LeafImpl extends DaggerLeaf {",
            "    @Override",
            "    public Modified modified() {",
            "      return RootModule_ModifiedFactory.proxyModified();",
            "    }",
            "",
            "    @Override",
            "    protected LeafModule leafModule() {",
            "      throw new UnsupportedOperationException(",
            "          LeafModule.class + \"" + exceptionText + "\");",
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
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  private Provider<A> aProvider;",
            "  private B_Factory bProvider;",
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

  private void createAncillaryClasses(
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

  private static Compilation compile(Iterable<JavaFileObject> files) {
    return daggerCompiler()
        .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
        .compile(files);
  }

  private static Compilation compileWithoutGuava(Iterable<JavaFileObject> files) {
    return daggerCompiler()
        .withOptions(
            AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts().append(CLASS_PATH_WITHOUT_GUAVA_OPTION))
        .compile(files);
  }
}
