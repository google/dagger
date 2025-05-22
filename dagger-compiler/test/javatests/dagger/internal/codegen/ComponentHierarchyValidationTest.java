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

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {ComponentHierarchyValidator}. */
@RunWith(Parameterized.class)
public class ComponentHierarchyValidationTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ComponentHierarchyValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void singletonSubcomponent() {
    Source component =
        CompilerTests.javaSource(
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
    Source subcomponent =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Subcomponent",
            "interface Child {}");

    CompilerTests.daggerCompiler(component, subcomponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "test.Child has conflicting scopes:",
                      "    test.Parent also has @Singleton"));
            });

    // Check that compiling with disableInterComponentScopeValidation=none flag succeeds.
    CompilerTests.daggerCompiler(component, subcomponent)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.disableInterComponentScopeValidation", "none")
                .buildOrThrow())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void productionComponents_productionScopeImplicitOnBoth() {
    Source component =
        CompilerTests.javaSource(
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
    Source parentModule =
        CompilerTests.javaSource(
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
    Source subcomponent =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  String productionScopedString();",
            "}");
    Source childModule =
        CompilerTests.javaSource(
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
    CompilerTests.daggerCompiler(component, subcomponent, parentModule, childModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void producerModuleRepeated() {
    Source component =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.producers.ProductionComponent;",
            "",
            "@ProductionComponent(modules = RepeatedProducerModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    Source repeatedModule =
        CompilerTests.javaSource(
            "test.RepeatedProducerModule",
            "package test;",
            "",
            "import dagger.producers.ProducerModule;",
            "",
            "@ProducerModule",
            "interface RepeatedProducerModule {}");
    Source subcomponent =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.producers.ProductionSubcomponent;",
            "",
            "@ProductionSubcomponent(modules = RepeatedProducerModule.class)",
            "interface Child {}");
    CompilerTests.daggerCompiler(component, subcomponent, repeatedModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.join(
                          "\n",
                          "test.Child repeats @ProducerModules:",
                          "test.Parent also installs: test.RepeatedProducerModule"))
                  .onSource(component)
                  .onLineContaining("interface Parent");
            });
  }

  @Test
  public void factoryMethodForSubcomponentWithBuilder_isNotAllowed() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module(subcomponents = Sub.class)",
            "class TestModule {",
            "}");

    Source subcomponent =
        CompilerTests.javaSource(
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

    Source component =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface C {",
            "  Sub newSub();",
            "}");

    CompilerTests.daggerCompiler(module, component, subcomponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Components may not have factory methods for subcomponents that define a "
                      + "builder.");
            });
  }

  @Test
  public void repeatedModulesWithScopes() {
    Source testScope =
        CompilerTests.javaSource(
            "test.TestScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface TestScope {}");
    Source moduleWithScopedProvides =
        CompilerTests.javaSource(
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
    Source moduleWithScopedBinds =
        CompilerTests.javaSource(
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
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = {ModuleWithScopedProvides.class, ModuleWithScopedBinds.class})",
            "interface Parent {",
            "  Child child();",
            "}");
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(",
            "    modules = {ModuleWithScopedProvides.class, ModuleWithScopedBinds.class})",
            "interface Child {}");
    CompilerTests.daggerCompiler(
            testScope, moduleWithScopedProvides, moduleWithScopedBinds, parent, child)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.join(
                      "\n",
                      "test.Child repeats modules with scoped bindings or declarations:",
                      "- test.Parent also includes:",
                      "    - test.ModuleWithScopedProvides with scopes: @test.TestScope",
                      "    - test.ModuleWithScopedBinds with scopes: @test.TestScope"));
            });
  }

  @Test
  public void repeatedModulesWithReusableScope() {
    Source moduleWithScopedProvides =
        CompilerTests.javaSource(
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
    Source moduleWithScopedBinds =
        CompilerTests.javaSource(
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
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = {ModuleWithScopedProvides.class, ModuleWithScopedBinds.class})",
            "interface Parent {",
            "  Child child();",
            "}");
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(",
            "    modules = {ModuleWithScopedProvides.class, ModuleWithScopedBinds.class})",
            "interface Child {}");
    CompilerTests.daggerCompiler(moduleWithScopedProvides, moduleWithScopedBinds, parent, child)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }
}
