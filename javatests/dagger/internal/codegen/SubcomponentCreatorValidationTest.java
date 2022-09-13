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

import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.SUBCOMPONENT_BUILDER;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.SUBCOMPONENT_FACTORY;
import static dagger.internal.codegen.base.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.base.ComponentCreatorKind.FACTORY;
import static dagger.internal.codegen.base.ComponentKind.SUBCOMPONENT;
import static dagger.internal.codegen.binding.ErrorMessages.ComponentCreatorMessages.moreThanOneRefToSubcomponent;
import static dagger.internal.codegen.binding.ErrorMessages.componentMessagesFor;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.base.ComponentCreatorAnnotation;
import dagger.testing.compile.CompilerTests;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {@link dagger.Subcomponent.Builder} validation. */
@RunWith(Parameterized.class)
public class SubcomponentCreatorValidationTest extends ComponentCreatorTestHelper {
  @Parameters(name = "creatorKind={0}")
  public static Collection<Object[]> parameters() {
    return ImmutableList.copyOf(new Object[][] {{SUBCOMPONENT_BUILDER}, {SUBCOMPONENT_FACTORY}});
  }

  public SubcomponentCreatorValidationTest(ComponentCreatorAnnotation componentCreatorAnnotation) {
    super(DEFAULT_MODE, componentCreatorAnnotation);
  }

