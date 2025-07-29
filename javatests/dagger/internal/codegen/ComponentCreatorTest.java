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

import static com.google.common.truth.TruthJUnit.assume;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.ComponentCreatorTest.CompilerType.JAVAC;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.COMPONENT_BUILDER;
import static dagger.internal.codegen.base.ComponentCreatorAnnotation.COMPONENT_FACTORY;
import static dagger.internal.codegen.base.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.base.ComponentCreatorKind.FACTORY;
import static dagger.internal.codegen.base.ComponentKind.COMPONENT;
import static dagger.internal.codegen.binding.ErrorMessages.componentMessagesFor;

import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.util.CompilationResultSubject;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.internal.codegen.base.ComponentCreatorAnnotation;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for properties of component creators shared by both builders and factories. */
@RunWith(Parameterized.class)
public class ComponentCreatorTest extends ComponentCreatorTestHelper {
  enum CompilerType {
    JAVAC
  }

  private final CompilerType compilerType;
  private final ImmutableMap<String, String> compilerOptions;

  @Parameters(name = "compilerMode={0}, creatorKind={1}")
  public static Collection<Object[]> parameters() {
    return ImmutableList.of(
      new Object[]{DEFAULT_MODE, COMPONENT_BUILDER, JAVAC},
      new Object[]{DEFAULT_MODE, COMPONENT_FACTORY, JAVAC},
      new Object[]{FAST_INIT_MODE, COMPONENT_BUILDER, JAVAC},
      new Object[]{FAST_INIT_MODE, COMPONENT_FACTORY, JAVAC});
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  public ComponentCreatorTest(
      CompilerMode compilerMode,
      ComponentCreatorAnnotation componentCreatorAnnotation,
      CompilerType compilerType) {
    super(compilerMode, componentCreatorAnnotation);
    this.compilerType = compilerType;
    this.compilerOptions =
        ImmutableMap.<String, String>builder()
            .putAll(compilerMode.processorOptions())
            .build();
  }

  @Test
  public void testEmptyCreator() throws Exception {
    assume().that(compilerType).isEqualTo(JAVAC);
    Source injectableTypeFile =
        CompilerTests.javaSource(
            "test.SomeInjectableType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class SomeInjectableType {",
            "  @Inject SomeInjectableType() {}",
            "}");
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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

    CompilerTests.daggerCompiler(injectableTypeFile, componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerSimpleComponent"));
            });
  }

  @Test
  public void testCanInstantiateModulesUserCannotSet() throws Exception {
    assume().that(compilerType).isEqualTo(JAVAC);
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides String string() { return null; }",
            "}");

