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
import static dagger.internal.codegen.TestUtils.message;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
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

  @Test
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

  @Test
  public void componentDependencyMustNotCycle_Direct() {
    JavaFileObject shortLifetime =
        JavaFileObjects.forSourceLines(
            "test.ComponentShort",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = ComponentShort.class)",
            "interface ComponentShort {",
            "}");

    Compilation compilation = daggerCompiler().compile(shortLifetime);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.ComponentShort contains a cycle in its component dependencies:",
                "    test.ComponentShort"));
  }

  @Test
  public void componentDependencyMustNotCycle_Indirect() {
    JavaFileObject longLifetime =
        JavaFileObjects.forSourceLines(
            "test.ComponentLong",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = ComponentMedium.class)",
            "interface ComponentLong {",
            "}");
    JavaFileObject mediumLifetime =
        JavaFileObjects.forSourceLines(
            "test.ComponentMedium",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = ComponentLong.class)",
            "interface ComponentMedium {",
            "}");
    JavaFileObject shortLifetime =
        JavaFileObjects.forSourceLines(
            "test.ComponentShort",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = ComponentMedium.class)",
            "interface ComponentShort {",
            "}");

    Compilation compilation = daggerCompiler().compile(longLifetime, mediumLifetime, shortLifetime);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.ComponentLong contains a cycle in its component dependencies:",
                "    test.ComponentLong",
                "    test.ComponentMedium",
                "    test.ComponentLong"))
        .inFile(longLifetime);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.ComponentMedium contains a cycle in its component dependencies:",
                "    test.ComponentMedium",
                "    test.ComponentLong",
                "    test.ComponentMedium"))
        .inFile(mediumLifetime);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.ComponentShort contains a cycle in its component dependencies:",
                "    test.ComponentMedium",
                "    test.ComponentLong",
                "    test.ComponentMedium",
                "    test.ComponentShort"))
        .inFile(shortLifetime);
  }

  @Test
  public void abstractModuleWithInstanceMethod() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides int i() { return 1; }",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  int i();",
            "}");
    Compilation compilation = daggerCompiler().compile(module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("TestModule is abstract and has instance @Provides methods")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void abstractModuleWithInstanceMethod_subclassedIsAllowed() {
    JavaFileObject abstractModule =
        JavaFileObjects.forSourceLines(
            "test.AbstractModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class AbstractModule {",
            "  @Provides int i() { return 1; }",
            "}");
    JavaFileObject subclassedModule =
        JavaFileObjects.forSourceLines(
            "test.SubclassedModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "class SubclassedModule extends AbstractModule {}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = SubclassedModule.class)",
            "interface TestComponent {",
            "  int i();",
            "}");
    Compilation compilation = daggerCompiler().compile(abstractModule, subclassedModule, component);
    assertThat(compilation).succeeded();
  }
}
