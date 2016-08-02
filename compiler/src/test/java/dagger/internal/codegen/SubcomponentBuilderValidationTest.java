/*
 * Copyright (C) 2015 The Dagger Authors.
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

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link dagger.Subcomponent.Builder} validation. */
@RunWith(JUnit4.class)
public class SubcomponentBuilderValidationTest {

  private static final ErrorMessages.SubcomponentBuilderMessages MSGS =
      new ErrorMessages.SubcomponentBuilderMessages();

  @Test
  public void testRefSubcomponentAndSubBuilderFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent child();",
        "  ChildComponent.Builder builder();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  static interface Builder {",
        "    ChildComponent build();",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(componentFile, childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(String.format(MSGS.moreThanOneRefToSubcomponent(),
            "test.ChildComponent", "[child(), builder()]"))
        .in(componentFile);
  }

  @Test
  public void testRefSubBuilderTwiceFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder builder1();",
        "  ChildComponent.Builder builder2();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  static interface Builder {",
        "    ChildComponent build();",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(componentFile, childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(String.format(MSGS.moreThanOneRefToSubcomponent(),
            "test.ChildComponent", "[builder1(), builder2()]"))
        .in(componentFile);
  }

  @Test
  public void testMoreThanOneBuilderFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder1 build();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  static interface Builder1 {",
        "    ChildComponent build();",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  static interface Builder2 {",
        "    ChildComponent build();",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(componentFile, childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(String.format(MSGS.moreThanOne(),
            "[test.ChildComponent.Builder1, test.ChildComponent.Builder2]"))
        .in(childComponentFile);
  }

  @Test
  public void testBuilderGenericsFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder1 build();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder<T> {",
        "     ChildComponent build();",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(componentFile, childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.generics())
        .in(childComponentFile);
  }

  @Test
  public void testBuilderNotInComponentFails() {
    JavaFileObject builder = JavaFileObjects.forSourceLines("test.Builder",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent.Builder",
        "interface Builder {}");
    assertAbout(javaSource()).that(builder)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.mustBeInComponent())
        .in(builder);
  }

  @Test
  public void testBuilderMissingBuildMethodFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder1 build();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {}",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(componentFile, childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.missingBuildMethod())
        .in(childComponentFile);
  }

  @Test
  public void testPrivateBuilderFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  private interface Builder {}",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.isPrivate())
        .in(childComponentFile);
  }

  @Test
  public void testNonStaticBuilderFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  abstract class Builder {}",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.mustBeStatic())
        .in(childComponentFile);
  }

  @Test
  public void testNonAbstractBuilderFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  static class Builder {}",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.mustBeAbstract())
        .in(childComponentFile);
  }

  @Test
  public void testBuilderOneCxtorWithArgsFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  static abstract class Builder {",
        "    Builder(String unused) {}",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.cxtorOnlyOneAndNoArgs())
        .in(childComponentFile);
  }

  @Test
  public void testBuilderMoreThanOneCxtorFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  static abstract class Builder {",
        "    Builder() {}",
        "    Builder(String unused) {}",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.cxtorOnlyOneAndNoArgs())
        .in(childComponentFile);
  }

  @Test
  public void testBuilderEnumFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  enum Builder {}",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.mustBeClassOrInterface())
        .in(childComponentFile);
  }

  @Test
  public void testBuilderBuildReturnsWrongTypeFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    String build();",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.buildMustReturnComponentType())
            .in(childComponentFile).onLine(9);
  }

  @Test
  public void testInheritedBuilderBuildReturnsWrongTypeFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    String build();",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.inheritedBuildMustReturnComponentType(), "build"))
            .in(childComponentFile).onLine(12);
  }

  @Test
  public void testTwoBuildMethodsFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    ChildComponent create();",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(String.format(MSGS.twoBuildMethods(), "build()"))
            .in(childComponentFile).onLine(10);
  }

  @Test
  public void testInheritedTwoBuildMethodsFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    ChildComponent create();",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.inheritedTwoBuildMethods(), "create()", "build()"))
            .in(childComponentFile).onLine(13);
  }

  @Test
  public void testMoreThanOneArgFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    Builder set(String s, Integer i);",
        "    Builder set(Number n, Double d);",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.methodsMustTakeOneArg())
            .in(childComponentFile).onLine(10)
        .and().withErrorContaining(MSGS.methodsMustTakeOneArg())
            .in(childComponentFile).onLine(11);
  }

  @Test
  public void testInheritedMoreThanOneArgFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    Builder set1(String s, Integer i);",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.inheritedMethodsMustTakeOneArg(),
                "set1(java.lang.String,java.lang.Integer)"))
            .in(childComponentFile).onLine(13);
  }

  @Test
  public void testSetterReturningNonVoidOrBuilderFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    String set(Integer i);",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.methodsMustReturnVoidOrBuilder())
            .in(childComponentFile).onLine(10);
  }

  @Test
  public void testInheritedSetterReturningNonVoidOrBuilderFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    String set(Integer i);",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.inheritedMethodsMustReturnVoidOrBuilder(),
                "set(java.lang.Integer)"))
            .in(childComponentFile).onLine(13);
  }

  @Test
  public void testGenericsOnSetterMethodFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    <T> Builder set(T t);",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.methodsMayNotHaveTypeParameters())
            .in(childComponentFile).onLine(10);
  }

  @Test
  public void testGenericsOnInheritedSetterMethodFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    <T> Builder set(T t);",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.inheritedMethodsMayNotHaveTypeParameters(), "<T>set(T)"))
            .in(childComponentFile).onLine(13);
  }

  @Test
  public void testMultipleSettersPerTypeFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    void set1(String s);",
        "    void set2(String s);",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.manyMethodsForType(),
                  "java.lang.String", "[set1(java.lang.String), set2(java.lang.String)]"))
            .in(childComponentFile).onLine(8);
  }

  @Test
  public void testMultipleSettersPerTypeIncludingResolvedGenericsFails() {
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent<T> {",
        "    void set1(T t);",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent<String> {",
        "    ChildComponent build();",
        "    void set2(String s);",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.manyMethodsForType(),
                  "java.lang.String", "[set1(T), set2(java.lang.String)]"))
            .in(childComponentFile).onLine(12);
  }

  @Test
  public void testExtraSettersFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder build();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    void set1(String s);",
        "    void set2(Integer s);",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(componentFile, childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.extraSetters(),
                  "[void test.ChildComponent.Builder.set1(String),"
                  + " void test.ChildComponent.Builder.set2(Integer)]"))
            .in(childComponentFile).onLine(8);
  }

  @Test
  public void testMissingSettersFail() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  TestModule(String unused) {}",
        "  @Provides String s() { return null; }",
        "}");
    JavaFileObject module2File = JavaFileObjects.forSourceLines("test.Test2Module",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class Test2Module {",
        "  @Provides Integer i() { return null; }",
        "}");
    JavaFileObject module3File = JavaFileObjects.forSourceLines("test.Test3Module",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class Test3Module {",
        "  Test3Module(String unused) {}",
        "  @Provides Double d() { return null; }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder build();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = {TestModule.class, Test2Module.class, Test3Module.class})",
        "interface ChildComponent {",
        "  String string();",
        "  Integer integer();",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent create();",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(moduleFile,
            module2File,
            module3File,
            componentFile,
            childComponentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            // Ignores Test2Module because we can construct it ourselves.
            // TODO(sameb): Ignore Test3Module because it's not used within transitive dependencies.
            String.format(MSGS.missingSetters(), "[test.TestModule, test.Test3Module]"))
            .in(childComponentFile).onLine(11);
  }
}
