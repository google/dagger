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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
            "",
            "    @Override",
            "    public MissingInLeaf missingFromComponentMethod() {",
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
            "    return new DependsOnMissingBinding(getMissingInLeaf());",
            "  }",
            "",
            "  public abstract MissingInLeaf getMissingInLeaf();",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
            "",
            "    @Override",
            "    public MissingInLeaf getMissingInLeaf() {",
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
            "  public abstract class AncestorImpl extends DaggerAncestor {",
            "    protected AncestorImpl() { super(); }",
            "",
            "    public abstract class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      protected LeafImpl() { super(); }",
            "",
            "      @Override",
            "      public MissingInLeaf getMissingInLeaf() {",
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
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface Sub {",
            "  String string();",
            "}");
    JavaFileObject module =
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
            "}");
    JavaFileObject generatedSubcomponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerSub",
            "package test;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerSub implements Sub {",
            "  private TestModule testModule;",
            "",
            "  protected DaggerSub() {",
            "    initialize();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.testModule = new TestModule();",
            "  }",
            "",
            "  @Override",
            "  public String string() {",
            "    return TestModule_ProvideStringFactory.proxyProvideString(testModule);",
            "  }",
            "}");
    Compilation compilation = compile(subcomponent, module);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSub")
        .hasSourceEquivalentTo(generatedSubcomponent);
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
            // "  Leaf leaf();", // TODO(b/72748365): enable this (and fix the bug that's causing
            // this to stack overflow
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
            "  protected DaggerLeaf(Builder builder) {}",
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
            "  public abstract class LeafBuilder extends DaggerLeaf.Builder {}",
            "",
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl(LeafBuilder builder) {",
            "      super(builder);",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
            "",
            "    @Override",
            "    public Optional<SatisfiedInAncestor> satisfiedInAncestor() {",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
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
            "  public abstract class AncestorImpl extends DaggerAncestor {",
            "    protected AncestorImpl() { super(); }",
            "",
            "    public abstract class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      protected LeafImpl() { super(); }",
            "",
            "      @Override",
            "      public Optional<SatisfiedInGrandAncestor> satisfiedInGrandAncestor() {",
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
            "  public RequiresOptionalSatisfiedInAncestor requiresOptionalSatisfiedInAncestor() {",
            "    return LeafModule_ProvideRequiresOptionalSatisfiedInAncestorFactory",
            "        .proxyProvideRequiresOptionalSatisfiedInAncestor(",
            "            getOptionalOfSatisfiedInAncestor());",
            "  }",
            "",
            "  public Optional<SatisfiedInAncestor> getOptionalOfSatisfiedInAncestor() {",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
            "",
            "    @Override",
            "    public Optional<SatisfiedInAncestor> getOptionalOfSatisfiedInAncestor() {",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
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
            "  public abstract class AncestorImpl extends DaggerAncestor {",
            "    protected AncestorImpl() { super(); }",
            "",
            "    public abstract class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      protected LeafImpl() { super(); }",
            "",
            "      @Override",
            "      public Optional<SatisfiedInGrandAncestor>",
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
  public void setMultibindings_satisfiedByDifferentAncestors() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(
        filesToCompile,
        "InGreatGrandchild",
        "InChild",
        "InGreatGrandchildAndChild",
        "InAllSubcomponents");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = GreatGrandchildModule.class)",
            "interface GreatGrandchild {",
            "  Set<InGreatGrandchild> contributionsInGreatGrandchildOnly();",
            "  Set<InChild> contributionsInChildOnly();",
            "  Set<InGreatGrandchildAndChild> contributionsInGreatGrandchildAndChild();",
            "  Set<InAllSubcomponents> contributionsAtAllLevels();",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class GreatGrandchildModule {",
            "  @Provides",
            "  @IntoSet",
            "  static InGreatGrandchild provideInGreatGrandchild() {",
            "    return new InGreatGrandchild();",
            "  }",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static InGreatGrandchildAndChild provideInGreatGrandchildAndChild() {",
            "    return new InGreatGrandchildAndChild();",
            "  }",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "    return new InAllSubcomponents();",
            "  }",
            "}"));

    JavaFileObject generatedGreatGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGreatGrandchild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGreatGrandchild implements GreatGrandchild {",
            "  protected DaggerGreatGrandchild() {}",
            "",
            "  @Override",
            "  public Set<InGreatGrandchild> contributionsInGreatGrandchildOnly() {",
            "    return ImmutableSet.<InGreatGrandchild>of(",
            "        GreatGrandchildModule_ProvideInGreatGrandchildFactory",
            "            .proxyProvideInGreatGrandchild());",
            "  }",
            "",
            "  @Override",
            "  public Set<InGreatGrandchildAndChild> contributionsInGreatGrandchildAndChild() {",
            "    return ImmutableSet.<InGreatGrandchildAndChild>of(",
            "        GreatGrandchildModule_ProvideInGreatGrandchildAndChildFactory",
            "            .proxyProvideInGreatGrandchildAndChild());",
            "  }",
            "",
            "  @Override",
            "  public Set<InAllSubcomponents> contributionsAtAllLevels() {",
            "    return ImmutableSet.<InAllSubcomponents>of(",
            "        GreatGrandchildModule_ProvideInAllSubcomponentsFactory",
            "            .proxyProvideInAllSubcomponents());",
            "  }",
            "}");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandchildModule.class)",
            "interface Grandchild {",
            "  GreatGrandchild greatGrandchild();",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GrandchildModule",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import java.util.Arrays;",
            "import java.util.Set;",
            "import java.util.HashSet;",
            "",
            "@Module",
            "class GrandchildModule {",
            "  @Provides",
            "  @ElementsIntoSet",
            "  static Set<InAllSubcomponents> provideInAllSubcomponents() {",
            "      return ImmutableSet.of(new InAllSubcomponents(), new InAllSubcomponents());",
            "  }",
            "}"));

    JavaFileObject generatedGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandchild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandchild implements Grandchild {",
            "  protected DaggerGrandchild() {}",
            "",
            "  public abstract class GreatGrandchildImpl extends DaggerGreatGrandchild {",
            "    protected GreatGrandchildImpl() {",
            "      super();",
            "    }",
            "",
            "    @Override",
            "    public Set<InAllSubcomponents> contributionsAtAllLevels() {",
            "      return ImmutableSet.<InAllSubcomponents>builderWithExpectedSize(2)",
            "          .addAll(GrandchildModule_ProvideInAllSubcomponentsFactory",
            "              .proxyProvideInAllSubcomponents())",
            "          .addAll(super.contributionsAtAllLevels())",
            "          .build();",
            "    }",
            "  }",
            "}");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Grandchild grandchild();",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Arrays;",
            "import java.util.HashSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides",
            "  @ElementsIntoSet",
            "  static Set<InChild> provideInChilds() {",
            "    return new HashSet<InChild>(Arrays.asList(new InChild(), new InChild()));",
            "  }",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static InGreatGrandchildAndChild provideInGreatGrandchildAndChild() {",
            "    return new InGreatGrandchildAndChild();",
            "  }",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "    return new InAllSubcomponents();",
            "  }",
            "}"));

    JavaFileObject generatedChild =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerChild implements Child {",
            "  protected DaggerChild() {}",
            "",
            "  public abstract class GrandchildImpl extends DaggerGrandchild {",
            "    protected GrandchildImpl() {",
            "      super();",
            "    }",
            "",
            "    public abstract class GreatGrandchildImpl extends",
            "        DaggerGrandchild.GreatGrandchildImpl {",
            "      protected GreatGrandchildImpl() {",
            "        super();",
            "      }",
            "",
            "      @Override",
            "      public Set<InChild> contributionsInChildOnly() {",
            "        return ImmutableSet.<InChild>copyOf(",
            "            ChildModule_ProvideInChildsFactory.proxyProvideInChilds());",
            "      }",
            "",
            "      @Override",
            "      public Set<InGreatGrandchildAndChild>",
            "          contributionsInGreatGrandchildAndChild() {",
            "        return ImmutableSet.<InGreatGrandchildAndChild>builderWithExpectedSize(2)",
            "            .add(ChildModule_ProvideInGreatGrandchildAndChildFactory",
            "                .proxyProvideInGreatGrandchildAndChild())",
            "            .addAll(super.contributionsInGreatGrandchildAndChild())",
            "            .build();",
            "      }",
            "",
            "      @Override",
            "      public Set<InAllSubcomponents> contributionsAtAllLevels() {",
            "        return ImmutableSet.<InAllSubcomponents>builderWithExpectedSize(3)",
            "            .add(ChildModule_ProvideInAllSubcomponentsFactory",
            "                .proxyProvideInAllSubcomponents())",
            "            .addAll(super.contributionsAtAllLevels())",
            "            .build();",
            "      }",
            "",
            "    }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(filesToCompile.build().toArray(new JavaFileObject[0]));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGreatGrandchild")
        .hasSourceEquivalentTo(generatedGreatGrandchild);
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGrandchild")
        .hasSourceEquivalentTo(generatedGrandchild);
    assertThat(compilation)
        .generatedSourceFile("test.DaggerChild")
        .hasSourceEquivalentTo(generatedChild);
  }

  @Test
  public void setMultibindings_methodDependencies() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(
        filesToCompile,
        "InAllSubcomponents",
        "RequiresInAllSubcomponents",
        "NoContributions",
        "RequiresNoContributions");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = GreatGrandchildModule.class)",
            "interface GreatGrandchild {",
            "  Set<InAllSubcomponents> contributionsAtAllLevels();",
            "  RequiresNoContributions requiresNonComponentMethodSet();",
            "  RequiresInAllSubcomponents requiresComponentMethodSet();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Builder module(GreatGrandchildModule module);",
            "",
            "    GreatGrandchild build();",
            "  }",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class GreatGrandchildModule {",
            "  @Provides",
            "  @IntoSet",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "    return new InAllSubcomponents();",
            "  }",
            "",
            "  @Provides",
            "  static RequiresNoContributions providesRequiresNonComponentMethodSet(",
            "      Set<NoContributions> noContributions) {",
            "    return new RequiresNoContributions();",
            "  }",
            "",
            "  @Provides",
            "  static RequiresInAllSubcomponents providesRequiresComponentMethodSet(",
            "      Set<InAllSubcomponents> inAllSubcomponents) {",
            "    return new RequiresInAllSubcomponents();",
            "  }",
            "}"));

    JavaFileObject generatedGreatGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGreatGrandchild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGreatGrandchild implements GreatGrandchild {",
            "  protected DaggerGreatGrandchild(Builder builder) {}",
            "",
            "  @Override",
            "  public Set<InAllSubcomponents> contributionsAtAllLevels() {",
            "    return ImmutableSet.<InAllSubcomponents>of(",
            "        GreatGrandchildModule_ProvideInAllSubcomponentsFactory",
            "            .proxyProvideInAllSubcomponents());",
            "  }",
            "",
            "  @Override",
            "  public RequiresNoContributions requiresNonComponentMethodSet() {",
            "    return GreatGrandchildModule_ProvidesRequiresNonComponentMethodSetFactory",
            "        .proxyProvidesRequiresNonComponentMethodSet(getSet());",
            "  }",
            "",
            "  @Override",
            "  public RequiresInAllSubcomponents requiresComponentMethodSet() {",
            "    return GreatGrandchildModule_ProvidesRequiresComponentMethodSetFactory",
            "        .proxyProvidesRequiresComponentMethodSet(contributionsAtAllLevels());",
            "  }",
            "",
            "  public abstract Set<NoContributions> getSet();",
            "",
            "  public abstract static class Builder implements GreatGrandchild.Builder {",
            "",
            "    @Override",
            "    public Builder module(GreatGrandchildModule module) {",
            "      return this;",
            "    }",
            "  }",
            "}");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandchildModule.class)",
            "interface Grandchild {",
            "  GreatGrandchild.Builder greatGrandchild();",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GrandchildModule",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.ElementsIntoSet;",
            "import java.util.Arrays;",
            "import java.util.Set;",
            "import java.util.HashSet;",
            "",
            "@Module",
            "class GrandchildModule {",
            "  @Provides",
            "  @ElementsIntoSet",
            "  static Set<InAllSubcomponents> provideInAllSubcomponents() {",
            "      return ImmutableSet.of(new InAllSubcomponents(), new InAllSubcomponents());",
            "  }",
            "}"));

    JavaFileObject generatedGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandchild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandchild implements Grandchild {",
            "  protected DaggerGrandchild() {}",
            "",
            "  public abstract class GreatGrandchildBuilder extends",
            "      DaggerGreatGrandchild.Builder {",
            "    @Override",
            "    public GreatGrandchildBuilder module(GreatGrandchildModule module) {",
            "      return this;",
            "    }",
            "  }",
            "",
            "  public abstract class GreatGrandchildImpl extends DaggerGreatGrandchild {",
            "    protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "      super(builder);",
            "    }",
            "",
            "    @Override",
            "    public Set<InAllSubcomponents> contributionsAtAllLevels() {",
            "      return ImmutableSet.<InAllSubcomponents>builderWithExpectedSize(2)",
            "          .addAll(GrandchildModule_ProvideInAllSubcomponentsFactory",
            "              .proxyProvideInAllSubcomponents())",
            "          .addAll(super.contributionsAtAllLevels())",
            "          .build();",
            "    }",
            "",
            "  }",
            "}");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Grandchild grandchild();",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class ChildModule {",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "    return new InAllSubcomponents();",
            "  }",
            "}"));

    JavaFileObject generatedChild =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerChild implements Child {",
            "  protected DaggerChild() {}",
            "",
            "  public abstract class GrandchildImpl extends DaggerGrandchild {",
            "    protected GrandchildImpl() {",
            "      super();",
            "    }",
            "",
            "    public abstract class GreatGrandchildBuilder",
            "        extends DaggerGrandchild.GreatGrandchildBuilder {",
            "      @Override",
            "      public GreatGrandchildBuilder module(GreatGrandchildModule module) {",
            "        return this;",
            "      }",
            "    }",
            "",
            "    public abstract class GreatGrandchildImpl extends",
            "        DaggerGrandchild.GreatGrandchildImpl {",
            "      protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "        super(builder);",
            "      }",
            "",
            "      @Override",
            "      public Set<InAllSubcomponents> contributionsAtAllLevels() {",
            "        return ImmutableSet.<InAllSubcomponents>builderWithExpectedSize(3)",
            "            .add(ChildModule_ProvideInAllSubcomponentsFactory",
            "                .proxyProvideInAllSubcomponents())",
            "            .addAll(super.contributionsAtAllLevels())",
            "            .build();",
            "      }",
            "",
            "    }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(filesToCompile.build().toArray(new JavaFileObject[0]));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGreatGrandchild")
        .hasSourceEquivalentTo(generatedGreatGrandchild);
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGrandchild")
        .hasSourceEquivalentTo(generatedGrandchild);
    assertThat(compilation)
        .generatedSourceFile("test.DaggerChild")
        .hasSourceEquivalentTo(generatedChild);
  }

  @Test
  public void setMultibindings_typeChanges() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "InGrandchild");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface GreatGrandchild {",
            "  InGrandchild missingWithSetDependency();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    GreatGrandchild build();",
            "  }",
            "}"));

    JavaFileObject generatedGreatGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGreatGrandchild",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGreatGrandchild implements GreatGrandchild {",
            "  protected DaggerGreatGrandchild(Builder builder) {}",
            "",
            "  public abstract static class Builder implements GreatGrandchild.Builder { }",
            "}");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandchildModule.class)",
            "interface Grandchild {",
            "  GreatGrandchild.Builder greatGrandchild();",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Set;",
            "",
            "@Module",
            "class GrandchildModule {",
            "",
            "  @Provides",
            "  static InGrandchild provideInGrandchild(Set<Long> longs) {",
            "    return new InGrandchild();",
            "  }",
            "}"));

    JavaFileObject generatedGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandchild",
            "package test;",
            "",
            "import java.util.Set;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandchild implements Grandchild {",
            "  protected DaggerGrandchild() {}",
            "",
            "  private InGrandchild getInGrandchild() {",
            "    return GrandchildModule_ProvideInGrandchildFactory",
            "        .proxyProvideInGrandchild(getSet());",
            "  }",
            "",
            "  public abstract Set<Long> getSet();",
            "",
            "  public abstract class GreatGrandchildBuilder extends",
            "      DaggerGreatGrandchild.Builder { }",
            "",
            "  public abstract class GreatGrandchildImpl extends DaggerGreatGrandchild {",
            "    protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "      super(builder);",
            "    }",
            "",
            "    @Override",
            "    public InGrandchild missingWithSetDependency() {",
            "      return DaggerGrandchild.this.getInGrandchild();",
            "    }",
            "",
            "  }",
            "}");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Grandchild grandchild();",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class ChildModule {",
            "",
            "  @Provides",
            "  @IntoSet",
            "  static Long provideLong() { return 0L; }",
            "}"));

    JavaFileObject generatedChild =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableSet;",
            "import java.util.Set;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerChild implements Child {",
            "  protected DaggerChild() {}",
            "",
            "  public abstract class GrandchildImpl extends DaggerGrandchild {",
            "    protected GrandchildImpl() {",
            "      super();",
            "    }",
            "",
            "    @Override",
            "    public Set<Long> getSet() {",
            "      return ImmutableSet.<Long>of(",
            "          ChildModule_ProvideLongFactory.proxyProvideLong());",
            "    }",
            "",
            "    public abstract class GreatGrandchildBuilder",
            "        extends DaggerGrandchild.GreatGrandchildBuilder { }",
            "",
            "    public abstract class GreatGrandchildImpl extends",
            "        DaggerGrandchild.GreatGrandchildImpl {",
            "      protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "        super(builder);",
            "      }",
            "    }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(filesToCompile.build().toArray(new JavaFileObject[0]));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGreatGrandchild")
        .hasSourceEquivalentTo(generatedGreatGrandchild);
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGrandchild")
        .hasSourceEquivalentTo(generatedGrandchild);
    assertThat(compilation)
        .generatedSourceFile("test.DaggerChild")
        .hasSourceEquivalentTo(generatedChild);
  }

  @Test
  public void mapMultibindings_satisfiedByDifferentAncestors() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(
        filesToCompile,
        "InGreatGrandchild",
        "InChild",
        "InGreatGrandchildAndChild",
        "InAllSubcomponents");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "",
            "@Subcomponent(modules = GreatGrandchildModule.class)",
            "interface GreatGrandchild {",
            "  Map<String, InGreatGrandchild> contributionsInGreatGrandchildOnly();",
            "  Map<String, InChild> contributionsInChildOnly();",
            "  Map<String, InGreatGrandchildAndChild> contributionsInGreatGrandchildAndChild();",
            "  Map<String, InAllSubcomponents> contributionsAtAllLevels();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Builder module(GreatGrandchildModule module);",
            "",
            "    GreatGrandchild build();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class GreatGrandchildModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"great-grandchild\")",
            "  static InGreatGrandchild provideInGreatGrandchild() {",
            "    return new InGreatGrandchild();",
            "  }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"great-grandchild\")",
            "  static InGreatGrandchildAndChild provideInGreatGrandchildAndChild() {",
            "    return new InGreatGrandchildAndChild();",
            "  }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"great-grandchild\")",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "    return new InAllSubcomponents();",
            "  }",
            "}"));
    JavaFileObject generatedGreatGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGreatGrandchild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGreatGrandchild implements GreatGrandchild {",
            "  protected DaggerGreatGrandchild(Builder builder) {}",
            "",
            "  @Override",
            "  public Map<String, InGreatGrandchild> contributionsInGreatGrandchildOnly() {",
            "    return ImmutableMap.<String, InGreatGrandchild>of(",
            "        \"great-grandchild\",",
            "        GreatGrandchildModule_ProvideInGreatGrandchildFactory",
            "            .proxyProvideInGreatGrandchild());",
            "  }",
            "",
            "  @Override",
            "  public Map<String, InGreatGrandchildAndChild>",
            "      contributionsInGreatGrandchildAndChild() {",
            "    return ImmutableMap.<String, InGreatGrandchildAndChild>of(",
            "        \"great-grandchild\",",
            "        GreatGrandchildModule_ProvideInGreatGrandchildAndChildFactory",
            "            .proxyProvideInGreatGrandchildAndChild());",
            "  }",
            "",
            "  @Override",
            "  public Map<String, InAllSubcomponents> contributionsAtAllLevels() {",
            "    return ImmutableMap.<String, InAllSubcomponents>of(",
            "        \"great-grandchild\",",
            "        GreatGrandchildModule_ProvideInAllSubcomponentsFactory",
            "            .proxyProvideInAllSubcomponents());",
            "  }",
            "",
            "  public abstract static class Builder implements GreatGrandchild.Builder {",
            "",
            "    @Override",
            "    public Builder module(GreatGrandchildModule module) {",
            "      return this;",
            "    }",
            "  }",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGreatGrandchild")
        .hasSourceEquivalentTo(generatedGreatGrandchild);

    JavaFileObject nonGuavaGeneratedGreatGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGreatGrandchild",
            "package test;",
            "",
            "import java.util.Collections;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGreatGrandchild implements GreatGrandchild {",
            "  protected DaggerGreatGrandchild(Builder builder) {}",
            "",
            "  @Override",
            "  public Map<String, InGreatGrandchild> contributionsInGreatGrandchildOnly() {",
            "    return Collections.<String, InGreatGrandchild>singletonMap(",
            "        \"great-grandchild\",",
            "        GreatGrandchildModule_ProvideInGreatGrandchildFactory",
            "            .proxyProvideInGreatGrandchild());",
            "  }",
            "",
            "  @Override",
            "  public Map<String, InGreatGrandchildAndChild>",
            "      contributionsInGreatGrandchildAndChild() {",
            "    return Collections.<String, InGreatGrandchildAndChild>singletonMap(",
            "        \"great-grandchild\",",
            "        GreatGrandchildModule_ProvideInGreatGrandchildAndChildFactory",
            "            .proxyProvideInGreatGrandchildAndChild());",
            "  }",
            "",
            "  @Override",
            "  public Map<String, InAllSubcomponents> contributionsAtAllLevels() {",
            "    return Collections.<String, InAllSubcomponents>singletonMap(",
            "        \"great-grandchild\",",
            "        GreatGrandchildModule_ProvideInAllSubcomponentsFactory",
            "            .proxyProvideInAllSubcomponents());",
            "  }",
            "",
            "  public abstract static class Builder implements GreatGrandchild.Builder {",
            "",
            "    @Override",
            "    public Builder module(GreatGrandchildModule module) {",
            "      return this;",
            "    }",
            "  }",
            "}");
    compilation = compileWithoutGuava(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGreatGrandchild")
        .hasSourceEquivalentTo(nonGuavaGeneratedGreatGrandchild);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandchildModule.class)",
            "interface Grandchild {",
            "  GreatGrandchild.Builder greatGrandchild();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.GrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "",
            "@Module",
            "class GrandchildModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"grandchild\")",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "    return new InAllSubcomponents();",
            "  }",
            "}"));
    JavaFileObject generatedGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandchild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandchild implements Grandchild {",
            "  protected DaggerGrandchild() {}",
            "",
            "  public abstract class GreatGrandchildBuilder extends",
            "      DaggerGreatGrandchild.Builder {",
            "    @Override",
            "    public GreatGrandchildBuilder module(GreatGrandchildModule module) {",
            "      return this;",
            "    }",
            "  }",
            "",
            "  public abstract class GreatGrandchildImpl extends DaggerGreatGrandchild {",
            "    protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "      super(builder);",
            "    }",
            "",
            "    @Override",
            "    public Map<String, InAllSubcomponents> contributionsAtAllLevels() {",
            "      return ImmutableMap.<String, InAllSubcomponents>builderWithExpectedSize(2)",
            "          .put(\"grandchild\", GrandchildModule_ProvideInAllSubcomponentsFactory",
            "              .proxyProvideInAllSubcomponents())",
            "          .putAll(super.contributionsAtAllLevels())",
            "          .build();",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGrandchild")
        .hasSourceEquivalentTo(generatedGrandchild);

    JavaFileObject nonGuavaGeneratedGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandchild",
            "package test;",
            "",
            "import dagger.internal.MapBuilder;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandchild implements Grandchild {",
            "  protected DaggerGrandchild() {}",
            "",
            "  public abstract class GreatGrandchildBuilder",
            "      extends DaggerGreatGrandchild.Builder {",
            "",
            "    @Override",
            "    public GreatGrandchildBuilder module(GreatGrandchildModule module) {",
            "      return this;",
            "    }",
            "  }",
            "",
            "  public abstract class GreatGrandchildImpl extends DaggerGreatGrandchild {",
            "    protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "      super(builder);",
            "    }",
            "",
            "    @Override",
            "    public Map<String, InAllSubcomponents> contributionsAtAllLevels() {",
            "      return MapBuilder.<String, InAllSubcomponents>newMapBuilder(2)",
            "          .put(",
            "              \"grandchild\",",
            "              GrandchildModule_ProvideInAllSubcomponentsFactory",
            "                  .proxyProvideInAllSubcomponents())",
            "          .putAll(super.contributionsAtAllLevels())",
            "          .build();",
            "    }",
            "  }",
            "}");
    compilation = compileWithoutGuava(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGrandchild")
        .hasSourceEquivalentTo(nonGuavaGeneratedGrandchild);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Grandchild grandchild();",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "",
            "@Module",
            "class ChildModule {",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"child\")",
            "  static InChild provideInChild() {",
            "    return new InChild();",
            "  }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"child\")",
            "  static InGreatGrandchildAndChild provideInGreatGrandchildAndChild() {",
            "    return new InGreatGrandchildAndChild();",
            "  }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"child\")",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "    return new InAllSubcomponents();",
            "  }",
            "}"));
    JavaFileObject generatedChild =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerChild implements Child {",
            "  protected DaggerChild() {}",
            "",
            "  public abstract class GrandchildImpl extends DaggerGrandchild {",
            "    protected GrandchildImpl() {",
            "      super();",
            "    }",
            "",
            "    public abstract class GreatGrandchildBuilder",
            "        extends DaggerGrandchild.GreatGrandchildBuilder {",
            "      @Override",
            "      public GreatGrandchildBuilder module(GreatGrandchildModule module) {",
            "        return this;",
            "      }",
            "    }",
            "",
            "    public abstract class GreatGrandchildImpl extends",
            "        DaggerGrandchild.GreatGrandchildImpl {",
            "      protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "        super(builder);",
            "      }",
            "",
            "      @Override",
            "      public Map<String, InChild> contributionsInChildOnly() {",
            "        return ImmutableMap.<String, InChild>of(",
            "            \"child\", ChildModule_ProvideInChildFactory.proxyProvideInChild());",
            "      }",
            "",
            "      @Override",
            "      public Map<String, InGreatGrandchildAndChild>",
            "          contributionsInGreatGrandchildAndChild() {",
            "        return",
            "            ImmutableMap.<String, InGreatGrandchildAndChild>builderWithExpectedSize(",
            "                2)",
            "            .put(\"child\",",
            "                ChildModule_ProvideInGreatGrandchildAndChildFactory",
            "                    .proxyProvideInGreatGrandchildAndChild())",
            "            .putAll(super.contributionsInGreatGrandchildAndChild())",
            "            .build();",
            "      }",
            "",
            "      @Override",
            "      public Map<String, InAllSubcomponents> contributionsAtAllLevels() {",
            "        return ImmutableMap.<String, InAllSubcomponents>builderWithExpectedSize(3)",
            "            .put(\"child\",",
            "                ChildModule_ProvideInAllSubcomponentsFactory",
            "                    .proxyProvideInAllSubcomponents())",
            "            .putAll(super.contributionsAtAllLevels())",
            "            .build();",
            "      }",
            "    }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerChild")
        .hasSourceEquivalentTo(generatedChild);

    JavaFileObject nonGuavaGeneratedChild =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
            "",
            "import dagger.internal.MapBuilder;",
            "import java.util.Collections;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerChild implements Child {",
            "  protected DaggerChild() {}",
            "",
            "  public abstract class GrandchildImpl extends DaggerGrandchild {",
            "    protected GrandchildImpl() {",
            "      super();",
            "    }",
            "",
            "    public abstract class GreatGrandchildBuilder",
            "        extends DaggerGrandchild.GreatGrandchildBuilder {",
            "",
            "      @Override",
            "      public GreatGrandchildBuilder module(GreatGrandchildModule module) {",
            "        return this;",
            "      }",
            "    }",
            "",
            "    public abstract class GreatGrandchildImpl",
            "        extends DaggerGrandchild.GreatGrandchildImpl {",
            "      protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "        super(builder);",
            "      }",
            "",
            "      @Override",
            "      public Map<String, InChild> contributionsInChildOnly() {",
            "        return Collections.<String, InChild>singletonMap(",
            "            \"child\", ChildModule_ProvideInChildFactory.proxyProvideInChild());",
            "      }",
            "",
            "      @Override",
            "      public Map<String, InGreatGrandchildAndChild>",
            "          contributionsInGreatGrandchildAndChild() {",
            "        return MapBuilder.<String, InGreatGrandchildAndChild>newMapBuilder(2)",
            "            .put(",
            "                \"child\",",
            "                ChildModule_ProvideInGreatGrandchildAndChildFactory",
            "                    .proxyProvideInGreatGrandchildAndChild())",
            "            .putAll(super.contributionsInGreatGrandchildAndChild())",
            "            .build();",
            "      }",
            "",
            "      @Override",
            "      public Map<String, InAllSubcomponents> contributionsAtAllLevels() {",
            "        return MapBuilder.<String, InAllSubcomponents>newMapBuilder(3)",
            "            .put(",
            "                \"child\",",
            "                ChildModule_ProvideInAllSubcomponentsFactory",
            "                   .proxyProvideInAllSubcomponents())",
            "            .putAll(super.contributionsAtAllLevels())",
            "            .build();",
            "      }",
            "    }",
            "  }",
            "}");
    compilation = compileWithoutGuava(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerChild")
        .hasSourceEquivalentTo(nonGuavaGeneratedChild);
  }

  @Test
  public void mapMultibindings_methodDependencies() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(
        filesToCompile,
        "InAllSubcomponents",
        "RequiresInAllSubcomponentsMap",
        "Unsatisfied",
        "RequiresUnsatisfiedMap");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "",
            "@Subcomponent(modules = GreatGrandchildModule.class)",
            "interface GreatGrandchild {",
            "  Map<String, InAllSubcomponents> contributionsAtAllLevels();",
            "  RequiresUnsatisfiedMap requiresNonComponentMethodMap();",
            "  RequiresInAllSubcomponentsMap requiresComponentMethodMap();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Builder module(GreatGrandchildModule module);",
            "",
            "    GreatGrandchild build();",
            "  }",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.Map;",
            "",
            "@Module",
            "class GreatGrandchildModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"great-grandchild\")",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "    return new InAllSubcomponents();",
            "  }",
            "",
            "  @Provides",
            "  static RequiresUnsatisfiedMap providesRequiresNonComponentMethodMap(",
            "      Map<String, Unsatisfied> unsatisfiedMap) {",
            "    return new RequiresUnsatisfiedMap();",
            "  }",
            "",
            "  @Provides",
            "  static RequiresInAllSubcomponentsMap providesRequiresComponentMethodMap(",
            "      Map<String, InAllSubcomponents> inAllSubcomponentsMap) {",
            "    return new RequiresInAllSubcomponentsMap();",
            "  }",
            "}"));

    JavaFileObject generatedGreatGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGreatGrandchild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGreatGrandchild implements GreatGrandchild {",
            "  protected DaggerGreatGrandchild(Builder builder) {}",
            "",
            "  @Override",
            "  public Map<String, InAllSubcomponents> contributionsAtAllLevels() {",
            "    return ImmutableMap.<String, InAllSubcomponents>of(",
            "        \"great-grandchild\",",
            "        GreatGrandchildModule_ProvideInAllSubcomponentsFactory",
            "            .proxyProvideInAllSubcomponents());",
            "  }",
            "",
            "  @Override",
            "  public RequiresUnsatisfiedMap requiresNonComponentMethodMap() {",
            "    return GreatGrandchildModule_ProvidesRequiresNonComponentMethodMapFactory",
            "        .proxyProvidesRequiresNonComponentMethodMap(getMap());",
            "  }",
            "",
            "  @Override",
            "  public RequiresInAllSubcomponentsMap requiresComponentMethodMap() {",
            "    return GreatGrandchildModule_ProvidesRequiresComponentMethodMapFactory",
            "        .proxyProvidesRequiresComponentMethodMap(contributionsAtAllLevels());",
            "  }",
            "",
            "  public abstract Map<String, Unsatisfied> getMap();",
            "",
            "  public abstract static class Builder implements GreatGrandchild.Builder {",
            "",
            "    @Override",
            "    public Builder module(GreatGrandchildModule module) {",
            "      return this;",
            "    }",
            "  }",
            "}");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandchildModule.class)",
            "interface Grandchild {",
            "  GreatGrandchild.Builder greatGrandchild();",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "",
            "@Module",
            "class GrandchildModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"grandchild\")",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "    return new InAllSubcomponents();",
            "  }",
            "}"));

    JavaFileObject generatedGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandchild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandchild implements Grandchild {",
            "  protected DaggerGrandchild() {}",
            "",
            "  public abstract class GreatGrandchildBuilder extends",
            "      DaggerGreatGrandchild.Builder {",
            "    @Override",
            "    public GreatGrandchildBuilder module(GreatGrandchildModule module) {",
            "      return this;",
            "    }",
            "  }",
            "",
            "  public abstract class GreatGrandchildImpl extends DaggerGreatGrandchild {",
            "    protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "      super(builder);",
            "    }",
            "",
            "    @Override",
            "    public Map<String, InAllSubcomponents> contributionsAtAllLevels() {",
            "      return ImmutableMap.<String, InAllSubcomponents>builderWithExpectedSize(2)",
            "          .put(\"grandchild\",",
            "              GrandchildModule_ProvideInAllSubcomponentsFactory",
            "                  .proxyProvideInAllSubcomponents())",
            "          .putAll(super.contributionsAtAllLevels())",
            "          .build();",
            "    }",
            "  }",
            "}");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Grandchild grandchild();",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides",
            "  @IntoMap",
            "  @StringKey(\"child\")",
            "  static InAllSubcomponents provideInAllSubcomponents() {",
            "    return new InAllSubcomponents();",
            "  }",
            "}"));

    JavaFileObject generatedChild =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
            "",
            "import com.google.common.collect.ImmutableMap;",
            "import java.util.Map;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerChild implements Child {",
            "  protected DaggerChild() {}",
            "",
            "  public abstract class GrandchildImpl extends DaggerGrandchild {",
            "    protected GrandchildImpl() {",
            "      super();",
            "    }",
            "",
            "    public abstract class GreatGrandchildBuilder",
            "        extends DaggerGrandchild.GreatGrandchildBuilder {",
            "      @Override",
            "      public GreatGrandchildBuilder module(GreatGrandchildModule module) {",
            "        return this;",
            "      }",
            "    }",
            "",
            "    public abstract class GreatGrandchildImpl extends",
            "        DaggerGrandchild.GreatGrandchildImpl {",
            "      protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "        super(builder);",
            "      }",
            "",
            "      @Override",
            "      public Map<String, InAllSubcomponents> contributionsAtAllLevels() {",
            "        return ImmutableMap.<String, InAllSubcomponents>builderWithExpectedSize(3)",
            "            .put(\"child\",",
            "                ChildModule_ProvideInAllSubcomponentsFactory",
            "                    .proxyProvideInAllSubcomponents())",
            "            .putAll(super.contributionsAtAllLevels())",
            "            .build();",
            "      }",
            "    }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(filesToCompile.build().toArray(new JavaFileObject[0]));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGreatGrandchild")
        .hasSourceEquivalentTo(generatedGreatGrandchild);
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGrandchild")
        .hasSourceEquivalentTo(generatedGrandchild);
    assertThat(compilation)
        .generatedSourceFile("test.DaggerChild")
        .hasSourceEquivalentTo(generatedChild);
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
            "  public abstract String getString();",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
            "",
            "    @Override",
            "    public ProvidedInAncestor injectedInLeaf() {",
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
            "  public abstract String getString();",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
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
            "  public abstract class AncestorImpl extends DaggerAncestor {",
            "    protected AncestorImpl() { super(); }",
            "",
            "    public abstract class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      protected LeafImpl() { super(); }",
            "",
            "      @Override",
            "      public ProvidedInGrandAncestor injectedInLeaf() {",
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
            "    return new InjectedInLeaf(getProvidedInAncestor());",
            "  }",
            "",
            "  public abstract String getString();",
            "",
            "  public ProvidedInAncestor getProvidedInAncestor() {",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {",
            "      super();",
            "    }",
            "",
            "    @Override",
            "    public ProvidedInAncestor getProvidedInAncestor() {",
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
  public void differentPackages() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.sub.Leaf",
            "package test.sub;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "public interface Leaf {",
            "  @Subcomponent.Builder",
            "  public interface Builder {",
            "    Leaf build();",
            "  }",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.sub.DaggerLeaf",
            "package test.sub;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf(Builder builder) {}",
            "",
            "  public abstract static class Builder implements Leaf.Builder {}",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.sub.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Ancestor",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import test.sub.Leaf;",
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
            "import test.sub.DaggerLeaf;",
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  public abstract class LeafBuilder extends DaggerLeaf.Builder {}",
            "",
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl(LeafBuilder builder) {",
            "      super(builder);",
            "    }",
            "  }",
            "}");
    compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerAncestor")
        .hasSourceEquivalentTo(generatedAncestor);
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

  private static Compilation compile(JavaFileObject... files) {
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