  @Test
  public void testRefSubcomponentAndSubCreatorFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent child();",
            "  ChildComponent.Builder childComponentBuilder();",
            "}");
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
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
    CompilerTests.daggerCompiler(componentFile, childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          moreThanOneRefToSubcomponent(),
                          "test.ChildComponent",
                          process("["
                              + "test.ParentComponent.child(), "
                              + "test.ParentComponent.childComponentBuilder()"
                              + "]")))
                  .onSource(componentFile);
            });
  }

  @Test
  public void testRefSubCreatorTwiceFails() {
    Source componentFile = preprocessedJavaSource("test.ParentComponent",
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
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
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
    CompilerTests.daggerCompiler(componentFile, childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          moreThanOneRefToSubcomponent(),
                          "test.ChildComponent",
                          process("["
                              + "test.ParentComponent.builder1(), "
                              + "test.ParentComponent.builder2()"
                              + "]")))
                  .onSource(componentFile);
            });
  }

  @Test
  public void testMoreThanOneCreatorFails() {
    Source componentFile = preprocessedJavaSource("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder1 build();",
        "}");
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
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
    CompilerTests.daggerCompiler(componentFile, childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          componentMessagesFor(SUBCOMPONENT).moreThanOne(),
                          process("[test.ChildComponent.Builder1, test.ChildComponent.Builder2]")))
                  .onSource(childComponentFile);
            });
  }

  @Test
  public void testMoreThanOneCreatorFails_differentTypes() {
    Source componentFile = preprocessedJavaSource("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder build();",
        "}");
    Source childComponentFile =
        CompilerTests.javaSource("test.ChildComponent",
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
        "",
        "  @Subcomponent.Factory",
        "  static interface Factory {",
        "    ChildComponent create();",
        "  }",
        "}");
    CompilerTests.daggerCompiler(componentFile, childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          componentMessagesFor(SUBCOMPONENT).moreThanOne(),
                          "[test.ChildComponent.Builder, test.ChildComponent.Factory]"))
                  .onSource(childComponentFile);
            });
  }

  @Test
  public void testCreatorGenericsFails() {
    Source componentFile = preprocessedJavaSource("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder build();",
        "}");
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
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
    CompilerTests.daggerCompiler(componentFile, childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.generics()).onSource(childComponentFile);
            });
  }

  @Test
  public void testCreatorNotInComponentFails() {
    Source builder = preprocessedJavaSource("test.Builder",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent.Builder",
        "interface Builder {}");
    CompilerTests.daggerCompiler(builder)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.mustBeInComponent()).onSource(builder);
            });
  }

  @Test
  public void testCreatorMissingFactoryMethodFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent.Builder childComponentBuilder();",
            "}");
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {}",
        "}");
    CompilerTests.daggerCompiler(componentFile, childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.missingFactoryMethod())
                  .onSource(childComponentFile);
            });
  }

  @Test
  public void testPrivateCreatorFails() {
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  private interface Builder {}",
        "}");
    CompilerTests.daggerCompiler(childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.isPrivate()).onSource(childComponentFile);
            });
  }

  @Test
  public void testNonStaticCreatorFails() {
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  abstract class Builder {}",
        "}");
    CompilerTests.daggerCompiler(childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.mustBeStatic()).onSource(childComponentFile);
            });
  }

  @Test
  public void testNonAbstractCreatorFails() {
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  static class Builder {}",
        "}");
    CompilerTests.daggerCompiler(childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.mustBeAbstract())
                  .onSource(childComponentFile);
            });
  }

  @Test
  public void testCreatorOneConstructorWithArgsFails() {
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  static abstract class Builder {",
        "    Builder(String unused) {}",
        "    abstract ChildComponent build();",
        "  }",
        "}");
    CompilerTests.daggerCompiler(childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.invalidConstructor())
                  .onSource(childComponentFile);
            });
  }

  @Test
  public void testCreatorMoreThanOneConstructorFails() {
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
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
        "    abstract ChildComponent build();",
        "  }",
        "}");
    CompilerTests.daggerCompiler(childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.invalidConstructor())
                  .onSource(childComponentFile);
            });
  }

  @Test
  public void testCreatorEnumFails() {
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  enum Builder {}",
        "}");
    CompilerTests.daggerCompiler(childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.mustBeClassOrInterface())
                  .onSource(childComponentFile);
            });
  }

  @Test
  public void testCreatorFactoryMethodReturnsWrongTypeFails() {
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
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
    CompilerTests.daggerCompiler(childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.factoryMethodMustReturnComponentType())
                  .onSource(childComponentFile)
                  .onLine(9);
            });
  }

  @Test
  public void testInheritedCreatorFactoryMethodReturnsWrongTypeFails() {
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
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

    CompilerTests.daggerCompiler(childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          messages.inheritedFactoryMethodMustReturnComponentType(),
                          process("String test.ChildComponent.Parent.build()")))
                  .onSource(childComponentFile)
                  .onLine(12);
            });
  }

  @Test
  public void testTwoFactoryMethodsFails() {
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    ChildComponent build1();",
        "  }",
        "}");

    CompilerTests.daggerCompiler(childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          messages.twoFactoryMethods(),
                          process("test.ChildComponent test.ChildComponent.Builder.build()")))
                  .onSource(childComponentFile)
                  .onLine(10);
            });
  }

  @Test
  public void testInheritedTwoFactoryMethodsFails() {
    Source childComponentFile = preprocessedJavaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    ChildComponent build1();",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");

    CompilerTests.daggerCompiler(childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          messages.inheritedTwoFactoryMethods(),
                          process("test.ChildComponent test.ChildComponent.Parent.build()"),
                          process("test.ChildComponent test.ChildComponent.Parent.build1()")))
                  .onSource(childComponentFile)
                  .onLine(13);
            });
  }

  @Test
  public void testMultipleSettersPerTypeFails() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides String s() { return \"\"; }",
            "}");
    Source componentFile =
        preprocessedJavaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent.Builder childComponentBuilder();",
            "}");
    Source childComponentFile =
        javaFileBuilder("test.ChildComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Subcomponent;",
                "import javax.inject.Provider;",
                "",
                "@Subcomponent(modules = TestModule.class)",
                "abstract class ChildComponent {",
                "  abstract String s();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Subcomponent.Builder",
                "  interface Builder {",
                "    ChildComponent build();",
                "    void set1(TestModule s);",
                "    void set2(TestModule s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Subcomponent.Factory",
                "  interface Factory {",
                "    ChildComponent create(TestModule m1, TestModule m2);",
                "  }")
            .addLines( //
                "}")
            .buildSource();

    String elements =
        creatorKind.equals(BUILDER)
            ? "[void test.ChildComponent.Builder.set1(test.TestModule), "
                + "void test.ChildComponent.Builder.set2(test.TestModule)]"
            : "[test.TestModule m1, test.TestModule m2]";
    CompilerTests.daggerCompiler(moduleFile, componentFile, childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.format(
                      messages.multipleSettersForModuleOrDependencyType(),
                      "test.TestModule",
                      elements))
                  .onSource(childComponentFile)
                  .onLine(11);
            });
  }

  @Test
  public void testMultipleSettersPerTypeIncludingResolvedGenericsFails() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides String s() { return \"\"; }",
            "}");
    Source componentFile =
        preprocessedJavaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent.Builder childComponentBuilder();",
            "}");
    Source childComponentFile =
        javaFileBuilder("test.ChildComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Subcomponent;",
                "import javax.inject.Provider;",
                "",
                "@Subcomponent(modules = TestModule.class)",
                "abstract class ChildComponent {",
                "  abstract String s();",
                "")
            .addLinesIf(
                BUILDER,
                "  interface Parent<T> {",
                "    void set1(T t);",
                "  }",
                "",
                "  @Subcomponent.Builder",
                "  interface Builder extends Parent<TestModule> {",
                "    ChildComponent build();",
                "    void set2(TestModule s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  interface Parent<C, T> {",
                "    C create(TestModule m1, T t);",
                "  }",
                "",
                "  @Subcomponent.Factory",
                "  interface Factory extends Parent<ChildComponent, TestModule> {}")
            .addLines( //
                "}")
            .buildSource();

    String elements =
        creatorKind.equals(BUILDER)
            ? "[void test.ChildComponent.Builder.set1(test.TestModule), "
                + "void test.ChildComponent.Builder.set2(test.TestModule)]"
            : "[test.TestModule m1, test.TestModule t]";
    CompilerTests.daggerCompiler(moduleFile, componentFile, childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  String.format(
                      messages.multipleSettersForModuleOrDependencyType(),
                      "test.TestModule",
                      elements))
                  .onSource(childComponentFile)
                  .onLine(15);
            });
  }

  @Test
  public void testMultipleSettersPerBoundInstanceTypeFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent.Builder childComponentBuilder();",
            "}");
    Source childComponentFile =
        javaFileBuilder("test.ChildComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.BindsInstance;",
                "import dagger.Subcomponent;",
                "",
                "@Subcomponent",
                "abstract class ChildComponent {",
                "  abstract String s();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Subcomponent.Builder",
                "  interface Builder {",
                "    ChildComponent build();",
                "    @BindsInstance void set1(String s);",
                "    @BindsInstance void set2(String s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Subcomponent.Factory",
                "  interface Factory {",
                "    ChildComponent create(",
                "        @BindsInstance String s1, @BindsInstance String s2);",
                "  }")
            .addLines( //
                "}")
            .buildSource();

    String firstBinding = creatorKind.equals(FACTORY)
        ? "ChildComponent.Factory.create(s1, …)"
        : "@BindsInstance void ChildComponent.Builder.set1(String)";
    String secondBinding = creatorKind.equals(FACTORY)
        ? "ChildComponent.Factory.create(…, s2)"
        : "@BindsInstance void ChildComponent.Builder.set2(String)";
    CompilerTests.daggerCompiler(componentFile, childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("String is bound multiple times:");
              subject.hasErrorContaining("    " + firstBinding);
              subject.hasErrorContaining("    " + secondBinding);
              subject.hasErrorContaining("    in component: [ParentComponent → ChildComponent]")
                  .onSource(componentFile)
                  .onLineContaining("interface ParentComponent {");
            });
  }

  @Test
  public void testExtraSettersFails() {
    Source componentFile = preprocessedJavaSource("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface ParentComponent {",
        "  ChildComponent.Builder build();",
        "}");
    Source childComponentFile =
        javaFileBuilder("test.ChildComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Subcomponent;",
                "import javax.inject.Provider;",
                "",
                "@Subcomponent",
                "abstract class ChildComponent {")
            .addLinesIf(
                BUILDER,
                "  @Subcomponent.Builder",
                "  interface Builder {",
                "    ChildComponent build();",
                "    void set1(String s);",
                "    void set2(Integer s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Subcomponent.Factory",
                "  interface Factory {",
                "    ChildComponent create(String s, Integer i);",
                "  }")
            .addLines("}")
            .buildSource();

    String elements =
        creatorKind.equals(FACTORY)
            ? "[String s, Integer i]"
            : "[void test.ChildComponent.Builder.set1(String),"
                + " void test.ChildComponent.Builder.set2(Integer)]";
    CompilerTests.daggerCompiler(componentFile, childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(String.format(messages.extraSetters(), elements))
                  .onSource(childComponentFile)
                  .onLine(9);
            });
  }

  @Test
  public void testMissingSettersFail() {
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
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
    Source module2File =
        CompilerTests.javaSource(
            "test.Test2Module",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class Test2Module {",
            "  @Provides Integer i() { return null; }",
            "}");
    Source module3File =
        CompilerTests.javaSource(
            "test.Test3Module",
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
    Source componentFile =
        preprocessedJavaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent.Builder build();",
            "}");
    Source childComponentFile =
        preprocessedJavaSource(
            "test.ChildComponent",
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
            "    ChildComponent build();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(
            moduleFile, module2File, module3File, componentFile, childComponentFile)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      // Ignores Test2Module because we can construct it ourselves.
                      // TODO(sameb): Ignore Test3Module because it's not used within transitive
                      // dependencies.
                      String.format(
                          messages.missingSetters(),
                          "[test.TestModule, test.Test3Module]"))
                  .onSource(childComponentFile)
                  .onLine(11);
            });
  }

  @Test
  public void covariantFactoryMethodReturnType() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    Source supertype =
        CompilerTests.javaSource(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  Foo foo();",
            "}");

    Source subcomponent =
        preprocessedJavaSource(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface HasSupertype extends Supertype {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Supertype build();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(foo, supertype, subcomponent)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }

  @Test
  public void covariantFactoryMethodReturnType_hasNewMethod() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    Source supertype =
        CompilerTests.javaSource(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  Foo foo();",
            "}");

    Source subcomponent =
        preprocessedJavaSource(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface HasSupertype extends Supertype {",
            "  Bar bar();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Supertype build();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(foo, bar, supertype, subcomponent)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(1);
              subject.hasWarningContaining(
                      process(
                          "test.HasSupertype.Builder.build() returns test.Supertype, but "
                              + "test.HasSupertype declares additional component method(s): bar(). "
                              + "In order to provide type-safe access to these methods, override "
                              + "build() to return test.HasSupertype"))
                  .onSource(subcomponent)
                  .onLine(11);
            });
  }

  @Test
  public void covariantFactoryMethodReturnType_hasNewMethod_buildMethodInherited() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    Source supertype =
        CompilerTests.javaSource(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  Foo foo();",
            "}");

    Source creatorSupertype =
        preprocessedJavaSource(
            "test.CreatorSupertype",
            "package test;",
            "",
            "interface CreatorSupertype {",
            "  Supertype build();",
            "}");

    Source subcomponent =
        preprocessedJavaSource(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface HasSupertype extends Supertype {",
            "  Bar bar();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder extends CreatorSupertype {}",
            "}");

    CompilerTests.daggerCompiler(foo, bar, supertype, creatorSupertype, subcomponent)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(1);
              subject.hasWarningContaining(
                  process(
                      "[test.CreatorSupertype.build()] test.HasSupertype.Builder.build() returns "
                          + "test.Supertype, but test.HasSupertype declares additional component "
                          + "method(s): bar(). In order to provide type-safe access to these "
                          + "methods, override build() to return test.HasSupertype"));
            });
  }
}
