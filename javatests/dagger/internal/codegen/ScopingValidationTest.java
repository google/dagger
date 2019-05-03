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
public class ScopingValidationTest {
  @Test
  public void componentWithoutScopeIncludesScopedBindings_Fail() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject typeFile =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = daggerCompiler().compile(componentFile, typeFile, moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.MyComponent (unscoped) may not reference scoped bindings:",
                "    @Singleton class test.ScopedType",
                "    @Provides @Singleton String test.ScopedModule.string()"));
  }

  @Test // b/79859714
  public void bindsWithChildScope_inParentModule_notAllowed() {
    JavaFileObject childScope =
        JavaFileObjects.forSourceLines(
            "test.ChildScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface ChildScope {}");

    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "", //
            "interface Foo {}");

    JavaFileObject fooImpl =
        JavaFileObjects.forSourceLines(
            "test.ChildModule",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class FooImpl implements Foo {",
            "  @Inject FooImpl() {}",
            "}");

    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
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

    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.ParentComponent",
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

    JavaFileObject child =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().compile(childScope, foo, fooImpl, parentModule, parent, child);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.Parent scoped with @Singleton may not reference bindings with different "
                    + "scopes:",
                "    @Binds @test.ChildScope test.Foo test.ParentModule.bind(test.FooImpl)"));
  }

  @Test
  public void componentWithScopeIncludesIncompatiblyScopedBindings_Fail() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject scopeFile =
        JavaFileObjects.forSourceLines(
            "test.PerTest",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface PerTest {}");
    JavaFileObject scopeWithAttribute =
        JavaFileObjects.forSourceLines(
            "test.Per",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface Per {",
            "  Class<?> value();",
            "}");
    JavaFileObject typeFile =
        JavaFileObjects.forSourceLines(
            "test.ScopedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "@PerTest", // incompatible scope
            "class ScopedType {",
            "  @Inject ScopedType(String s, long l, float f, boolean b) {}",
            "}");
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .compile(componentFile, scopeFile, scopeWithAttribute, typeFile, moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.MyComponent scoped with @Singleton "
                    + "may not reference bindings with different scopes:",
                "    @test.PerTest class test.ScopedType",
                "    @Provides @test.PerTest String test.ScopedModule.string()",
                "    @Provides @test.Per(test.MyComponent.class) boolean "
                    + "test.ScopedModule.bool()"))
        .inFile(componentFile)
        .onLineContaining("interface MyComponent");

    compilation =
        daggerCompiler()
            .withOptions("-Adagger.fullBindingGraphValidation=ERROR")
            .compile(componentFile, scopeFile, scopeWithAttribute, typeFile, moduleFile);
    // The @Inject binding for ScopedType should not appear here, but the @Singleton binding should.
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.ScopedModule contains bindings with different scopes:",
                "    @Provides @test.PerTest String test.ScopedModule.string()",
                "    @Provides @Singleton float test.ScopedModule.floatingPoint()",
                "    @Provides @test.Per(test.MyComponent.class) boolean "
                    + "test.ScopedModule.bool()"))
        .inFile(moduleFile)
        .onLineContaining("class ScopedModule");
  }

  @Test
  public void fullBindingGraphValidationDoesNotReportForOneScope() {
    Compilation compilation =
        daggerCompiler()
            .withOptions(
                "-Adagger.fullBindingGraphValidation=ERROR",
                "-Adagger.moduleHasDifferentScopesValidation=ERROR")
            .compile(
                JavaFileObjects.forSourceLines(
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
                    "}"));
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void fullBindingGraphValidationDoesNotReportInjectBindings() {
    Compilation compilation =
        daggerCompiler()
            .withOptions(
                "-Adagger.fullBindingGraphValidation=ERROR",
                "-Adagger.moduleHasDifferentScopesValidation=ERROR")
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.UsedInRootRedScoped",
                    "package test;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "@RedScope",
                    "final class UsedInRootRedScoped {",
                    "  @Inject UsedInRootRedScoped() {}",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.UsedInRootBlueScoped",
                    "package test;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "@BlueScope",
                    "final class UsedInRootBlueScoped {",
                    "  @Inject UsedInRootBlueScoped() {}",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.RedScope",
                    "package test;",
                    "",
                    "import javax.inject.Scope;",
                    "",
                    "@Scope",
                    "@interface RedScope {}"),
                JavaFileObjects.forSourceLines(
                    "test.BlueScope",
                    "package test;",
                    "",
                    "import javax.inject.Scope;",
                    "",
                    "@Scope",
                    "@interface BlueScope {}"),
                JavaFileObjects.forSourceLines(
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
                JavaFileObjects.forSourceLines(
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
                JavaFileObjects.forSourceLines(
                    "test.UsedInChildRedScoped",
                    "package test;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "@RedScope",
                    "final class UsedInChildRedScoped {",
                    "  @Inject UsedInChildRedScoped() {}",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.UsedInChildBlueScoped",
                    "package test;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "@BlueScope",
                    "final class UsedInChildBlueScoped {",
                    "  @Inject UsedInChildBlueScoped() {}",
                    "}"));
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void componentWithScopeMayDependOnOnlyOneScopedComponent() {
    // If a scoped component will have dependencies, they must only include, at most, a single
    // scoped component
    JavaFileObject type =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject simpleScope =
        JavaFileObjects.forSourceLines(
            "test.SimpleScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface SimpleScope {}");
    JavaFileObject singletonScopedA =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject singletonScopedB =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject scopeless =
        JavaFileObjects.forSourceLines(
            "test.ScopelessComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ScopelessComponent {",
            "  SimpleType type();",
            "}");
    JavaFileObject simpleScoped =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .compile(
                type, simpleScope, simpleScoped, singletonScopedA, singletonScopedB, scopeless);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "@test.SimpleScope test.SimpleScopedComponent depends on more than one scoped "
                    + "component:",
                "    @Singleton test.SingletonComponentA",
                "    @Singleton test.SingletonComponentB"));
  }

  @Test
  public void componentWithoutScopeCannotDependOnScopedComponent() {
    JavaFileObject type =
        JavaFileObjects.forSourceLines(
            "test.SimpleType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class SimpleType {",
            "  @Inject SimpleType() {}",
            "}");
    JavaFileObject scopedComponent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject unscopedComponent =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation = daggerCompiler().compile(type, scopedComponent, unscopedComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.UnscopedComponent (unscoped) cannot depend on scoped components:",
                "    @Singleton test.ScopedComponent"));
  }

  @Test
  public void componentWithSingletonScopeMayNotDependOnOtherScope() {
    // Singleton must be the widest lifetime of present scopes.
    JavaFileObject type =
        JavaFileObjects.forSourceLines(
            "test.SimpleType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class SimpleType {",
            "  @Inject SimpleType() {}",
            "}");
    JavaFileObject simpleScope =
        JavaFileObjects.forSourceLines(
            "test.SimpleScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface SimpleScope {}");
    JavaFileObject simpleScoped =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject singletonScoped =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().compile(type, simpleScope, simpleScoped, singletonScoped);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "This @Singleton component cannot depend on scoped components:",
                "    @test.SimpleScope test.SimpleScopedComponent"));
  }

  @Test
  public void componentScopeAncestryMustNotCycle() {
    // The dependency relationship of components is necessarily from shorter lifetimes to
    // longer lifetimes.  The scoping annotations must reflect this, and so one cannot declare
    // scopes on components such that they cycle.
    JavaFileObject type =
        JavaFileObjects.forSourceLines(
            "test.SimpleType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class SimpleType {",
            "  @Inject SimpleType() {}",
            "}");
    JavaFileObject scopeA =
        JavaFileObjects.forSourceLines(
            "test.ScopeA",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface ScopeA {}");
    JavaFileObject scopeB =
        JavaFileObjects.forSourceLines(
            "test.ScopeB",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope @interface ScopeB {}");
    JavaFileObject longLifetime =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject mediumLifetime =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject shortLifetime =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().compile(type, scopeA, scopeB, longLifetime, mediumLifetime, shortLifetime);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.ComponentShort depends on scoped components in a non-hierarchical scope "
                    + "ordering:",
                "    @test.ScopeA test.ComponentLong",
                "    @test.ScopeB test.ComponentMedium",
                "    @test.ScopeA test.ComponentShort"));
  }

  @Test
  public void reusableNotAllowedOnComponent() {
    JavaFileObject someComponent =
        JavaFileObjects.forSourceLines(
            "test.SomeComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Reusable;",
            "",
            "@Reusable",
            "@Component",
            "interface SomeComponent {}");
    Compilation compilation = daggerCompiler().compile(someComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Reusable cannot be applied to components or subcomponents")
        .inFile(someComponent)
        .onLine(6);
  }

  @Test
  public void reusableNotAllowedOnSubcomponent() {
    JavaFileObject someSubcomponent =
        JavaFileObjects.forSourceLines(
            "test.SomeComponent",
            "package test;",
            "",
            "import dagger.Reusable;",
            "import dagger.Subcomponent;",
            "",
            "@Reusable",
            "@Subcomponent",
            "interface SomeSubcomponent {}");
    Compilation compilation = daggerCompiler().compile(someSubcomponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Reusable cannot be applied to components or subcomponents")
        .inFile(someSubcomponent)
        .onLine(6);
  }
}
