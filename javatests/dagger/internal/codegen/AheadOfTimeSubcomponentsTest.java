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

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AheadOfTimeSubcomponentsTest {
  @Test
  public void simpleSubcomponent() {
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
  public void subcomponent_MissingBinding() {
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  String string();",
            "}");

    JavaFileObject generatedSubcomponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerChild",
            "package test;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public abstract class DaggerChild implements Child {",
            "  protected DaggerChild() {}",
            "",
            "  @Override",
            "  public String string() {",
            "    return null;",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(subcomponent);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerChild")
        .hasSourceEquivalentTo(generatedSubcomponent);
  }

  @Test
  public void subcomponent_BuilderAndGeneratedInstanceBinding() {
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
            "  @Override",
            "  public Grandchild.Builder grandchild() {",
            "    return null;",
            "  }",
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
  public void simpleDeepComponentHierarchy() {
    JavaFileObject greatGrandchild =
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GreatGrandchildModule.class)",
            "interface GreatGrandchild {",
            "  Integer i();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Builder module(GreatGrandchildModule module);",
            "",
            "    GreatGrandchild build();",
            "  }",
            "}");

    JavaFileObject greatGrandchildModule =
        JavaFileObjects.forSourceLines(
            "test.GreatGrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class GreatGrandchildModule {",
            "  @Provides static Integer provideInteger() { return 0; }",
            "}");

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
            "  @Override",
            "  public Integer i() {",
            "    return GreatGrandchildModule_ProvideIntegerFactory.proxyProvideInteger();",
            "  }",
            "",
            "  protected abstract static class Builder implements GreatGrandchild.Builder {",
            "",
            "    @Override",
            "    public Builder module(GreatGrandchildModule module) {",
            "      return this;",
            "    }",
            "  }",
            "}");

    JavaFileObject grandchild =
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Grandchild {",
            "  GreatGrandchild.Builder greatGrandchild();",
            "}");

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
            "  @Override",
            "  public GreatGrandchild.Builder greatGrandchild() {",
            "    return null;",
            "  }",
            "",
            "  protected abstract class GreatGrandchildBuilder extends",
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
            "  Grandchild grandchild();",
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
            "  public abstract class GrandchildImpl extends DaggerGrandchild {",
            "    protected GrandchildImpl() {",
            "      super();",
            "    }",
            "",
            "    protected abstract class GreatGrandchildBuilder",
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
            "    }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(AHEAD_OF_TIME_SUBCOMPONENTS_MODE.javacopts())
            .compile(child, grandchild, greatGrandchild, greatGrandchildModule);
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
}