    Source componentFile =
        preprocessedJavaSource(
            "test.TestComponent",
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
            "    TestComponent build();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(module, componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void testMoreThanOneCreatorOfSameTypeFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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

    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              String formattedList =
                  formattedList(
                      subject,
                      "test.SimpleComponent.Builder",
                      "test.SimpleComponent.Builder2");
              subject.hasErrorContainingMatch(
                      String.format(
                          componentMessagesFor(COMPONENT).moreThanOne(),
                          process(formattedList)))
                  .onSource(componentFile);
            });
  }

  @Test
  public void testBothBuilderAndFactoryFails() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.SimpleComponent",
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
            "  @Component.Factory",
            "  interface Factory {",
            "     SimpleComponent create();",
            "  }",
            "}");
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              String formattedList =
                  formattedList(
                      subject,
                      "test.SimpleComponent.Builder",
                      "test.SimpleComponent.Factory");
              subject.hasErrorContainingMatch(
                      String.format(componentMessagesFor(COMPONENT).moreThanOne(), formattedList))
                  .onSource(componentFile);
            });
  }

  @Test
  public void testGenericCreatorTypeFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.generics()).onSource(componentFile);
            });
  }

  @Test
  public void testCreatorNotInComponentFails() {
    Source builder =
        preprocessedJavaSource(
            "test.Builder",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component.Builder",
            "interface Builder {}");
    CompilerTests.daggerCompiler(builder)
        .withProcessingOptions(compilerOptions)
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
            "test.SimpleComponent",
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
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.missingFactoryMethod())
                  .onSource(componentFile);
            });
  }

  @Test
  public void testCreatorWithBindsInstanceNoStaticCreateGenerated() throws Exception {
    assume().that(compilerType).isEqualTo(JAVAC);
    Source componentFile =
        javaFileBuilder("test.SimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.BindsInstance;",
                "import dagger.Component;",
                "import javax.inject.Provider;",
                "",
                "@Component",
                "interface SimpleComponent {",
                "  Object object();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Component.Builder",
                "  interface Builder {",
                "    @BindsInstance Builder object(Object object);",
                "    SimpleComponent build();",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Component.Factory",
                "  interface Factory {",
                "    SimpleComponent create(@BindsInstance Object object);",
                "  }")
            .addLines("}")
            .buildSource();

    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerSimpleComponent"));
            });
  }

  @Test
  public void testCreatorWithPrimitiveBindsInstance() throws Exception {
    assume().that(compilerType).isEqualTo(JAVAC);
    Source componentFile =
        javaFileBuilder("test.SimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.BindsInstance;",
                "import dagger.Component;",
                "import javax.inject.Provider;",
                "",
                "@Component",
                "interface SimpleComponent {",
                "  int anInt();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Component.Builder",
                "  interface Builder {",
                "    @BindsInstance Builder i(int i);",
                "    SimpleComponent build();",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Component.Factory",
                "  interface Factory {",
                "    SimpleComponent create(@BindsInstance int i);",
                "  }")
            .addLines(
                "}")
            .buildSource();

    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerSimpleComponent"));
            });
  }

  @Test
  public void testPrivateCreatorFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.isPrivate()).onSource(componentFile);
            });
  }

  @Test
  public void testNonStaticCreatorFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.mustBeStatic()).onSource(componentFile);
            });
  }

  @Test
  public void testNonAbstractCreatorFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.mustBeAbstract()).onSource(componentFile);
            });
  }

  @Test
  public void testCreatorOneConstructorWithArgsFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(messages.invalidConstructor())
                  .onSource(componentFile);
            });
  }

  @Test
  public void testCreatorMoreThanOneConstructorFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining(messages.invalidConstructor())
                  .onSource(componentFile);
            });
  }

  @Test
  public void testCreatorEnumFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.mustBeClassOrInterface())
                  .onSource(componentFile);
            });
  }

  @Test
  public void testCreatorFactoryMethodReturnsWrongTypeFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.factoryMethodMustReturnComponentType())
                  .onSource(componentFile)
                  .onLineContaining(process("String build();"));
            });
  }

  @Test
  public void testCreatorSetterForNonBindsInstancePrimitiveFails() {
    Source component =
        javaFileBuilder("test.TestComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Component;",
                "",
                "@Component",
                "interface TestComponent {",
                "  Object object();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Component.Builder",
                "  interface Builder {",
                "    Builder primitive(long l);",
                "    TestComponent build();",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Component.Factory",
                "  interface Factory {",
                "    TestComponent create(long l);",
                "  }")
            .addLines( //
                "}")
            .buildSource();
    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.nonBindsInstanceParametersMayNotBePrimitives())
                  .onSource(component)
                  .onLineContaining("(long l)");
            });
  }

  @Test
  public void testInheritedBuilderBuildReturnsWrongTypeFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                    String.format(
                        messages.inheritedFactoryMethodMustReturnComponentType(),
                        process("String test.SimpleComponent.Parent.build()")))
                  .onSource(componentFile)
                  .onLineContaining(process("interface Builder"));
            });
  }

  @Test
  public void testTwoFactoryMethodsFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
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
            "    SimpleComponent newSimpleComponent();",
            "  }",
            "}");
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          messages.twoFactoryMethods(),
                          process("test.SimpleComponent test.SimpleComponent.Builder.build()")))
                  .onSource(componentFile)
                  .onLineContaining("SimpleComponent newSimpleComponent();");
            });
  }

  @Test
  public void testInheritedTwoFactoryMethodsFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  interface Parent {",
            "    SimpleComponent build();",
            "    SimpleComponent newSimpleComponent();",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          messages.inheritedTwoFactoryMethods(),
                          process("test.SimpleComponent test.SimpleComponent.Parent.build()"),
                          "test.SimpleComponent test.SimpleComponent.Parent.newSimpleComponent()"))
                  .onSource(componentFile)
                  .onLineContaining(process("interface Builder"));
            });
  }

  @Test
  public void testMultipleSettersPerTypeFails() {
    assume().that(compilerType).isEqualTo(JAVAC);
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
        javaFileBuilder("test.SimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Component;",
                "import javax.inject.Provider;",
                "",
                "@Component(modules = TestModule.class)",
                "abstract class SimpleComponent {",
                "  abstract String s();",
                "")
            .addLinesIf(
                BUILDER,
                "  @Component.Builder",
                "  interface Builder {",
                "    SimpleComponent build();",
                "    void set1(TestModule s);",
                "    void set2(TestModule s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Component.Factory",
                "  interface Factory {",
                "    SimpleComponent create(TestModule m1, TestModule m2);",
                "  }")
            .addLines( //
                "}")
            .buildSource();
    CompilerTests.daggerCompiler(moduleFile, componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              String elements =
                  creatorKind.equals(BUILDER)
                      ? "[void test.SimpleComponent.Builder.set1(test.TestModule), "
                          + "void test.SimpleComponent.Builder.set2(test.TestModule)]"
                      : "[test.TestModule m1, test.TestModule m2]";
              subject.hasErrorContaining(
                      String.format(
                          messages.multipleSettersForModuleOrDependencyType(),
                          "test.TestModule",
                          elements))
                  .onSource(componentFile)
                  .onLineContaining(process("interface Builder"));
            });
  }

  @Test
  public void testMultipleSettersPerTypeIncludingResolvedGenericsFails() {
    assume().that(compilerType).isEqualTo(JAVAC);
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
        javaFileBuilder("test.SimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Component;",
                "import javax.inject.Provider;",
                "",
                "@Component(modules = TestModule.class)",
                "abstract class SimpleComponent {",
                "  abstract String s();",
                "")
            .addLinesIf(
                BUILDER,
                "  interface Parent<T> {",
                "    void set1(T t);",
                "  }",
                "",
                "  @Component.Builder",
                "  interface Builder extends Parent<TestModule> {",
                "    SimpleComponent build();",
                "    void set2(TestModule s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  interface Parent<C, T> {",
                "    C create(TestModule m1, T t);",
                "  }",
                "",
                "  @Component.Factory",
                "  interface Factory extends Parent<SimpleComponent, TestModule> {}")
            .addLines( //
                "}")
            .buildSource();
    CompilerTests.daggerCompiler(moduleFile, componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              String elements =
                  creatorKind.equals(BUILDER)
                      ? "[void test.SimpleComponent.Builder.set1(test.TestModule), "
                          + "void test.SimpleComponent.Builder.set2(test.TestModule)]"
                      : "[test.TestModule m1, test.TestModule t]";
              subject.hasErrorContaining(
                      String.format(
                          messages.multipleSettersForModuleOrDependencyType(),
                          "test.TestModule",
                          elements))
                  .onSource(componentFile)
                  .onLineContaining(process("interface Builder"));
            });
  }

  @Test
  public void testExtraSettersFails() {
    assume().that(compilerType).isEqualTo(JAVAC);
    Source componentFile =
        javaFileBuilder("test.SimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Component;",
                "import javax.inject.Provider;",
                "",
                "@Component(modules = AbstractModule.class)",
                "abstract class SimpleComponent {")
            .addLinesIf(
                BUILDER,
                "  @Component.Builder",
                "  interface Builder {",
                "    SimpleComponent build();",
                "    void abstractModule(AbstractModule abstractModule);",
                "    void other(String s);",
                "  }")
            .addLinesIf(
                FACTORY,
                "  @Component.Factory",
                "  interface Factory {",
                "    SimpleComponent create(AbstractModule abstractModule, String s);",
                "  }")
            .addLines("}")
            .buildSource();
    Source abstractModule =
        CompilerTests.javaSource(
            "test.AbstractModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class AbstractModule {}");
    CompilerTests.daggerCompiler(componentFile, abstractModule)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              String elements =
                  creatorKind.equals(BUILDER)
                      ? "[void test.SimpleComponent.Builder.abstractModule(test.AbstractModule), "
                          + "void test.SimpleComponent.Builder.other(String)]"
                      : "[test.AbstractModule abstractModule, String s]";
              subject.hasErrorContaining(String.format(messages.extraSetters(), elements))
                  .onSource(componentFile)
                  .onLineContaining(process("interface Builder"));
            });
  }

  @Test
  public void testMissingSettersFail() {
    assume().that(compilerType).isEqualTo(JAVAC);
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
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(",
            "    modules = {TestModule.class, Test2Module.class, Test3Module.class},",
            "    dependencies = OtherComponent.class",
            ")",
            "interface TestComponent {",
            "  String string();",
            "  Integer integer();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    TestComponent create();",
            "  }",
            "}");
    Source otherComponent =
        CompilerTests.javaSource(
            "test.OtherComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface OtherComponent {}");
    CompilerTests.daggerCompiler(
            moduleFile, module2File, module3File, componentFile, otherComponent)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      // Ignores Test2Module because we can construct it ourselves.
                      // TODO(sameb): Ignore Test3Module because it's not used within transitive
                      // dependencies.
                      String.format(
                          messages.missingSetters(),
                          "[test.TestModule, test.Test3Module, test.OtherComponent]"))
                  .onSource(componentFile)
                  .onLineContaining(process("interface Builder"));
            });
  }

  @Test
  public void covariantFactoryMethodReturnType() {
    assume().that(compilerType).isEqualTo(JAVAC);
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

    Source component =
        preprocessedJavaSource(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface HasSupertype extends Supertype {",
            "  @Component.Builder",
            "  interface Builder {",
            "    Supertype build();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(foo, supertype, component)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
            });
  }

  @Test
  public void covariantFactoryMethodReturnType_hasNewMethod() {
    assume().that(compilerType).isEqualTo(JAVAC);
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

    Source component =
        preprocessedJavaSource(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface HasSupertype extends Supertype {",
            "  Bar bar();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    Supertype build();",
            "  }",
            "}");

    CompilerTests.daggerCompiler(foo, bar, supertype, component)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningContaining(
                      process(
                          "test.HasSupertype.Builder.build() returns test.Supertype, but "
                              + "test.HasSupertype declares additional component method(s): bar(). "
                              + "In order to provide type-safe access to these methods, override "
                              + "build() to return test.HasSupertype"))
                  .onSource(component)
                  .onLine(11);
            });
  }

  @Test
  public void covariantFactoryMethodReturnType_hasNewMethod_factoryMethodInherited() {
    assume().that(compilerType).isEqualTo(JAVAC);
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

    Source component =
        preprocessedJavaSource(
            "test.HasSupertype",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface HasSupertype extends Supertype {",
            "  Bar bar();",
            "",
            "  @Component.Builder",
            "  interface Builder extends CreatorSupertype {}",
            "}");

    CompilerTests.daggerCompiler(foo, bar, supertype, creatorSupertype, component)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningContaining(
                  process(
                      "test.HasSupertype.Builder.build() returns test.Supertype, but "
                          + "test.HasSupertype declares additional component method(s): bar(). "
                          + "In order to provide type-safe access to these methods, override "
                          + "build() to return test.HasSupertype"));
            });
  }

  @Test
  public void testGenericsOnFactoryMethodFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  @Component.Builder",
            "  interface Builder {",
            "    <T> SimpleComponent build();",
            "  }",
            "}");
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(messages.methodsMayNotHaveTypeParameters())
                  .onSource(componentFile)
                  .onLineContaining(process("<T> SimpleComponent build();"));
            });
  }

  @Test
  public void testGenericsOnInheritedFactoryMethodFails() {
    Source componentFile =
        preprocessedJavaSource(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "abstract class SimpleComponent {",
            "  interface Parent {",
            "    <T> SimpleComponent build();",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    CompilerTests.daggerCompiler(componentFile)
        .withProcessingOptions(compilerOptions)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      String.format(
                          messages.inheritedMethodsMayNotHaveTypeParameters(),
                          process("test.SimpleComponent test.SimpleComponent.Parent.build()")))
                  .onSource(componentFile)
                  .onLineContaining(process("interface Builder"));
            });
  }

  private static String formattedList(
      CompilationResultSubject subject, String element1, String element2) {
    return
        CompilerTests.backend(subject) == XProcessingEnv.Backend.KSP
            // TODO(b/381556660): KSP2 reports the elements in arbitrary order so check both orders.
            ? String.format("(\\[%s, %s\\]|\\[%s, %s\\])", element1, element2, element2, element1)
            : String.format("\\[%s, %s\\]", element1, element2);
  }
}
