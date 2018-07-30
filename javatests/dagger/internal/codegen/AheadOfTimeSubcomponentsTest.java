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
}
