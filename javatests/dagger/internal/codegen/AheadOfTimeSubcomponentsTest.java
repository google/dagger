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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {",
            "      super();",
            "    }",
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
            "  public Set<InAllSubcomponents> getSetOfInAllSubcomponents() {",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
            "",
            "    @Override",
            "    public Set<InAllSubcomponents> getSetOfInAllSubcomponents() {",
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
            "  private RequiresInAncestorSet getRequiresInAncestorSet() {",
            "    return AncestorModule_ProvideRequiresInAncestorSetFactory",
            "        .proxyProvideRequiresInAncestorSet(getSetOfInAncestor());",
            "  }",
            "",
            "  public Set<InAncestor> getSetOfInAncestor() {",
            "    return ImmutableSet.<InAncestor>of(",
            "        AncestorModule_ProvideInAncestorFactory.proxyProvideInAncestor());",
            "  }",
            "",
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
            "",
            "    @Override",
            "    public RequiresInAncestorSet missingWithSetDependency() {",
            "      return DaggerAncestor.this.getRequiresInAncestorSet();",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() { super(); }",
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
            "  public abstract class AncestorImpl extends DaggerAncestor {",
            "    protected AncestorImpl() { super(); }",
            "",
            "    public abstract class LeafImpl extends DaggerAncestor.LeafImpl {",
            "      protected LeafImpl() { super(); }",
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
            "  public abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {",
            "      super();",
            "    }",
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
