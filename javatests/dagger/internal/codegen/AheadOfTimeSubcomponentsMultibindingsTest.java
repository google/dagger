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
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.CLASS_PATH_WITHOUT_GUAVA_OPTION;
import static dagger.internal.codegen.Compilers.compilerWithOptions;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.GENERATION_OPTIONS_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AheadOfTimeSubcomponentsMultibindingsTest {
  @Test
  public void setMultibindings_contributionsInLeaf() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "InLeaf");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Set<InLeaf> contributionsInLeaf() {",
            "    return ImmutableSet.<InLeaf>of(",
            "        LeafModule_ProvideInLeafFactory.provideInLeaf());",
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
    createSimplePackagePrivateClasses(filesToCompile, "InAncestor");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public abstract Set<InAncestor> contributionsInAncestor();",
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
            "    @Override",
            "    public Set<InAncestor> contributionsInAncestor() {",
            "      return ImmutableSet.<InAncestor>copyOf(",
            "          AncestorModule_ProvideInAncestorsFactory.provideInAncestors());",
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
    createSimplePackagePrivateClasses(filesToCompile, "InEachSubcomponent");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Set<InEachSubcomponent> contributionsInEachSubcomponent() {",
            "    return ImmutableSet.<InEachSubcomponent>of(",
            "        LeafModule_ProvideInLeafFactory.provideInLeaf(),",
            "        LeafModule_ProvideAnotherInLeafFactory.provideAnotherInLeaf());",
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
            "    @Override",
            "    public Set<InEachSubcomponent> contributionsInEachSubcomponent() {",
            "      return ImmutableSet.<InEachSubcomponent>builderWithExpectedSize(3)",
            "          .addAll(AncestorModule_ProvideInAncestorFactory.provideInAncestor())",
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
    createSimplePackagePrivateClasses(filesToCompile, "InLeafAndGrandAncestor");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Set<InLeafAndGrandAncestor> contributionsInLeafAndGrandAncestor() {",
            "    return ImmutableSet.<InLeafAndGrandAncestor>of(",
            "        LeafModule_ProvideInLeafFactory.provideInLeaf(),",
            "        LeafModule_ProvideAnotherInLeafFactory.provideAnotherInLeaf());",
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
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
            "              .provideInGrandAncestor())",
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
    createSimplePackagePrivateClasses(
        filesToCompile, "InAllSubcomponents", "RequresInAllSubcomponentsSet");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public RequresInAllSubcomponentsSet requiresNonComponentMethod() {",
            "    return LeafModule_ProvidesRequresInAllSubcomponentsSetFactory",
            "        .providesRequresInAllSubcomponentsSet(getSetOfInAllSubcomponents());",
            "  }",
            "",
            "  protected Set getSetOfInAllSubcomponents() {",
            "    return ImmutableSet.<InAllSubcomponents>of(",
            "        LeafModule_ProvideInAllSubcomponentsFactory",
            "            .provideInAllSubcomponents());",
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
            "    @Override",
            "    protected Set getSetOfInAllSubcomponents() {",
            "      return ImmutableSet.<InAllSubcomponents>builderWithExpectedSize(2)",
            "          .add(AncestorModule_ProvideInAllSubcomponentsFactory",
            "              .provideInAllSubcomponents())",
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
    createSimplePackagePrivateClasses(filesToCompile, "InAncestor", "RequiresInAncestorSet");
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
            "import dagger.internal.GenerationOptions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public abstract RequiresInAncestorSet missingWithSetDependency();",
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerAncestor implements Ancestor {",
            "  protected DaggerAncestor() {}",
            "",
            "  private RequiresInAncestorSet getRequiresInAncestorSet() {",
            "    return AncestorModule_ProvideRequiresInAncestorSetFactory",
            "        .provideRequiresInAncestorSet(getSetOfInAncestor());",
            "  }",
            "",
            "  protected Set getSetOfInAncestor() {",
            "    return ImmutableSet.<InAncestor>of(",
            "        AncestorModule_ProvideInAncestorFactory.provideInAncestor());",
            "  }",
            "",
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public final RequiresInAncestorSet missingWithSetDependency() {",
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
  public void setMultibinding_requestedAsInstanceInLeaf_requestedAsFrameworkInstanceFromAncestor() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Set<Multibound> instance() {",
            "    return ImmutableSet.<Multibound>of(",
            "        LeafModule_ContributionFactory.contribution());",
            "  }",
            "",
            "  @Override",
            "  public abstract MissingInLeaf_WillDependOnFrameworkInstance",
            "      willDependOnFrameworkInstance();",
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
            "      return AncestorModule_ProvidedInAncestorFactory.providedInAncestor(",
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
  public void missingMultibindingInLeaf_onlyContributionsInAncestor_notReModifiedInRoot() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "import javax.inject.Provider;",
            "",
            "@Subcomponent",
            "interface Leaf {",
            "  Set<Object> set();",
            "}"));
    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public abstract Set<Object> set();",
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
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class AncestorModule {",
            "  @Provides",
            "  @IntoSet",
            "  static Object onlyContribution() {",
            "    return new Object();",
            "  }",
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
            "  protected abstract class LeafImpl extends DaggerLeaf {",
            "    protected LeafImpl() {}",
            "",
            "    @Override",
            "    public Set<Object> set() {",
            "      return ImmutableSet.<Object>of(",
            "          AncestorModule_OnlyContributionFactory.onlyContribution());",
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
            // This tests a regression case where Dagger used to reimplement Leaf.set(), even though
            // there were no new contributions, because the state change from missing -> 
            // multibinding wasn't properly recorded
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
  public void setMultibindings_contributionsInLeafAndAncestor_frameworkInstances() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "InEachSubcomponent");
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
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.SetFactory;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
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
    createSimplePackagePrivateClasses(filesToCompile, "InLeaf");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Map<String, InLeaf> contributionsInLeaf() {",
            "    return ImmutableMap.<String, InLeaf>of(",
            "        \"leafmodule\",",
            "        LeafModule_ProvideInLeafFactory.provideInLeaf());",
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
    createSimplePackagePrivateClasses(filesToCompile, "InAncestor");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public abstract Map<String, InAncestor> contributionsInAncestor();",
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Map;",
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
            "    public Map<String, InAncestor> contributionsInAncestor() {",
            "      return ImmutableMap.<String, InAncestor>of(\"ancestormodule\",",
            "          AncestorModule_ProvideInAncestorFactory.provideInAncestor());",
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
    createSimplePackagePrivateClasses(filesToCompile, "InEachSubcomponent");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Map<String, InEachSubcomponent> contributionsInEachSubcomponent() {",
            "    return ImmutableMap.<String, InEachSubcomponent>of(",
            "        \"leafmodule\", LeafModule_ProvideInLeafFactory.provideInLeaf());",
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Map;",
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
            "    public Map<String, InEachSubcomponent> contributionsInEachSubcomponent() {",
            "      return ImmutableMap.<String, InEachSubcomponent>builderWithExpectedSize(2)",
            "          .put(\"ancestormodule\",",
            "              AncestorModule_ProvideInAncestorFactory.provideInAncestor())",
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
    createSimplePackagePrivateClasses(filesToCompile, "InEachSubcomponent");
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
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.MapFactory;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
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
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.MapFactory;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
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
    createSimplePackagePrivateClasses(filesToCompile, "InLeafAndGrandAncestor");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Map<String, InLeafAndGrandAncestor> contributionsInLeafAndGrandAncestor() {",
            "    return ImmutableMap.<String, InLeafAndGrandAncestor>of(",
            "        \"leafmodule\", LeafModule_ProvideInLeafFactory.provideInLeaf());",
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Map;",
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
            "      public Map<String, InLeafAndGrandAncestor>",
            "          contributionsInLeafAndGrandAncestor() {",
            "        return",
            "            ImmutableMap.<String, InLeafAndGrandAncestor>builderWithExpectedSize(2)",
            "                .put(\"grandancestormodule\",",
            "                    GrandAncestorModule_ProvideInGrandAncestorFactory",
            "                        .provideInGrandAncestor())",
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
    createSimplePackagePrivateClasses(filesToCompile, "InEachSubcomponent");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Collections;",
            "import java.util.Map",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Map<String, InEachSubcomponent> contributionsInEachSubcomponent() {",
            "    return Collections.<String, InEachSubcomponent>singletonMap(",
            "        \"leafmodule\", LeafModule_ProvideInLeafFactory.provideInLeaf());",
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
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.MapBuilder;",
            "import java.util.Map;",
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
            "    public Map<String, InEachSubcomponent> contributionsInEachSubcomponent() {",
            "      return MapBuilder.<String, InEachSubcomponent>newMapBuilder(2)",
            "          .put(\"ancestormodule\",",
            "              AncestorModule_ProvideInAncestorFactory.provideInAncestor())",
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
    createSimplePackagePrivateClasses(
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Map<Integer, Multibound> instance() {",
            "    return ImmutableMap.<Integer, Multibound>of(",
            "        111, LeafModule_ContributionFactory.contribution());",
            "  }",
            "",
            "  @Override",
            "  public abstract MissingInLeaf_WillDependOnFrameworkInstance",
            "      willDependOnFrameworkInstance();",
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
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.MapFactory;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
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
            "      return AncestorModule_ProvidedInAncestorFactory.providedInAncestor(",
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
    createSimplePackagePrivateClasses(filesToCompile, "Multibound");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
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
            "    @Override",
            "    public Set<Multibound> set() {",
            "      return ImmutableSet.<Multibound>of(",
            "          AncestorModule_FromAncestorFactory.fromAncestor());",
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
    createSimplePackagePrivateClasses(filesToCompile, "Multibound");
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
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.SetFactory;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
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
    createSimplePackagePrivateClasses(filesToCompile, "Multibound");
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Map;",
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
            "    public Map<Integer, Multibound> map() {",
            "      return ImmutableMap.<Integer, Multibound>of(",
            "          111, AncestorModule_FromAncestorFactory.fromAncestor());",
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
    createSimplePackagePrivateClasses(filesToCompile, "Multibound");
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
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.MapFactory;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
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
            "import dagger.internal.GenerationOptions;",
            "import dagger.internal.MapFactory;",
            "import java.util.Map;",
            IMPORT_GENERATED_ANNOTATION,
            "import javax.inject.Provider;",
            "",
            GENERATION_OPTIONS_ANNOTATION,
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
            "import dagger.internal.GenerationOptions;",
            "import java.util.Set;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATION_OPTIONS_ANNOTATION,
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  @Override",
            "  public Set<CharSequence> set() {",
            "    return ImmutableSet.<CharSequence>of(getBindsMultibindingWithMissingDep());",
            "  }",
            "",
            // The expected output here is subtle: the Key of
            // LeafModule.bindsMultibindingWithMissingDep() is Set<CharSequence>, but the binding
            // method should only be returning an individual CharSequence. Otherwise the
            // ImmutableSet factory method above will fail.
            "  protected abstract CharSequence getBindsMultibindingWithMissingDep();",
            "}");
    Compilation compilation = compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .hasSourceEquivalentTo(generatedLeaf);
  }

  @Test
  public void multibindingsAndFastInit() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createSimplePackagePrivateClasses(filesToCompile, "PackagePrivate");
    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.MultibindingModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntKey;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "interface MultibindingModule {",
            "  @Provides",
            "  @IntoSet",
            "  @LeafScope",
            "  static PackagePrivate setContribution() {",
            "    return new PackagePrivate();",
            "  }",
            "",
            "  @Provides",
            "  @IntoMap",
            "  @IntKey(1)",
            "  @LeafScope",
            "  static PackagePrivate mapContribution() {",
            "    return new PackagePrivate();",
            "  }",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.LeafScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface LeafScope {}"),
        JavaFileObjects.forSourceLines(
            "test.UsesMultibindings",
            "package test;",
            "",
            "import java.util.Map;",
            "import java.util.Set;",
            "import javax.inject.Inject;",
            "",
            "class UsesMultibindings {",
            "  @Inject",
            "  UsesMultibindings(Set<PackagePrivate> set, Map<Integer, PackagePrivate> map) {}",
            "}"),
        JavaFileObjects.forSourceLines(
            "test.Leaf",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "@LeafScope",
            "@Subcomponent(modules = MultibindingModule.class)",
            "interface Leaf {",
            "  UsesMultibindings entryPoint();",
            "}"));

    Compilation compilation =
        compilerWithOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE, FAST_INIT_MODE)
            .compile(filesToCompile.build());
    assertThat(compilation).succeededWithoutWarnings();

    JavaFileObject generatedLeaf =
        JavaFileObjects.forSourceLines(
            "test.DaggerLeaf",
            "package test;",
            "",
            "@GenerationOptions(fastInit = true)",
            GENERATED_ANNOTATION,
            "public abstract class DaggerLeaf implements Leaf {",
            "  protected DaggerLeaf() {}",
            "",
            "  private PackagePrivate getSetContribution() {",
            "    Object local = setContribution;",
            "    if (local instanceof MemoizedSentinel) {",
            "      synchronized (local) {",
            "        local = setContribution;",
            "        if (local instanceof MemoizedSentinel) {",
            "          local = MultibindingModule_SetContributionFactory.setContribution();",
            "          setContribution = DoubleCheck.reentrantCheck(setContribution, local);",
            "        }",
            "      }",
            "    }",
            "    return (PackagePrivate) local;",
            "  }",
            "",
            "  private PackagePrivate getMapContribution() {",
            "    Object local = mapContribution;",
            "    if (local instanceof MemoizedSentinel) {",
            "      synchronized (local) {",
            "        local = mapContribution;",
            "        if (local instanceof MemoizedSentinel) {",
            "          local = MultibindingModule_MapContributionFactory.mapContribution();",
            "          mapContribution = DoubleCheck.reentrantCheck(mapContribution, local);",
            "        }",
            "      }",
            "    }",
            "    return (PackagePrivate) local;",
            "  }",
            "",
            "  @Override",
            "  public UsesMultibindings entryPoint() {",
            "    return new UsesMultibindings(",
            "        getSetOfPackagePrivate(), getMapOfIntegerAndPackagePrivate());",
            "  }",
            "",
            "  protected Set getSetOfPackagePrivate() {",
            "    return ImmutableSet.<PackagePrivate>of(getSetContribution());",
            "  }",
            "",
            "  protected Map getMapOfIntegerAndPackagePrivate() {",
            "    return ImmutableMap.<Integer, PackagePrivate>of(1, getMapContribution());",
            "  }",
            "}");
    assertThat(compilation)
        .generatedSourceFile("test.DaggerLeaf")
        .containsElementsIn(generatedLeaf);
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

  private static Compilation compile(Iterable<JavaFileObject> files) {
    return compilerWithOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE).compile(files);
  }

  private static Compilation compileWithoutGuava(Iterable<JavaFileObject> files) {
    return daggerCompiler()
        .withOptions(
            AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts().append(CLASS_PATH_WITHOUT_GUAVA_OPTION))
        .compile(files);
  }
}
