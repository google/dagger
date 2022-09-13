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

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ScopingValidationTest {
  @Test
  public void componentWithoutScopeIncludesScopedBindings_Fail() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Component(modules = ScopedModule.class)",
            "interface MyComponent {",
            "  ScopedType string();",
            "}");
    Source typeFile =
        CompilerTests.javaSource(
            "test.ScopedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "class ScopedType {",
            "  @Inject ScopedType(String s, long l, float f) {}",
            "}");
    Source moduleFile =
        CompilerTests.javaSource(
            "test.ScopedModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "class ScopedModule {",
            "  @Provides @Singleton String string() { return \"a string\"; }",
            "  @Provides long integer() { return 0L; }",
            "  @Provides float floatingPoint() { return 0.0f; }",
            "}");

    CompilerTests.daggerCompiler(componentFile, typeFile, moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining(
                  "MyComponent (unscoped) may not reference scoped bindings:");
              subject.hasErrorContaining("    @Singleton class ScopedType");
              subject.hasErrorContaining("    ScopedType is requested at");
              subject.hasErrorContaining("        MyComponent.string()");
              subject.hasErrorContaining("");
              subject.hasErrorContaining("    @Provides @Singleton String ScopedModule.string()");
            });
  }

  @Test // b/79859714
  public void bindsWithChildScope_inParentModule_notAllowed() {
    Source childScope =
        CompilerTests.javaSource(
            "test.ChildScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface ChildScope {}");

    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "", //
            "interface Foo {}");

    Source fooImpl =
        CompilerTests.javaSource(
            "test.FooImpl",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class FooImpl implements Foo {",
            "  @Inject FooImpl() {}",
            "}");

    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface ParentModule {",
            "  @Binds @ChildScope Foo bind(FooImpl fooImpl);",
            "}");

    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = ParentModule.class)",
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
            "@ChildScope",
            "@Subcomponent",
            "interface Child {",
            "  Foo foo();",
            "}");

    CompilerTests.daggerCompiler(childScope, foo, fooImpl, parentModule, parent, child)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining(
                  "Parent scoped with @Singleton may not reference bindings with different "
                      + "scopes:");
              subject.hasErrorContaining("    @Binds @ChildScope Foo ParentModule.bind(FooImpl)");
            });
  }

  @Test
  public void componentWithScopeIncludesIncompatiblyScopedBindings_Fail() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = ScopedModule.class)",
            "interface MyComponent {",
            "  ScopedType string();",
            "}");
    Source scopeFile =
        CompilerTests.javaSource(
            "test.PerTest",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface PerTest {}");
    Source scopeWithAttribute =
        CompilerTests.javaSource(
            "test.Per",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface Per {",
            "  Class<?> value();",
            "}");
    Source typeFile =
        CompilerTests.javaSource(
            "test.ScopedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "@PerTest", // incompatible scope
            "class ScopedType {",
            "  @Inject ScopedType(String s, long l, float f, boolean b) {}",
            "}");
    Source moduleFile =
        CompilerTests.javaSource(
            "test.ScopedModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "class ScopedModule {",
            "  @Provides @PerTest String string() { return \"a string\"; }", // incompatible scope
            "  @Provides long integer() { return 0L; }", // unscoped - valid
            "  @Provides @Singleton float floatingPoint() { return 0.0f; }", // same scope - valid
            "  @Provides @Per(MyComponent.class) boolean bool() { return false; }", // incompatible
            "}");

    CompilerTests.daggerCompiler(componentFile, scopeFile, scopeWithAttribute, typeFile, moduleFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining(
                  "MyComponent scoped with @Singleton may not reference bindings with different "
                      + "scopes:");
              subject.hasErrorContaining("    @PerTest class ScopedType");
              subject.hasErrorContaining("    ScopedType is requested at");
              subject.hasErrorContaining("        MyComponent.string()");
              subject.hasErrorContaining("");
              subject.hasErrorContaining("    @Provides @PerTest String ScopedModule.string()");
              subject.hasErrorContaining("");
              subject.hasErrorContaining(
                      String.format(
                          "    @Provides @Per(%s) boolean ScopedModule.bool()",
                          // TODO(b/241293838): Remove dependency on backend once this bug is fixed.
                          CompilerTests.backend(subject).equals(XProcessingEnv.Backend.JAVAC)
                              ? "MyComponent.class"
                              : "MyComponent"))
                  .onSource(componentFile)
                  .onLineContaining("interface MyComponent");
            });

    // The @Inject binding for ScopedType should not appear here, but the @Singleton binding should.
    CompilerTests.daggerCompiler(componentFile, scopeFile, scopeWithAttribute, typeFile, moduleFile)
        .withProcessingOptions(ImmutableMap.of("dagger.fullBindingGraphValidation", "ERROR"))
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining("ScopedModule contains bindings with different scopes:");
              subject.hasErrorContaining("    @Provides @PerTest String ScopedModule.string()");
              subject.hasErrorContaining("");
              subject.hasErrorContaining(
                  "    @Provides @Singleton float ScopedModule.floatingPoint()");
              subject.hasErrorContaining("");
              subject.hasErrorContaining(
                      String.format(
                          "    @Provides @Per(%s) boolean ScopedModule.bool()",
                          // TODO(b/241293838): Remove dependency on backend once this bug is fixed.
                          CompilerTests.backend(subject).equals(XProcessingEnv.Backend.JAVAC)
                              ? "MyComponent.class"
                              : "MyComponent"))
                  .onSource(moduleFile)
                  .onLineContaining("class ScopedModule");
            });
  }

  @Test
  public void fullBindingGraphValidationDoesNotReportForOneScope() {
    CompilerTests.daggerCompiler(
            CompilerTests.javaSource(
                "test.TestModule",
                "package test;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import javax.inject.Singleton;",
                "",
                "@Module",
                "interface TestModule {",
                "  @Provides @Singleton static Object object() { return \"object\"; }",
                "  @Provides @Singleton static String string() { return \"string\"; }",
                "  @Provides static int integer() { return 4; }",
                "}"))
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .put("dagger.fullBindingGraphValidation", "ERROR")
                .put("dagger.moduleHasDifferentScopesValidation", "ERROR")
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }

  @Test
  public void fullBindingGraphValidationDoesNotReportInjectBindings() {
    CompilerTests.daggerCompiler(
            CompilerTests.javaSource(
                "test.UsedInRootRedScoped",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "@RedScope",
                "final class UsedInRootRedScoped {",
                "  @Inject UsedInRootRedScoped() {}",
                "}"),
            CompilerTests.javaSource(
                "test.UsedInRootBlueScoped",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "@BlueScope",
                "final class UsedInRootBlueScoped {",
                "  @Inject UsedInRootBlueScoped() {}",
                "}"),
            CompilerTests.javaSource(
                "test.RedScope",
                "package test;",
                "",
                "import javax.inject.Scope;",
                "",
                "@Scope",
                "@interface RedScope {}"),
            CompilerTests.javaSource(
                "test.BlueScope",
                "package test;",
                "",
                "import javax.inject.Scope;",
                "",
                "@Scope",
                "@interface BlueScope {}"),
            CompilerTests.javaSource(
                "test.TestModule",
                "package test;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "import javax.inject.Singleton;",
                "",
                "@Module(subcomponents = Child.class)",
                "interface TestModule {",
                "  @Provides @Singleton",
                "  static Object object(",
                "      UsedInRootRedScoped usedInRootRedScoped,",
                "      UsedInRootBlueScoped usedInRootBlueScoped) {",
                "    return \"object\";",
                "  }",
                "}"),
            CompilerTests.javaSource(
                "test.Child",
                "package test;",
                "",
                "import dagger.Subcomponent;",
                "",
                "@Subcomponent",
                "interface Child {",
                "  UsedInChildRedScoped usedInChildRedScoped();",
                "  UsedInChildBlueScoped usedInChildBlueScoped();",
                "",
                "  @Subcomponent.Builder",
                "  interface Builder {",
                "    Child child();",
                "  }",
                "}"),
            CompilerTests.javaSource(
                "test.UsedInChildRedScoped",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "@RedScope",
                "final class UsedInChildRedScoped {",
                "  @Inject UsedInChildRedScoped() {}",
                "}"),
            CompilerTests.javaSource(
                "test.UsedInChildBlueScoped",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "@BlueScope",
                "final class UsedInChildBlueScoped {",
                "  @Inject UsedInChildBlueScoped() {}",
                "}"))
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .put("dagger.fullBindingGraphValidation", "ERROR")
                .put("dagger.moduleHasDifferentScopesValidation", "ERROR")
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }

  @Test
  public void componentWithScopeCanDependOnMultipleScopedComponents() {
    // If a scoped component will have dependencies, they can include multiple scoped component
    Source type =
        CompilerTests.javaSource(
            "test.SimpleType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class SimpleType {",
            "  @Inject SimpleType() {}",
            "  static class A { @Inject A() {} }",
            "  static class B { @Inject B() {} }",
            "}");
    Source simpleScope =
        CompilerTests.javaSource(
            "test.SimpleScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface SimpleScope {}");
    Source singletonScopedA =
        CompilerTests.javaSource(
            "test.SingletonComponentA",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface SingletonComponentA {",
            "  SimpleType.A type();",
            "}");
    Source singletonScopedB =
        CompilerTests.javaSource(
            "test.SingletonComponentB",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface SingletonComponentB {",
            "  SimpleType.B type();",
            "}");
    Source scopeless =
        CompilerTests.javaSource(
            "test.ScopelessComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ScopelessComponent {",
            "  SimpleType type();",
            "}");
    Source simpleScoped =
        CompilerTests.javaSource(
            "test.SimpleScopedComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@SimpleScope",
            "@Component(dependencies = {SingletonComponentA.class, SingletonComponentB.class})",
            "interface SimpleScopedComponent {",
            "  SimpleType.A type();",
            "}");

    CompilerTests.daggerCompiler(
            type, simpleScope, simpleScoped, singletonScopedA, singletonScopedB, scopeless)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }



  // Tests the following component hierarchy:
  //
  //        @ScopeA
  //        ComponentA
  //        [SimpleType getSimpleType()]
  //        /        \
  //       /          \
  //   @ScopeB         @ScopeB
  //   ComponentB1     ComponentB2
  //      \            [SimpleType getSimpleType()]
  //       \          /
  //        \        /
  //         @ScopeC
  //         ComponentC
  //         [SimpleType getSimpleType()]
  @Test
  public void componentWithScopeCanDependOnMultipleScopedComponentsEvenDoingADiamond() {
    Source type =
        CompilerTests.javaSource(
            "test.SimpleType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class SimpleType {",
            "  @Inject SimpleType() {}",
            "}");
    Source simpleScope =
        CompilerTests.javaSource(
            "test.SimpleScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface SimpleScope {}");
    Source scopeA =
        CompilerTests.javaSource(
            "test.ScopeA",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface ScopeA {}");
    Source scopeB =
        CompilerTests.javaSource(
            "test.ScopeB",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface ScopeB {}");
    Source componentA =
        CompilerTests.javaSource(
            "test.ComponentA",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@ScopeA",
            "@Component",
            "interface ComponentA {",
            "  SimpleType type();",
            "}");
    Source componentB1 =
        CompilerTests.javaSource(
            "test.ComponentB1",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@ScopeB",
            "@Component(dependencies = ComponentA.class)",
            "interface ComponentB1 {",
            "  SimpleType type();",
            "}");
    Source componentB2 =
        CompilerTests.javaSource(
            "test.ComponentB2",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@ScopeB",
            "@Component(dependencies = ComponentA.class)",
            "interface ComponentB2 {",
            "}");
    Source componentC =
        CompilerTests.javaSource(
            "test.ComponentC",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@SimpleScope",
            "@Component(dependencies = {ComponentB1.class, ComponentB2.class})",
            "interface ComponentC {",
            "  SimpleType type();",
            "}");

    CompilerTests.daggerCompiler(
            type, simpleScope, scopeA, scopeB, componentA, componentB1, componentB2, componentC)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }

  @Test
  public void componentWithoutScopeCannotDependOnScopedComponent() {
    Source type =
        CompilerTests.javaSource(
            "test.SimpleType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class SimpleType {",
            "  @Inject SimpleType() {}",
            "}");
    Source scopedComponent =
        CompilerTests.javaSource(
            "test.ScopedComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface ScopedComponent {",
            "  SimpleType type();",
            "}");
    Source unscopedComponent =
        CompilerTests.javaSource(
            "test.UnscopedComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Component(dependencies = ScopedComponent.class)",
            "interface UnscopedComponent {",
            "  SimpleType type();",
            "}");

    CompilerTests.daggerCompiler(type, scopedComponent, unscopedComponent)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining(
                  "test.UnscopedComponent (unscoped) cannot depend on scoped components:");
              subject.hasErrorContaining("    @Singleton test.ScopedComponent");
            });
  }

  @Test
  public void componentWithSingletonScopeMayNotDependOnOtherScope() {
    // Singleton must be the widest lifetime of present scopes.
    Source type =
        CompilerTests.javaSource(
            "test.SimpleType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class SimpleType {",
            "  @Inject SimpleType() {}",
            "}");
    Source simpleScope =
        CompilerTests.javaSource(
            "test.SimpleScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface SimpleScope {}");
    Source simpleScoped =
        CompilerTests.javaSource(
            "test.SimpleScopedComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@SimpleScope",
            "@Component",
            "interface SimpleScopedComponent {",
            "  SimpleType type();",
            "}");
    Source singletonScoped =
        CompilerTests.javaSource(
            "test.SingletonComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(dependencies = SimpleScopedComponent.class)",
            "interface SingletonComponent {",
            "  SimpleType type();",
            "}");

    CompilerTests.daggerCompiler(type, simpleScope, simpleScoped, singletonScoped)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining(
                  "This @Singleton component cannot depend on scoped components:");
              subject.hasErrorContaining("    @test.SimpleScope test.SimpleScopedComponent");
            });
  }

  @Test
  public void componentScopeWithMultipleScopedDependenciesMustNotCycle() {
    Source type =
        CompilerTests.javaSource(
            "test.SimpleType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class SimpleType {",
            "  @Inject SimpleType() {}",
            "}");
    Source scopeA =
        CompilerTests.javaSource(
            "test.ScopeA",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface ScopeA {}");
    Source scopeB =
        CompilerTests.javaSource(
            "test.ScopeB",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface ScopeB {}");
    Source longLifetime =
        CompilerTests.javaSource(
            "test.ComponentLong",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@ScopeA",
            "@Component",
            "interface ComponentLong {",
            "  SimpleType type();",
            "}");
    Source mediumLifetime1 =
        CompilerTests.javaSource(
            "test.ComponentMedium1",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@ScopeB",
            "@Component(dependencies = ComponentLong.class)",
            "interface ComponentMedium1 {",
            "  SimpleType type();",
            "}");
    Source mediumLifetime2 =
        CompilerTests.javaSource(
            "test.ComponentMedium2",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@ScopeB",
            "@Component",
            "interface ComponentMedium2 {",
            "}");
    Source shortLifetime =
        CompilerTests.javaSource(
            "test.ComponentShort",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@ScopeA",
            "@Component(dependencies = {ComponentMedium1.class, ComponentMedium2.class})",
            "interface ComponentShort {",
            "  SimpleType type();",
            "}");

    CompilerTests.daggerCompiler(
            type, scopeA, scopeB, longLifetime, mediumLifetime1, mediumLifetime2, shortLifetime)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining(
                  "test.ComponentShort depends on scoped components in a non-hierarchical scope "
                      + "ordering:");
              subject.hasErrorContaining("    @test.ScopeA test.ComponentLong");
              subject.hasErrorContaining("    @test.ScopeB test.ComponentMedium1");
              subject.hasErrorContaining("    @test.ScopeA test.ComponentShort");
            });
  }

  @Test
  public void componentScopeAncestryMustNotCycle() {
    // The dependency relationship of components is necessarily from shorter lifetimes to
    // longer lifetimes.  The scoping annotations must reflect this, and so one cannot declare
    // scopes on components such that they cycle.
    Source type =
        CompilerTests.javaSource(
            "test.SimpleType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class SimpleType {",
            "  @Inject SimpleType() {}",
            "}");
    Source scopeA =
        CompilerTests.javaSource(
            "test.ScopeA",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface ScopeA {}");
    Source scopeB =
        CompilerTests.javaSource(
            "test.ScopeB",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface ScopeB {}");
    Source longLifetime =
        CompilerTests.javaSource(
            "test.ComponentLong",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@ScopeA",
            "@Component",
            "interface ComponentLong {",
            "  SimpleType type();",
            "}");
    Source mediumLifetime =
        CompilerTests.javaSource(
            "test.ComponentMedium",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@ScopeB",
            "@Component(dependencies = ComponentLong.class)",
            "interface ComponentMedium {",
            "  SimpleType type();",
            "}");
    Source shortLifetime =
        CompilerTests.javaSource(
            "test.ComponentShort",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@ScopeA",
            "@Component(dependencies = ComponentMedium.class)",
            "interface ComponentShort {",
            "  SimpleType type();",
            "}");

    CompilerTests.daggerCompiler(type, scopeA, scopeB, longLifetime, mediumLifetime, shortLifetime)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              // TODO(b/243689574): Combine this to a single assertion once this bug is fixed.
              subject.hasErrorContaining(
                  "test.ComponentShort depends on scoped components in a non-hierarchical scope "
                      + "ordering:");
              subject.hasErrorContaining("    @test.ScopeA test.ComponentLong");
              subject.hasErrorContaining("    @test.ScopeB test.ComponentMedium");
              subject.hasErrorContaining("    @test.ScopeA test.ComponentShort");
            });

    // Test that compilation succeeds when transitive validation is disabled because the scope cycle
    // cannot be detected.
    CompilerTests.daggerCompiler(type, scopeA, scopeB, longLifetime, mediumLifetime, shortLifetime)
        .withProcessingOptions(
            ImmutableMap.of("dagger.validateTransitiveComponentDependencies", "DISABLED"))
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void reusableNotAllowedOnComponent() {
    Source someComponent =
        CompilerTests.javaSource(
            "test.SomeComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Reusable;",
            "",
            "@Reusable",
            "@Component",
            "interface SomeComponent {}");
    CompilerTests.daggerCompiler(someComponent)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@Reusable cannot be applied to components or subcomponents")
                  .onSource(someComponent)
                  .onLine(6);
            });
  }

  @Test
  public void reusableNotAllowedOnSubcomponent() {
    Source someSubcomponent =
        CompilerTests.javaSource(
            "test.SomeComponent",
            "package test;",
            "",
            "import dagger.Reusable;",
            "import dagger.Subcomponent;",
            "",
            "@Reusable",
            "@Subcomponent",
            "interface SomeSubcomponent {}");
    CompilerTests.daggerCompiler(someSubcomponent)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@Reusable cannot be applied to components or subcomponents")
                  .onSource(someSubcomponent)
                  .onLine(6);
            });
  }
}
