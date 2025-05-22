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

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableMap;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.testing.compile.CompilerTests;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ComponentValidationTest {
  @Test
  public void componentOnConcreteClass() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.NotAComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "final class NotAComponent {}");
    CompilerTests.daggerCompiler(componentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("interface");
            });
  }

  @Test
  public void componentOnOverridingBuilder_failsWhenMethodNameConflictsWithStaticCreatorName() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules=TestModule.class)",
            "interface TestComponent {",
            "  String builder();",
            "}");
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String provideString() { return \"test\"; }",
            "}");

    CompilerTests.daggerCompiler(componentFile, moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The method test.TestComponent.builder() conflicts with a method");
            });
  }

  @Test
  public void componentOnOverridingCreate_failsWhenGeneratedCreateMethod() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules=TestModule.class)",
            "interface TestComponent {",
            "  String create();",
            "}");
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String provideString() { return \"test\"; }",
            "}");

    CompilerTests.daggerCompiler(componentFile, moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "The method test.TestComponent.create() conflicts with a method");
            });
  }

  @Test
  public void subcomponentMethodNameBuilder_succeeds() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  TestSubcomponent.Builder subcomponent();",
            "}");
    Source subcomponentFile =
        CompilerTests.javaSource(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules=TestModule.class)",
            "interface TestSubcomponent {",
            "  String builder();",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    TestSubcomponent build();",
            "  }",
            "}");
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String provideString() { return \"test\"; }",
            "}");

    CompilerTests.daggerCompiler(componentFile, subcomponentFile, moduleFile)
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test public void componentOnEnum() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.NotAComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "enum NotAComponent {",
            "  INSTANCE",
            "}");
    CompilerTests.daggerCompiler(componentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("interface");
            });
  }

  @Test public void componentOnAnnotation() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.NotAComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "@interface NotAComponent {}");
    CompilerTests.daggerCompiler(componentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("interface");
            });
  }

  @Test public void nonModuleModule() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.NotAComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = Object.class)",
            "interface NotAComponent {}");
    CompilerTests.daggerCompiler(componentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("is not annotated with @Module");
            });
  }

  @Test
  public void componentWithInvalidModule() {
    Source module =
        CompilerTests.javaSource(
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
    Source component =
        CompilerTests.javaSource(
            "test.BadComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = BadModule.class)",
            "interface BadComponent {",
            "  Object object();",
            "}");
    CompilerTests.daggerCompiler(module, component)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("test.BadModule has errors")
                  .onSource(component)
                  .onLine(5);
              subject.hasErrorContaining(
                      "@Binds methods must have exactly one parameter, whose type is assignable to "
                          + "the return type")
                  .onSource(module)
                  .onLine(8);
            });
  }

  @Test
  public void attemptToInjectWildcardGenerics() {
    Source testComponent =
        CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(testComponent)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("wildcard type").onSource(testComponent).onLine(9);
              subject.hasErrorContaining("wildcard type").onSource(testComponent).onLine(10);
            });
  }

  // TODO(b/245954367): Migrate test to XProcessing Testing after this bug has been fixed.
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

  // TODO(b/245954367): Migrate test to XProcessing Testing after this bug has been fixed.
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
    Source testModule =
        CompilerTests.javaSource(
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
    Source testComponent =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = TestModule.class)",
            "interface TestComponent {}");
    CompilerTests.daggerCompiler(testModule, testComponent)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "test.TestModule is a module, which cannot be a component dependency");
            });
  }

  @Test
  public void componentDependencyMustNotCycle_Direct() {
    Source shortLifetime =
        CompilerTests.javaSource(
            "test.ComponentShort",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = ComponentShort.class)",
            "interface ComponentShort {",
            "}");

    String errorMessage =
        message(
            "test.ComponentShort contains a cycle in its component dependencies:",
            "    test.ComponentShort");
    CompilerTests.daggerCompiler(shortLifetime)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(errorMessage);
            });

    // Test that this also fails when transitive validation is disabled.
    CompilerTests.daggerCompiler(shortLifetime)
        .withProcessingOptions(
            ImmutableMap.of("dagger.validateTransitiveComponentDependencies", "DISABLED"))
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(errorMessage);
            });
  }

  @Test
  public void componentDependencyMustNotCycle_Indirect() {
    Source longLifetime =
        CompilerTests.javaSource(
            "test.ComponentLong",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = ComponentMedium.class)",
            "interface ComponentLong {",
            "}");
    Source mediumLifetime =
        CompilerTests.javaSource(
            "test.ComponentMedium",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = ComponentLong.class)",
            "interface ComponentMedium {",
            "}");
    Source shortLifetime =
        CompilerTests.javaSource(
            "test.ComponentShort",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = ComponentMedium.class)",
            "interface ComponentShort {",
            "}");

    CompilerTests.daggerCompiler(longLifetime, mediumLifetime, shortLifetime)
        .compile(
            subject -> {
              subject.hasErrorCount(3);
              subject.hasErrorContaining(
                      message(
                          "test.ComponentLong contains a cycle in its component dependencies:",
                          "    test.ComponentLong",
                          "    test.ComponentMedium",
                          "    test.ComponentLong"))
                  .onSource(longLifetime);
              subject.hasErrorContaining(
                      message(
                          "test.ComponentMedium contains a cycle in its component dependencies:",
                          "    test.ComponentMedium",
                          "    test.ComponentLong",
                          "    test.ComponentMedium"))
                  .onSource(mediumLifetime);
              subject.hasErrorContaining(
                      message(
                          "test.ComponentShort contains a cycle in its component dependencies:",
                          "    test.ComponentMedium",
                          "    test.ComponentLong",
                          "    test.ComponentMedium",
                          "    test.ComponentShort"))
                  .onSource(shortLifetime);
            });

    // Test that compilation succeeds when transitive validation is disabled because the cycle
    // cannot be detected.
    CompilerTests.daggerCompiler(longLifetime, mediumLifetime, shortLifetime)
        .withProcessingOptions(
            ImmutableMap.of("dagger.validateTransitiveComponentDependencies", "DISABLED"))
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void abstractModuleWithInstanceMethod() {
    Source module =
        CompilerTests.javaSource(
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
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  int i();",
            "}");
    CompilerTests.daggerCompiler(module, component)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "TestModule is abstract and has instance @Provides methods")
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test
  public void abstractModuleWithInstanceMethod_subclassedIsAllowed() {
    Source abstractModule =
        CompilerTests.javaSource(
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
    Source subclassedModule =
        CompilerTests.javaSource(
            "test.SubclassedModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "class SubclassedModule extends AbstractModule {}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = SubclassedModule.class)",
            "interface TestComponent {",
            "  int i();",
            "}");
    CompilerTests.daggerCompiler(abstractModule, subclassedModule, component)
        .compile(subject -> subject.hasErrorCount(0));
  }
}
