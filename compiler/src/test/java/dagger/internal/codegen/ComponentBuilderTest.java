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
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link dagger.Component.Builder} */
@RunWith(JUnit4.class)
public class ComponentBuilderTest {

  private static final ErrorMessages.ComponentBuilderMessages MSGS =
      ErrorMessages.ComponentBuilderMessages.INSTANCE;

  @Test
  public void testEmptyBuilder() {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "",
        "  @Component.Builder",
        "  static interface Builder {",
        "     SimpleComponent build();",
        "  }",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerSimpleComponent",
        "package test;",
        "",
        "import javax.annotation.Generated;",
        "",
        GENERATED_ANNOTATION,
        "public final class DaggerSimpleComponent implements SimpleComponent {",
        "  private DaggerSimpleComponent(Builder builder) {",
        "    assert builder != null;",
        "  }",
        "",
        "  public static SimpleComponent.Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static SimpleComponent create() {",
        "    return builder().build();",
        "  }",
        "",
        "  @Override",
        "  public SomeInjectableType someInjectableType() {",
        "    return SomeInjectableType_Factory.create().get();",
        "  }",
        "",
        "  private static final class Builder implements SimpleComponent.Builder {",
        "    @Override",
        "    public SimpleComponent build() {",
        "      return new DaggerSimpleComponent(this);",
        "    }",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(injectableTypeFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
  }

  @Test
  public void testUsesBuildAndSetterNames() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides String string() { return null; }",
        "}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  String string();",
        "",
        "  @Component.Builder",
        "  interface Builder {",
        "    Builder setTestModule(TestModule testModule);",
        "    TestComponent create();",
        "  }",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerTestComponent",
        "package test;",
        "",
        "import dagger.internal.Preconditions;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class DaggerTestComponent implements TestComponent {",
        "  private Provider<String> stringProvider;",
        "",
        "  private DaggerTestComponent(Builder builder) {",
        "    assert builder != null;",
        "    initialize(builder);",
        "  }",
        "",
        "  public static TestComponent.Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static TestComponent create() {",
        "    return builder().create();",
        "  }",
        "",
        "  @SuppressWarnings(\"unchecked\")",
        "  private void initialize(final Builder builder) {",
        "    this.stringProvider = TestModule_StringFactory.create(builder.testModule);",
        "  }",
        "",
        "  @Override",
        "  public String string() {",
        "    return stringProvider.get();",
        "  }",
        "",
        "  private static final class Builder implements TestComponent.Builder {",
        "    private TestModule testModule;",
        "",
        "    @Override",
        "    public TestComponent create() {",
        "      if (testModule == null) {",
        "        this.testModule = new TestModule();",
        "      }",
        "      return new DaggerTestComponent(this);",
        "    }",
        "",
        "    @Override",
        "    public Builder setTestModule(TestModule testModule) {",
        "      this.testModule = Preconditions.checkNotNull(testModule);",
        "      return this;",
        "    }",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(moduleFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
  }

  @Test
  public void testIgnoresModulesNotInApi() {
    JavaFileObject module1 = JavaFileObjects.forSourceLines("test.TestModule1",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule1 {",
        "  @Provides String string() { return null; }",
        "}");
    JavaFileObject module2 = JavaFileObjects.forSourceLines("test.TestModule2",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule2 {",
        "  @Provides Integer integer() { return null; }",
        "}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = {TestModule1.class, TestModule2.class})",
        "interface TestComponent {",
        "  String string();",
        "  Integer integer();",
        "",
        "  @Component.Builder",
        "  interface Builder {",
        "    Builder testModule1(TestModule1 testModule1);",
        "    TestComponent build();",
        "  }",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerTestComponent",
        "package test;",
        "",
        "import dagger.internal.Preconditions;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class DaggerTestComponent implements TestComponent {",
        "  private Provider<String> stringProvider;",
        "  private Provider<Integer> integerProvider;",
        "",
        "  private DaggerTestComponent(Builder builder) {",
        "    assert builder != null;",
        "    initialize(builder);",
        "  }",
        "",
        "  public static TestComponent.Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static TestComponent create() {",
        "    return builder().build();",
        "  }",
        "",
        "  @SuppressWarnings(\"unchecked\")",
        "  private void initialize(final Builder builder) {",
        "    this.stringProvider = TestModule1_StringFactory.create(builder.testModule1);",
        "    this.integerProvider = TestModule2_IntegerFactory.create(builder.testModule2);",
        "  }",
        "",
        "  @Override",
        "  public String string() {",
        "    return stringProvider.get();",
        "  }",
        "",
        "  @Override",
        "  public Integer integer() {",
        "    return integerProvider.get();",
        "  }",
        "",
        "  private static final class Builder implements TestComponent.Builder {",
        "    private TestModule1 testModule1;",
        "    private TestModule2 testModule2;",
        "",
        "    @Override",
        "    public TestComponent build() {",
        "      if (testModule1 == null) {",
        "        this.testModule1 = new TestModule1();",
        "      }",
        "      if (testModule2 == null) {",
        "        this.testModule2 = new TestModule2();",
        "      }",
        "      return new DaggerTestComponent(this);",
        "    }",
        "",
        "    @Override",
        "    public Builder testModule1(TestModule1 testModule1) {",
        "      this.testModule1 = Preconditions.checkNotNull(testModule1);",
        "      return this;",
        "    }",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(module1, module2, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
  }

  @Test
  public void testMoreThanOneBuilderFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  @Component.Builder",
        "  static interface Builder {",
        "     SimpleComponent build();",
        "  }",
        "",
        "  @Component.Builder",
        "  interface Builder2 {",
        "     SimpleComponent build();",
        "  }",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(String.format(MSGS.moreThanOne(),
            "[test.SimpleComponent.Builder, test.SimpleComponent.Builder2]"))
        .in(componentFile);
  }

  @Test
  public void testBuilderGenericsFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  @Component.Builder",
        "  interface Builder<T> {",
        "     SimpleComponent build();",
        "  }",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.generics())
        .in(componentFile);
  }

  @Test
  public void testBuilderNotInComponentFails() {
    JavaFileObject builder = JavaFileObjects.forSourceLines("test.Builder",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component.Builder",
        "interface Builder {}");
    assertAbout(javaSource()).that(builder)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.mustBeInComponent())
        .in(builder);
  }

  @Test
  public void testBuilderMissingBuildMethodFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  @Component.Builder",
        "  interface Builder {}",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.missingBuildMethod())
        .in(componentFile);
  }

  @Test
  public void testPrivateBuilderFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  private interface Builder {}",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.isPrivate())
        .in(componentFile);
  }

  @Test
  public void testNonStaticBuilderFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  abstract class Builder {}",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.mustBeStatic())
        .in(componentFile);
  }

  @Test
  public void testNonAbstractBuilderFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  static class Builder {}",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.mustBeAbstract());
  }

  @Test
  public void testBuilderOneCxtorWithArgsFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  static abstract class Builder {",
        "    Builder(String unused) {}",
        "  }",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.cxtorOnlyOneAndNoArgs())
        .in(componentFile);
  }

  @Test
  public void testBuilderMoreThanOneCxtorFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  static abstract class Builder {",
        "    Builder() {}",
        "    Builder(String unused) {}",
        "  }",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.cxtorOnlyOneAndNoArgs())
        .in(componentFile);
  }

  @Test
  public void testBuilderEnumFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  enum Builder {}",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.mustBeClassOrInterface())
        .in(componentFile);
  }

  @Test
  public void testBuilderBuildReturnsWrongTypeFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  interface Builder {",
        "    String build();",
        "  }",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.buildMustReturnComponentType())
            .in(componentFile).onLine(10);
  }

  @Test
  public void testInheritedBuilderBuildReturnsWrongTypeFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  interface Parent {",
        "    String build();",
        "  }",
        "",
        "  @Component.Builder",
        "  interface Builder extends Parent {}",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.inheritedBuildMustReturnComponentType(), "build"))
            .in(componentFile).onLine(13);
  }

  @Test
  public void testTwoBuildMethodsFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  interface Builder {",
        "    SimpleComponent build();",
        "    SimpleComponent create();",
        "  }",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(String.format(MSGS.twoBuildMethods(), "build()"))
            .in(componentFile).onLine(11);
  }

  @Test
  public void testInheritedTwoBuildMethodsFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  interface Parent {",
        "    SimpleComponent build();",
        "    SimpleComponent create();",
        "  }",
        "",
        "  @Component.Builder",
        "  interface Builder extends Parent {}",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.inheritedTwoBuildMethods(), "create()", "build()"))
            .in(componentFile).onLine(14);
  }

  @Test
  public void testMoreThanOneArgFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  interface Builder {",
        "    SimpleComponent build();",
        "    Builder set(String s, Integer i);",
        "    Builder set(Number n, Double d);",
        "  }",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.methodsMustTakeOneArg())
            .in(componentFile).onLine(11)
        .and().withErrorContaining(MSGS.methodsMustTakeOneArg())
            .in(componentFile).onLine(12);
  }

  @Test
  public void testInheritedMoreThanOneArgFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  interface Parent {",
        "    SimpleComponent build();",
        "    Builder set1(String s, Integer i);",
        "  }",
        "",
        "  @Component.Builder",
        "  interface Builder extends Parent {}",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.inheritedMethodsMustTakeOneArg(),
                "set1(java.lang.String,java.lang.Integer)"))
            .in(componentFile).onLine(14);
  }

  @Test
  public void testSetterReturningNonVoidOrBuilderFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  interface Builder {",
        "    SimpleComponent build();",
        "    String set(Integer i);",
        "  }",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.methodsMustReturnVoidOrBuilder())
            .in(componentFile).onLine(11);
  }

  @Test
  public void testInheritedSetterReturningNonVoidOrBuilderFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  interface Parent {",
        "    SimpleComponent build();",
        "    String set(Integer i);",
        "  }",
        "",
        "  @Component.Builder",
        "  interface Builder extends Parent {}",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.inheritedMethodsMustReturnVoidOrBuilder(),
                "set(java.lang.Integer)"))
            .in(componentFile).onLine(14);
  }

  @Test
  public void testGenericsOnSetterMethodFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  interface Builder {",
        "    SimpleComponent build();",
        "    <T> Builder set(T t);",
        "  }",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(MSGS.methodsMayNotHaveTypeParameters())
            .in(componentFile).onLine(11);
  }

  @Test
  public void testGenericsOnInheritedSetterMethodFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  interface Parent {",
        "    SimpleComponent build();",
        "    <T> Builder set(T t);",
        "  }",
        "",
        "  @Component.Builder",
        "  interface Builder extends Parent {}",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.inheritedMethodsMayNotHaveTypeParameters(), "<T>set(T)"))
            .in(componentFile).onLine(14);
  }

  @Test
  public void testMultipleSettersPerTypeFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  @Component.Builder",
        "  interface Builder {",
        "    SimpleComponent build();",
        "    void set1(String s);",
        "    void set2(String s);",
        "  }",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.manyMethodsForType(),
                  "java.lang.String", "[set1(java.lang.String), set2(java.lang.String)]"))
            .in(componentFile).onLine(9);
  }

  @Test
  public void testMultipleSettersPerTypeIncludingResolvedGenericsFails() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "abstract class SimpleComponent {",
        "  interface Parent<T> {",
        "    void set1(T t);",
        "  }",
        "",
        "  @Component.Builder",
        "  interface Builder extends Parent<String> {",
        "    SimpleComponent build();",
        "    void set2(String s);",
        "  }",
        "}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(MSGS.manyMethodsForType(),
                  "java.lang.String", "[set1(T), set2(java.lang.String)]"))
            .in(componentFile).onLine(13);
  }

  @Test
  public void testExtraSettersFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = AbstractModule.class)",
            "abstract class SimpleComponent {",
            "  @Component.Builder",
            "  interface Builder {",
            "    SimpleComponent build();",
            "    void abstractModule(AbstractModule abstractModule);",
            "    void other(String s);",
            "  }",
            "}");
    JavaFileObject abstractModule =
        JavaFileObjects.forSourceLines(
            "test.AbstractModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class AbstractModule {}");
    assertAbout(javaSources())
        .that(ImmutableList.of(componentFile, abstractModule))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(
                MSGS.extraSetters(),
                "[void test.SimpleComponent.Builder.abstractModule(test.AbstractModule), "
                    + "void test.SimpleComponent.Builder.other(String)]"))
        .in(componentFile)
        .onLine(10);
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
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = {TestModule.class, Test2Module.class, Test3Module.class},",
        "           dependencies = OtherComponent.class)",
        "interface TestComponent {",
        "  String string();",
        "  Integer integer();",
        "",
        "  @Component.Builder",
        "  interface Builder {",
        "    TestComponent create();",
        "  }",
        "}");
    JavaFileObject otherComponent = JavaFileObjects.forSourceLines("test.OtherComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface OtherComponent {}");
    assertAbout(javaSources())
        .that(ImmutableList.of(moduleFile, module2File, module3File, componentFile, otherComponent))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            // Ignores Test2Module because we can construct it ourselves.
            // TODO(sameb): Ignore Test3Module because it's not used within transitive dependencies.
            String.format(MSGS.missingSetters(),
                "[test.TestModule, test.Test3Module, test.OtherComponent]"))
            .in(componentFile).onLine(12);
  }
}
