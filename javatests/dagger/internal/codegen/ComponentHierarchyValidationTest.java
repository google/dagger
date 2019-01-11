/*
 * Copyright (C) 2016 The Dagger Authors.
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

/** Tests for {ComponentHierarchyValidator}. */
@RunWith(JUnit4.class)
public class ComponentHierarchyValidationTest {
  @Test
  public void singletonSubcomponent() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Subcomponent",
            "interface Child {}");

    Compilation compilation = daggerCompiler().compile(component, subcomponent);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("conflicting scopes");
    assertThat(compilation).hadErrorContaining("test.Parent also has @Singleton");

    Compilation withoutScopeValidation =
        daggerCompiler()
            .withOptions("-Adagger.disableInterComponentScopeValidation=none")
            .compile(component, subcomponent);
    assertThat(withoutScopeValidation).succeeded();
  }

  @Test
  public void productionComponents_productionScopeImplicitOnBoth() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.producers.ProductionComponent;",
            "",
            "@ProductionComponent(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "  Object productionScopedObject();",
            "}");
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Provides;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.ProductionScope;",
            "",
            "@ProducerModule",
            "class ParentModule {",
            "  @Provides @ProductionScope Object parentScopedObject() { return new Object(); }",
            "}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  String productionScopedString();",
            "}");
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Provides;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.ProductionScope;",
            "",
            "@ProducerModule",
            "class ChildModule {",
            "  @Provides @ProductionScope String childScopedString() { return new String(); }",
            "}");
    Compilation compilation =
        daggerCompiler().compile(component, subcomponent, parentModule, childModule);
    assertThat(compilation).succeeded();
  }

  @Test
  public void producerModuleRepeated() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.producers.ProductionComponent;",
            "",
            "@ProductionComponent(modules = RepeatedProducerModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject repeatedModule =
        JavaFileObjects.forSourceLines(
            "test.RepeatedProducerModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "",
            "@ProducerModule",
            "interface RepeatedProducerModule {}");
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent(modules = RepeatedProducerModule.class)",
            "interface Child {}");
    Compilation compilation = daggerCompiler().compile(component, subcomponent, repeatedModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.Child repeats @ProducerModules:",
                "  test.Parent also installs: test.RepeatedProducerModule"))
        .inFile(component)
        .onLineContaining("interface Parent");
  }

  @Test
  public void factoryMethodForSubcomponentWithBuilder_isNotAllowed() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module(subcomponents = Sub.class)",
            "class TestModule {",
            "}");

    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Sub {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Sub build();",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface C {",
            "  Sub newSub();",
            "}");

    Compilation compilation = daggerCompiler().compile(module, component, subcomponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Components may not have factory methods for subcomponents that define a builder.");
  }

  @Test
  public void repeatedModulesWithScopes() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface TestScope {}");
    JavaFileObject moduleWithScopedProvides =
        JavaFileObjects.forSourceLines(
            "test.ModuleWithScopedProvides",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ModuleWithScopedProvides {",
            "  @Provides",
            "  @TestScope",
            "  static Object o() { return new Object(); }",
            "}");
    JavaFileObject moduleWithScopedBinds =
        JavaFileObjects.forSourceLines(
            "test.ModuleWithScopedBinds",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface ModuleWithScopedBinds {",
            "  @Binds",
            "  @TestScope",
            "  Object o(String s);",
            "}");
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = {ModuleWithScopedProvides.class, ModuleWithScopedBinds.class})",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(",
            "    modules = {ModuleWithScopedProvides.class, ModuleWithScopedBinds.class})",
            "interface Child {}");
    Compilation compilation =
        daggerCompiler()
            .compile(testScope, moduleWithScopedProvides, moduleWithScopedBinds, parent, child);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.Child repeats modules with scoped bindings or declarations:",
                "  - test.Parent also includes:",
                "    - test.ModuleWithScopedProvides with scopes: @test.TestScope",
                "    - test.ModuleWithScopedBinds with scopes: @test.TestScope"));
  }

  @Test
  public void repeatedModulesWithReusableScope() {
    JavaFileObject moduleWithScopedProvides =
        JavaFileObjects.forSourceLines(
            "test.ModuleWithScopedProvides",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Reusable;",
            "",
            "@Module",
            "class ModuleWithScopedProvides {",
            "  @Provides",
            "  @Reusable",
            "  static Object o() { return new Object(); }",
            "}");
    JavaFileObject moduleWithScopedBinds =
        JavaFileObjects.forSourceLines(
            "test.ModuleWithScopedBinds",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Reusable;",
            "",
            "@Module",
            "interface ModuleWithScopedBinds {",
            "  @Binds",
            "  @Reusable",
            "  Object o(String s);",
            "}");
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = {ModuleWithScopedProvides.class, ModuleWithScopedBinds.class})",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(",
            "    modules = {ModuleWithScopedProvides.class, ModuleWithScopedBinds.class})",
            "interface Child {}");
    Compilation compilation =
        daggerCompiler()
            .compile(moduleWithScopedProvides, moduleWithScopedBinds, parent, child);
    assertThat(compilation).succeededWithoutWarnings();
  }
}
