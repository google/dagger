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
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.TestUtils.message;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ReleasableReferenceManagerValidationTest {
  @Test
  public void missingReleasableReferenceManager() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@CanReleaseReferences",
            "@BadMetadata",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject otherScope =
        JavaFileObjects.forSourceLines(
            "test.OtherScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface OtherScope {}");
    JavaFileObject yetAnotherScope =
        JavaFileObjects.forSourceLines(
            "test.YetAnotherScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface YetAnotherScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");
    JavaFileObject badMetadata =
        JavaFileObjects.forSourceLines(
            "test.BadMetadata", // force one-string-per-line format
            "package test;",
            "",
            "@interface BadMetadata {}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponents",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "",
            "interface TestComponents {",
            "  @TestScope",
            "  @YetAnotherScope",
            "  @Component",
            "  interface WrongScopeComponent {",
            "    @ForReleasableReferences(OtherScope.class)",
            "    ReleasableReferenceManager otherManager();",
            "  }",
            "",
            "  @TestScope",
            "  @YetAnotherScope",
            "  @Component",
            "  interface WrongMetadataComponent {",
            "    @ForReleasableReferences(TestScope.class)",
            "    TypedReleasableReferenceManager<TestMetadata> wrongMetadata();",
            "  }",
            "",
            "  @TestScope",
            "  @YetAnotherScope",
            "  @Component",
            "  interface BadMetadataComponent {",
            "    @ForReleasableReferences(TestScope.class)",
            "    TypedReleasableReferenceManager<BadMetadata> badManager();",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .compile(testScope, otherScope, yetAnotherScope, testMetadata, badMetadata, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "There is no binding for "
                + "@dagger.releasablereferences.ForReleasableReferences(test.OtherScope.class) "
                + "dagger.releasablereferences.ReleasableReferenceManager "
                + "because no component in test.TestComponents.WrongScopeComponent's "
                + "component hierarchy is annotated with @test.OtherScope. "
                + "The available reference-releasing scopes are "
                + "[@test.TestScope, @test.YetAnotherScope].")
        .inFile(component)
        .onLineContaining("interface WrongScopeComponent");
    assertThat(compilation)
        .hadErrorContaining(
            "There is no binding for "
                + "@dagger.releasablereferences.ForReleasableReferences(test.TestScope.class) "
                + "dagger.releasablereferences.TypedReleasableReferenceManager<test.TestMetadata> "
                + "because test.TestScope is not annotated with @test.TestMetadata")
        .inFile(component)
        .onLineContaining("interface WrongMetadataComponent");
    assertThat(compilation)
        .hadErrorContaining(
            "There is no binding for "
                + "@dagger.releasablereferences.ForReleasableReferences(test.TestScope.class) "
                + "dagger.releasablereferences.TypedReleasableReferenceManager<test.BadMetadata> "
                + "because test.BadMetadata is not annotated with "
                + "@dagger.releasablereferences.CanReleaseReferences")
        .inFile(component)
        .onLineContaining("interface BadMetadataComponent");
  }

  @Test
  public void releasableReferenceManagerConflict_ReleasableReferenceManager() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@TestMetadata",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");

    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides @ForReleasableReferences(TestScope.class)",
            "  static ReleasableReferenceManager rrm() {",
            "    return null;",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "",
            "@TestScope",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  @ForReleasableReferences(TestScope.class)",
            "  ReleasableReferenceManager testManager();",
            "}");

    Compilation compilation =
        daggerCompiler().compile(testScope, testMetadata, testModule, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                message(
                    "@%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.ReleasableReferenceManager is bound multiple times:",
                    "    @Provides @%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.ReleasableReferenceManager test.TestModule.rrm()",
                    "    binding for "
                        + "@%1$s.ForReleasableReferences(value = test.TestScope.class) "
                        + "%1$s.ReleasableReferenceManager from the scope declaration"),
                "dagger.releasablereferences"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void releasableReferenceManagerConflict_TypedReleasableReferenceManager() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@TestMetadata",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");

    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides @ForReleasableReferences(TestScope.class)",
            "  static TypedReleasableReferenceManager<TestMetadata> typedRrm() {",
            "    return null;",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "",
            "@TestScope",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  @ForReleasableReferences(TestScope.class)",
            "  TypedReleasableReferenceManager<TestMetadata> typedManager();",
            "}");

    Compilation compilation =
        daggerCompiler().compile(testScope, testMetadata, testModule, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                message(
                    "@%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.TypedReleasableReferenceManager<test.TestMetadata> "
                        + "is bound multiple times:",
                    "    @Provides @%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.TypedReleasableReferenceManager<test.TestMetadata> "
                        + "test.TestModule.typedRrm()",
                    "    binding for "
                        + "@%1$s.ForReleasableReferences(value = test.TestScope.class) "
                        + "%1$s.TypedReleasableReferenceManager<test.TestMetadata> "
                        + "from the scope declaration"),
                "dagger.releasablereferences"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void releasableReferenceManagerConflict_SetOfReleasableReferenceManager() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@TestMetadata",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");

    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "import java.util.Set;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides",
            "  static Set<ReleasableReferenceManager> rrmSet() {",
            "    return null;",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "import java.util.Set;",
            "",
            "@TestScope",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Set<ReleasableReferenceManager> managers();",
            "}");

    Compilation compilation =
        daggerCompiler().compile(testScope, testMetadata, testModule, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.Set<dagger.releasablereferences.ReleasableReferenceManager> "
                    + "is bound multiple times:",
                "    @Provides "
                    + "Set<dagger.releasablereferences.ReleasableReferenceManager> "
                    + "test.TestModule.rrmSet()",
                "    Dagger-generated binding for "
                    + "Set<dagger.releasablereferences.ReleasableReferenceManager>"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void releasableReferenceManagerConflict_SetOfTypedReleasableReferenceManagers() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@TestMetadata",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");

    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "import java.util.Set;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides",
            "  static Set<TypedReleasableReferenceManager<TestMetadata>> typedRrmSet() {",
            "    return null;",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "import java.util.Set;",
            "",
            "@TestScope",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Set<TypedReleasableReferenceManager<TestMetadata>> typedManagers();",
            "}");

    Compilation compilation =
        daggerCompiler().compile(testScope, testMetadata, testModule, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                message(
                    "java.util.Set<%1$s.TypedReleasableReferenceManager<test.TestMetadata>> "
                        + "is bound multiple times:",
                    "    @Provides "
                        + "Set<%1$s.TypedReleasableReferenceManager<test.TestMetadata>> "
                        + "test.TestModule.typedRrmSet()",
                    "    Dagger-generated binding for "
                        + "Set<%1$s.TypedReleasableReferenceManager<test.TestMetadata>>"),
                "dagger.releasablereferences"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }
}
