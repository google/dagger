/*
 * Copyright (C) 2014 The Dagger Authors.
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

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ComponentValidationTest {
  @Test
  public void componentOnConcreteClass() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "final class NotAComponent {}");
    Compilation compilation = daggerCompiler().compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test public void componentOnEnum() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "enum NotAComponent {",
        "  INSTANCE",
        "}");
    Compilation compilation = daggerCompiler().compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test public void componentOnAnnotation() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "@interface NotAComponent {}");
    Compilation compilation = daggerCompiler().compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test public void nonModuleModule() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = Object.class)",
        "interface NotAComponent {}");
    Compilation compilation = daggerCompiler().compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("is not annotated with @Module");
  }

  @Test
  public void componentWithInvalidModule() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.BadModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class BadModule {",
            "  @Binds abstract Object noParameters();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.BadComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = BadModule.class)",
            "interface BadComponent {",
            "  Object object();",
            "}");
    Compilation compilation = daggerCompiler().compile(module, component);
    assertThat(compilation)
        .hadErrorContaining("test.BadModule has errors")
        .inFile(component)
        .onLine(5);
  }

  @Test
  public void attemptToInjectWildcardGenerics() {
    JavaFileObject testComponent =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Lazy;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Lazy<? extends Number> wildcardNumberLazy();",
            "  Provider<? super Number> wildcardNumberProvider();",
            "}");
    Compilation compilation = daggerCompiler().compile(testComponent);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("wildcard type").inFile(testComponent).onLine(9);
    assertThat(compilation).hadErrorContaining("wildcard type").inFile(testComponent).onLine(10);
  }

  // TODO(b/34107586): Fix and enable test.
  @Test
  @Ignore
  public void invalidComponentDependencies() {
    JavaFileObject testComponent =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = int.class)",
            "interface TestComponent {}");
    Compilation compilation = daggerCompiler().compile(testComponent);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("int is not a valid component dependency type");
  }

  @Test
  public void invalidComponentModules() {
    JavaFileObject testComponent =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = int.class)",
            "interface TestComponent {}");
    Compilation compilation = daggerCompiler().compile(testComponent);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("int is not a valid module type");
  }

  @Test
  public void moduleInDependencies() {
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides String s() { return null; }",
            "}");
    JavaFileObject testComponent =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = TestModule.class)",
            "interface TestComponent {}");
    Compilation compilation = daggerCompiler().compile(testModule, testComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.TestModule is a module, which cannot be a component dependency");
  }

}
