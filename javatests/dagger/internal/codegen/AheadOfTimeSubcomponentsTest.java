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
  public void missingBindings() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(
        filesToCompile,
        "Foo",
        "Bar",
        "RequiresBar",
        "Baz",
        "Qux",
        "RequiresQux",
        "RequiresRequiresQux");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GreatGrandchildModule.class)",
            "interface GreatGrandchild {",
            "  RequiresBar requiresComponentMethodMissingBinding();",
            "  RequiresRequiresQux requiresNonComponentMethodMissingBinding();",
            "  Foo satisfiedByGrandchild();",
            "  Bar satisfiedByChild();",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class GreatGrandchildModule {",
            "  @Provides static RequiresBar provideRequiresBar(Bar bar) {",
            "    return new RequiresBar();",
            "  }",
            "",
            "  @Provides static RequiresRequiresQux provideRequiresRequiresQux(RequiresQux qux) {",
            "    return new RequiresRequiresQux();",
            "  }",
            "}"));

    JavaFileObject generatedGreatGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGreatGrandchild",
            "package test;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGreatGrandchild implements GreatGrandchild {",
            "  protected DaggerGreatGrandchild() {}",
            "",
            "  @Override",
            "  public RequiresBar requiresComponentMethodMissingBinding() {",
            "    return GreatGrandchildModule_ProvideRequiresBarFactory.proxyProvideRequiresBar(",
            "        satisfiedByChild());",
            "  }",
            "",
            "  @Override",
            "  public RequiresRequiresQux requiresNonComponentMethodMissingBinding() {",
            "    return GreatGrandchildModule_ProvideRequiresRequiresQuxFactory",
            "        .proxyProvideRequiresRequiresQux(getRequiresQux());",
            "  }",
            "",
            "  public abstract RequiresQux getRequiresQux();",
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
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class GrandchildModule {",
            "  @Provides static Foo provideFoo() { return new Foo(); }",
            "",
            "  @Provides static RequiresQux provideRequiresQux(Qux qux) {",
            "    return new RequiresQux();",
            "  }",
            "}"));

    JavaFileObject generatedGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandchild",
            "package test;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandchild implements Grandchild {",
            "  protected DaggerGrandchild() {}",
            "",
            "  private RequiresQux getRequiresQux() {",
            "    return GrandchildModule_ProvideRequiresQuxFactory.proxyProvideRequiresQux(",
            "        getQux());",
            "  }",
            "",
            "  public abstract Qux getQux();",
            "",
            "  public abstract class GreatGrandchildImpl extends DaggerGreatGrandchild {",
            "    protected GreatGrandchildImpl() {",
            "      super();",
            "    }",
            "",
            "    @Override",
            "    public RequiresQux getRequiresQux() {",
            "      return DaggerGrandchild.this.getRequiresQux();",
            "    }",
            "",
            "    @Override",
            "    public Foo satisfiedByGrandchild() {",
            "      return GrandchildModule_ProvideFooFactory.proxyProvideFoo();",
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
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides static Bar provideBar() { return new Bar(); }",
            "  @Provides static Qux provideQux() { return new Qux(); }",
            "}"));

    JavaFileObject generatedChild =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
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
            "    public Qux getQux() {",
            "      return ChildModule_ProvideQuxFactory.proxyProvideQux();",
            "    }",
            "",
            "    public abstract class GreatGrandchildImpl extends",
            "        DaggerGrandchild.GreatGrandchildImpl {",
            "      protected GreatGrandchildImpl() {",
            "        super();",
            "      }",
            "",
            "      @Override",
            "      public Bar satisfiedByChild() {",
            "        return ChildModule_ProvideBarFactory.proxyProvideBar();",
            "      }",
            "    }",
            "  }",
            "}");

    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(filesToCompile.build());
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
  public void moduleInstanceDependency() {
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface Child {",
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
            "test.DaggerChild",
            "package test;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerChild implements Child {",
            "  private TestModule testModule;",
            "",
            "  protected DaggerChild() {",
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
    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(subcomponent, module);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerChild")
        .hasSourceEquivalentTo(generatedSubcomponent);
  }

  @Test
  public void generatedInstanceBinding() {
    JavaFileObject grandchild =
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandchildModule.class)",
            "interface Grandchild {",
            "  Integer i();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Builder module(GrandchildModule module);",
            "",
            "    Grandchild build();",
            "  }",
            "}");

    JavaFileObject grandchildModule =
        JavaFileObjects.forSourceLines(
            "test.GrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class GrandchildModule {",
            "  @Provides static Integer provideInteger() { return 0; }",
            "}");

    JavaFileObject generatedGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandchild",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandchild implements Grandchild {",
            "  protected DaggerGrandchild(Builder builder) {}",
            "",
            "  @Override",
            "  public Integer i() {",
            "    return GrandchildModule_ProvideIntegerFactory.proxyProvideInteger();",
            "  }",
            "",
            "  protected abstract static class Builder implements Grandchild.Builder {",
            "",
            "    @Override",
            "    public Builder module(GrandchildModule module) {",
            "      return this;",
            "    }",
            "  }",
            "}");

    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  Grandchild.Builder grandchild();",
            "}");

    JavaFileObject generatedChild =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerChild implements Child {",
            "  protected DaggerChild() {}",
            "",
            "  protected abstract class GrandchildBuilder extends DaggerGrandchild.Builder {",
            "    @Override",
            "    public GrandchildBuilder module(GrandchildModule module) {",
            "      return this;",
            "    }",
            "  }",
            "",
            "  public abstract class GrandchildImpl extends DaggerGrandchild {",
            "    protected GrandchildImpl(GrandchildBuilder builder) {",
            "      super(builder);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(child, grandchild, grandchildModule);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerGrandchild")
        .hasSourceEquivalentTo(generatedGrandchild);
    assertThat(compilation)
        .generatedSourceFile("test.DaggerChild")
        .hasSourceEquivalentTo(generatedChild);
  }

  @Test
  public void optionalBindings_satisfiedByDifferentAncestors() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "Baz", "Qux", "Flob", "Thud");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Optional;",
            "",
            "@Subcomponent(modules = GreatGrandchildModule.class)",
            "interface GreatGrandchild {",
            "  Optional<Baz> unsatisfied();",
            "  Optional<Qux> satisfiedByGreatGrandchild();",
            "  Optional<Flob> satisfiedByGrandchild();",
            "  Optional<Thud> satisfiedByChild();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    GreatGrandchild build();",
            "  }",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchildModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "abstract class GreatGrandchildModule {",
            "  @BindsOptionalOf abstract Baz optionalBaz();",
            "  @BindsOptionalOf abstract Qux optionalQux();",
            "  @Provides static Qux provideQux() { return new Qux(); }",
            "  @BindsOptionalOf abstract Flob optionalFlob();",
            "  @BindsOptionalOf abstract Thud optionalThud();",
            "}"));

    JavaFileObject generatedGreatGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGreatGrandchild",
            "package test;",
            "",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGreatGrandchild implements GreatGrandchild {",
            "  protected DaggerGreatGrandchild(Builder builder) {}",
            "",
            "  @Override",
            "  public Optional<Baz> unsatisfied() {",
            "    return Optional.<Baz>empty();",
            "  }",
            "",
            "  @Override",
            "  public Optional<Qux> satisfiedByGreatGrandchild() {",
            "    return Optional.of(GreatGrandchildModule_ProvideQuxFactory.proxyProvideQux());",
            "  }",
            "",
            "  @Override",
            "  public Optional<Flob> satisfiedByGrandchild() {",
            "    return Optional.<Flob>empty();",
            "  }",
            "",
            "  @Override",
            "  public Optional<Thud> satisfiedByChild() {",
            "    return Optional.<Thud>empty();",
            "  }",
            "",
            "  protected abstract static class Builder implements GreatGrandchild.Builder {}",
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
            "",
            "@Module",
            "abstract class GrandchildModule {",
            "  @Provides static Flob provideFlob() { return new Flob(); }",
            "}"));

    JavaFileObject generatedGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandchild",
            "package test;",
            "",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandchild implements Grandchild {",
            "  protected DaggerGrandchild() {}",
            "",
            "  protected abstract class GreatGrandchildBuilder",
            "      extends DaggerGreatGrandchild.Builder {}",
            "",
            "  public abstract class GreatGrandchildImpl extends DaggerGreatGrandchild {",
            "    protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "      super(builder);",
            "    }",
            "",
            "    @Override",
            "    public Optional<Flob> satisfiedByGrandchild() {",
            "      return Optional.of(",
            "          GrandchildModule_ProvideFlobFactory.proxyProvideFlob());",
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
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides static Thud provideThud() { return new Thud(); }",
            "}"));

    JavaFileObject generatedChild =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
            "",
            "import java.util.Optional;",
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
            "    protected abstract class GreatGrandchildBuilder",
            "        extends DaggerGrandchild.GreatGrandchildBuilder {}",
            "",
            "    public abstract class GreatGrandchildImpl",
            "        extends DaggerGrandchild.GreatGrandchildImpl {",
            "      protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "        super(builder);",
            "      }",
            "",
            "      @Override",
            "      public Optional<Thud> satisfiedByChild() {",
            "        return Optional.of(ChildModule_ProvideThudFactory.proxyProvideThud());",
            "      }",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(filesToCompile.build());
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
  public void optionalBindings_methodDependencies() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(
        filesToCompile,
        "Bar",
        "NeedsOptionalBar",
        "AlsoNeedsOptionalBar",
        "Baz",
        "NeedsOptionalBaz",
        "AlsoNeedsOptionalBaz");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Optional;",
            "",
            "@Subcomponent(modules = GreatGrandchildModule.class)",
            "interface GreatGrandchild {",
            "  NeedsOptionalBaz needsOptionalBaz();",
            "  Optional<Baz> componentMethod();",
            "  NeedsOptionalBar needsOptionalBar();",
            "  AlsoNeedsOptionalBar alsoNeedsOptionalBar();",
            "  AlsoNeedsOptionalBaz alsoNeedsOptionalBaz();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    GreatGrandchild build();",
            "  }",
            "}"));

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchildModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "abstract class GreatGrandchildModule {",
            "  @Provides static NeedsOptionalBar needsOptionalBar(Optional<Bar> optionalBar) {",
            "    return new NeedsOptionalBar();",
            "  }",
            "  @Provides static AlsoNeedsOptionalBar alsoNeedsOptionalBar(",
            "      Optional<Bar> optionalBar) {",
            "    return new AlsoNeedsOptionalBar();",
            "  }",
            "  @Provides static NeedsOptionalBaz needsOptionalBaz(",
            "      Optional<Baz> optionalBaz) {",
            "    return new NeedsOptionalBaz();",
            "  }",
            "  @Provides static AlsoNeedsOptionalBaz alsoNeedsOptionalBaz(",
            "      Optional<Baz> optionalBaz) {",
            "    return new AlsoNeedsOptionalBaz();",
            "  }",
            "  @BindsOptionalOf abstract Baz optionalBaz();",
            "  @BindsOptionalOf abstract Bar optionalBar();",
            "}"));

    JavaFileObject generatedGreatGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGreatGrandchild",
            "package test;",
            "",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGreatGrandchild implements GreatGrandchild {",
            "  protected DaggerGreatGrandchild(Builder builder) {}",
            "",
            "  @Override",
            "  public NeedsOptionalBaz needsOptionalBaz() {",
            "    return GreatGrandchildModule_NeedsOptionalBazFactory.proxyNeedsOptionalBaz(",
            "        componentMethod());",
            "  }",
            "",
            "  @Override",
            "  public Optional<Baz> componentMethod() {",
            "    return Optional.<Baz>empty();",
            "  }",
            "",
            "  @Override",
            "  public NeedsOptionalBar needsOptionalBar() {",
            "    return GreatGrandchildModule_NeedsOptionalBarFactory.proxyNeedsOptionalBar(",
            "        getOptionalOfBar());",
            "  }",
            "",
            "  @Override",
            "  public AlsoNeedsOptionalBar alsoNeedsOptionalBar() {",
            "    return",
            "        GreatGrandchildModule_AlsoNeedsOptionalBarFactory.proxyAlsoNeedsOptionalBar(",
            "        getOptionalOfBar());",
            "  }",
            "",
            "  @Override",
            "  public AlsoNeedsOptionalBaz alsoNeedsOptionalBaz() {",
            "    return GreatGrandchildModule_AlsoNeedsOptionalBazFactory",
            "        .proxyAlsoNeedsOptionalBaz(componentMethod());",
            "  }",
            "",
            "  public Optional<Bar> getOptionalOfBar() {",
            "    return Optional.<Bar>empty();",
            "  }",
            "",
            "  protected abstract static class Builder implements GreatGrandchild.Builder {}",
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
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class GrandchildModule {",
            "  @Provides static Baz provideBaz() { return new Baz(); }",
            "}"));

    JavaFileObject generatedGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandchild",
            "package test;",
            "",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandchild implements Grandchild {",
            "  protected DaggerGrandchild() {}",
            "",
            "  protected abstract class GreatGrandchildBuilder",
            "      extends DaggerGreatGrandchild.Builder {}",
            "",
            "  public abstract class GreatGrandchildImpl extends DaggerGreatGrandchild {",
            "    protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "      super(builder);",
            "    }",
            "",
            "    @Override",
            "    public Optional<Baz> componentMethod() {",
            "      return Optional.of(GrandchildModule_ProvideBazFactory.proxyProvideBaz());",
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
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides static Bar provideBar() { return new Bar(); }",
            "}"));

    JavaFileObject generatedChild =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
            "",
            "import java.util.Optional;",
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
            "    protected abstract class GreatGrandchildBuilder",
            "        extends DaggerGrandchild.GreatGrandchildBuilder {}",
            "",
            "    public abstract class GreatGrandchildImpl",
            "        extends DaggerGrandchild.GreatGrandchildImpl {",
            "      protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "        super(builder);",
            "      }",
            "",
            "      @Override",
            "      public Optional<Bar> getOptionalOfBar() {",
            "        return Optional.of(ChildModule_ProvideBarFactory.proxyProvideBar());",
            "      }",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(filesToCompile.build());
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
  public void optionalBindings_typeChanges() {
    ImmutableList.Builder<JavaFileObject> filesToCompile = ImmutableList.builder();
    createAncillaryClasses(filesToCompile, "Wobble");

    filesToCompile.add(
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Optional;",
            "",
            "@Subcomponent",
            "interface GreatGrandchild {",
            "  Optional<Wobble> satisfiedByChildAndBoundInGrandchild();",
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
            "  protected abstract static class Builder implements GreatGrandchild.Builder {}",
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
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "abstract class GrandchildModule {",
            "  @BindsOptionalOf abstract Wobble optionalWobble();",
            "}"));

    JavaFileObject generatedGrandchild =
        JavaFileObjects.forSourceLines(
            "test.DaggerGrandchild",
            "package test;",
            "",
            "import java.util.Optional;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerGrandchild implements Grandchild {",
            "  protected DaggerGrandchild() {}",
            "",
            "  protected abstract class GreatGrandchildBuilder",
            "      extends DaggerGreatGrandchild.Builder {}",
            "",
            "  public abstract class GreatGrandchildImpl extends DaggerGreatGrandchild {",
            "    protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "      super(builder);",
            "    }",
            "",
            "    @Override",
            "    public Optional<Wobble> satisfiedByChildAndBoundInGrandchild() {",
            "      return Optional.<Wobble>empty();",
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
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides static Wobble provideWobble() { return new Wobble(); }",
            "}"));

    JavaFileObject generatedChild =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
            "",
            "import java.util.Optional;",
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
            "    protected abstract class GreatGrandchildBuilder",
            "        extends DaggerGrandchild.GreatGrandchildBuilder {}",
            "",
            "    public abstract class GreatGrandchildImpl",
            "        extends DaggerGrandchild.GreatGrandchildImpl {",
            "      protected GreatGrandchildImpl(GreatGrandchildBuilder builder) {",
            "        super(builder);",
            "      }",
            "",
            "      @Override",
            "      public Optional<Wobble> satisfiedByChildAndBoundInGrandchild() {",
            "        return Optional.of(ChildModule_ProvideWobbleFactory.proxyProvideWobble());",
            "      }",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(filesToCompile.build());
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
}
