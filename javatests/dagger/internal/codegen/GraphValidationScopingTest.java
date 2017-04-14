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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static java.util.Arrays.asList;

import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GraphValidationScopingTest {
  @Test public void componentWithoutScopeIncludesScopedBindings_Fail() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.MyComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Singleton;",
        "",
        "@Component(modules = ScopedModule.class)",
        "interface MyComponent {",
        "  ScopedType string();",
        "}");
    JavaFileObject typeFile = JavaFileObjects.forSourceLines("test.ScopedType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Singleton;",
        "",
        "@Singleton",
        "class ScopedType {",
        "  @Inject ScopedType(String s, long l, float f) {}",
        "}");
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.ScopedModule",
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
    String errorMessage =
        "test.MyComponent (unscoped) may not reference scoped bindings:\n"
            + "      @Singleton class test.ScopedType\n"
            + "      @Provides @Singleton String test.ScopedModule.string()";
    assertAbout(javaSources())
        .that(asList(componentFile, typeFile, moduleFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(errorMessage);
  }

  @Test public void componentWithScopeIncludesIncompatiblyScopedBindings_Fail() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.MyComponent",
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
    JavaFileObject scopeFile = JavaFileObjects.forSourceLines("test.PerTest",
        "package test;",
        "",
        "import javax.inject.Scope;",
        "",
        "@Scope",
        "@interface PerTest {}");
    JavaFileObject typeFile = JavaFileObjects.forSourceLines("test.ScopedType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "@PerTest", // incompatible scope
        "class ScopedType {",
        "  @Inject ScopedType(String s, long l, float f) {}",
        "}");
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.ScopedModule",
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
        "}");
    String errorMessage =
        "test.MyComponent scoped with @Singleton "
            + "may not reference bindings with different scopes:\n"
            + "      @test.PerTest class test.ScopedType\n"
            + "      @Provides @test.PerTest String test.ScopedModule.string()";
    assertAbout(javaSources())
        .that(asList(componentFile, scopeFile, typeFile, moduleFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(errorMessage);
  }

  @Test public void componentWithScopeMayDependOnOnlyOneScopedComponent() {
    // If a scoped component will have dependencies, they must only include, at most, a single
    // scoped component
    JavaFileObject type = JavaFileObjects.forSourceLines("test.SimpleType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class SimpleType {",
        "  @Inject SimpleType() {}",
        "  static class A { @Inject A() {} }",
        "  static class B { @Inject B() {} }",
        "}");
    JavaFileObject simpleScope = JavaFileObjects.forSourceLines("test.SimpleScope",
        "package test;",
        "",
        "import javax.inject.Scope;",
        "",
        "@Scope @interface SimpleScope {}");
    JavaFileObject singletonScopedA = JavaFileObjects.forSourceLines("test.SingletonComponentA",
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
    JavaFileObject singletonScopedB = JavaFileObjects.forSourceLines("test.SingletonComponentB",
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
    JavaFileObject scopeless = JavaFileObjects.forSourceLines("test.ScopelessComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface ScopelessComponent {",
        "  SimpleType type();",
        "}");
    JavaFileObject simpleScoped = JavaFileObjects.forSourceLines("test.SimpleScopedComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@SimpleScope",
        "@Component(dependencies = {SingletonComponentA.class, SingletonComponentB.class})",
        "interface SimpleScopedComponent {",
        "  SimpleType.A type();",
        "}");
    String errorMessage =
        "@test.SimpleScope test.SimpleScopedComponent depends on more than one scoped component:\n"
        + "      @Singleton test.SingletonComponentA\n"
        + "      @Singleton test.SingletonComponentB";
    assertAbout(javaSources())
        .that(
            asList(type, simpleScope, simpleScoped, singletonScopedA, singletonScopedB, scopeless))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(errorMessage);
  }

  @Test public void componentWithoutScopeCannotDependOnScopedComponent() {
    JavaFileObject type = JavaFileObjects.forSourceLines("test.SimpleType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class SimpleType {",
        "  @Inject SimpleType() {}",
        "}");
    JavaFileObject scopedComponent = JavaFileObjects.forSourceLines("test.ScopedComponent",
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
    JavaFileObject unscopedComponent = JavaFileObjects.forSourceLines("test.UnscopedComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Singleton;",
        "",
        "@Component(dependencies = ScopedComponent.class)",
        "interface UnscopedComponent {",
        "  SimpleType type();",
        "}");
    String errorMessage =
        "test.UnscopedComponent (unscoped) cannot depend on scoped components:\n"
        + "      @Singleton test.ScopedComponent";
    assertAbout(javaSources())
        .that(asList(type, scopedComponent, unscopedComponent))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(errorMessage);
  }

  @Test public void componentWithSingletonScopeMayNotDependOnOtherScope() {
    // Singleton must be the widest lifetime of present scopes.
    JavaFileObject type = JavaFileObjects.forSourceLines("test.SimpleType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class SimpleType {",
        "  @Inject SimpleType() {}",
        "}");
    JavaFileObject simpleScope = JavaFileObjects.forSourceLines("test.SimpleScope",
        "package test;",
        "",
        "import javax.inject.Scope;",
        "",
        "@Scope @interface SimpleScope {}");
    JavaFileObject simpleScoped = JavaFileObjects.forSourceLines("test.SimpleScopedComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@SimpleScope",
        "@Component",
        "interface SimpleScopedComponent {",
        "  SimpleType type();",
        "}");
    JavaFileObject singletonScoped = JavaFileObjects.forSourceLines("test.SingletonComponent",
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
    String errorMessage =
        "This @Singleton component cannot depend on scoped components:\n"
        + "      @test.SimpleScope test.SimpleScopedComponent";
    assertAbout(javaSources())
        .that(asList(type, simpleScope, simpleScoped, singletonScoped))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(errorMessage);
  }

  @Test public void componentScopeAncestryMustNotCycle() {
    // The dependency relationship of components is necessarily from shorter lifetimes to
    // longer lifetimes.  The scoping annotations must reflect this, and so one cannot declare
    // scopes on components such that they cycle.
    JavaFileObject type = JavaFileObjects.forSourceLines("test.SimpleType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class SimpleType {",
        "  @Inject SimpleType() {}",
        "}");
    JavaFileObject scopeA = JavaFileObjects.forSourceLines("test.ScopeA",
        "package test;",
        "",
        "import javax.inject.Scope;",
        "",
        "@Scope @interface ScopeA {}");
    JavaFileObject scopeB = JavaFileObjects.forSourceLines("test.ScopeB",
        "package test;",
        "",
        "import javax.inject.Scope;",
        "",
        "@Scope @interface ScopeB {}");
    JavaFileObject longLifetime = JavaFileObjects.forSourceLines("test.ComponentLong",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@ScopeA",
        "@Component",
        "interface ComponentLong {",
        "  SimpleType type();",
        "}");
    JavaFileObject mediumLifetime = JavaFileObjects.forSourceLines("test.ComponentMedium",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@ScopeB",
        "@Component(dependencies = ComponentLong.class)",
        "interface ComponentMedium {",
        "  SimpleType type();",
        "}");
    JavaFileObject shortLifetime = JavaFileObjects.forSourceLines("test.ComponentShort",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@ScopeA",
        "@Component(dependencies = ComponentMedium.class)",
        "interface ComponentShort {",
        "  SimpleType type();",
        "}");
    String errorMessage =
        "test.ComponentShort depends on scoped components in a non-hierarchical scope ordering:\n"
        + "      @test.ScopeA test.ComponentLong\n"
        + "      @test.ScopeB test.ComponentMedium\n"
        + "      @test.ScopeA test.ComponentShort";
    assertAbout(javaSources())
        .that(asList(type, scopeA, scopeB, longLifetime, mediumLifetime, shortLifetime))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(errorMessage);
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
    assertAbout(javaSource())
        .that(someComponent)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("@Reusable cannot be applied to components or subcomponents.")
        .in(someComponent)
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
    assertAbout(javaSource())
        .that(someSubcomponent)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("@Reusable cannot be applied to components or subcomponents.")
        .in(someSubcomponent)
        .onLine(6);
  }
}
