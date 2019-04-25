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

import static com.google.common.collect.Sets.immutableEnumSet;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.ComponentCreatorAnnotation.COMPONENT_BUILDER;
import static dagger.internal.codegen.ComponentCreatorAnnotation.COMPONENT_FACTORY;
import static dagger.internal.codegen.ComponentCreatorKind.BUILDER;
import static dagger.internal.codegen.ComponentCreatorKind.FACTORY;
import static dagger.internal.codegen.ComponentKind.COMPONENT;
import static dagger.internal.codegen.ErrorMessages.componentMessagesFor;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for properties of component creators shared by both builders and factories. */
@RunWith(Parameterized.class)
public class ComponentCreatorTest extends ComponentCreatorTestHelper {
  @Parameters(name = "compilerMode={0}, creatorKind={1}")
  public static Collection<Object[]> parameters() {
    Set<List<Object>> params =
        Sets.<Object>cartesianProduct(
            immutableEnumSet(DEFAULT_MODE, FAST_INIT_MODE),
            immutableEnumSet(COMPONENT_BUILDER, COMPONENT_FACTORY));
    return ImmutableList.copyOf(Iterables.transform(params, Collection::toArray));
  }

  public ComponentCreatorTest(
      CompilerMode compilerMode, ComponentCreatorAnnotation componentCreatorAnnotation) {
    super(compilerMode, componentCreatorAnnotation);
  }

  @Test
  public void testEmptyCreator() {
    JavaFileObject injectableTypeFile =
        JavaFileObjects.forSourceLines(
            "test.SomeInjectableType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class SomeInjectableType {",
            "  @Inject SomeInjectableType() {}",
            "}");
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    JavaFileObject generatedComponent =
        preprocessedJavaFile(
            "test.DaggerSimpleComponent",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerSimpleComponent implements SimpleComponent {",
            "  private static final class Builder implements SimpleComponent.Builder {",
            "    @Override",
            "    public SimpleComponent build() {",
            "      return new DaggerSimpleComponent();",
            "    }",
            "  }",
            "}");
    Compilation compilation = compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void testCanInstantiateModulesUserCannotSet() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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

    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    JavaFileObject generatedComponent =
        preprocessedJavaFile(
            "test.DaggerTestComponent",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  private final TestModule testModule;",
            "",
            "  private DaggerTestComponent(TestModule testModuleParam) {",
            "    this.testModule = testModuleParam;",
            "  }",
            "",
            "  public static TestComponent.Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public String string() {",
            "    return TestModule_StringFactory.string(testModule);",
            "  }",
            "",
            "  private static final class Builder implements TestComponent.Builder {",
            "    @Override",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(new TestModule());",
            "    }",
            "  }",
            "}");
    Compilation compilation = compile(module, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void testMoreThanOneCreatorOfSameTypeFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                componentMessagesFor(COMPONENT).moreThanOne(),
                process("[test.SimpleComponent.Builder, test.SimpleComponent.Builder2]")))
        .inFile(componentFile);
  }

  @Test
  public void testBothBuilderAndFactoryFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                componentMessagesFor(COMPONENT).moreThanOne(),
                "[test.SimpleComponent.Builder, test.SimpleComponent.Factory]"))
        .inFile(componentFile);
  }

  @Test
  public void testGenericCreatorTypeFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(messages.generics()).inFile(componentFile);
  }

  @Test
  public void testCreatorNotInComponentFails() {
    JavaFileObject builder =
        preprocessedJavaFile(
            "test.Builder",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component.Builder",
            "interface Builder {}");
    Compilation compilation = compile(builder);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(messages.mustBeInComponent()).inFile(builder);
  }

  @Test
  public void testCreatorMissingFactoryMethodFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(messages.missingFactoryMethod())
        .inFile(componentFile);
  }

  @Test
  public void testCreatorWithBindsInstanceNoStaticCreateGenerated() {
    JavaFileObject componentFile =
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
            .build();

    JavaFileObject generatedComponent =
        javaFileBuilder("test.DaggerSimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.internal.Preconditions;",
                IMPORT_GENERATED_ANNOTATION,
                "",
                GENERATED_ANNOTATION,
                "final class DaggerSimpleComponent implements SimpleComponent {",
                "  private final Object object;",
                "",
                "  private DaggerSimpleComponent(Object objectParam) {",
                "    this.object = objectParam;",
                "  }",
                "")
            .addLinesIf(
                BUILDER,
                "  public static SimpleComponent.Builder builder() {",
                "    return new Builder();",
                "  }")
            .addLinesIf(
                FACTORY,
                "  public static SimpleComponent.Factory factory() {",
                "    return new Factory();",
                "  }")
            .addLines(
                "", //
                "  @Override",
                "  public Object object() {",
                "    return object;",
                "  }",
                "")
            .addLinesIf(
                BUILDER,
                "  private static final class Builder implements SimpleComponent.Builder {",
                "    private Object object;",
                "",
                "    @Override",
                "    public Builder object(Object object) {",
                "      this.object = Preconditions.checkNotNull(object);",
                "      return this;",
                "    }",
                "",
                "    @Override",
                "    public SimpleComponent build() {",
                "      Preconditions.checkBuilderRequirement(object, Object.class);",
                "      return new DaggerSimpleComponent(object);",
                "    }",
                "  }")
            .addLinesIf(
                FACTORY,
                "  private static final class Factory implements SimpleComponent.Factory {",
                "    @Override",
                "    public SimpleComponent create(Object object) {",
                "      Preconditions.checkNotNull(object);",
                "      return new DaggerSimpleComponent(object);",
                "    }",
                "  }")
            .addLines("}")
            .build();

    Compilation compilation = compile(componentFile);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void testCreatorWithPrimitiveBindsInstance() {
    JavaFileObject componentFile =
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
            .build();

    JavaFileObject generatedComponent =
        javaFileBuilder("test.DaggerSimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.internal.Preconditions;",
                IMPORT_GENERATED_ANNOTATION,
                "",
                GENERATED_ANNOTATION,
                "final class DaggerSimpleComponent implements SimpleComponent {",
                "  private final Integer i;",
                "",
                "  private DaggerSimpleComponent(Integer iParam) {",
                "    this.i = iParam;",
                "  }",
                "",
                "  @Override",
                "  public int anInt() {",
                "    return i;",
                "  }",
                "")
            .addLinesIf(
                BUILDER,
                "  private static final class Builder implements SimpleComponent.Builder {",
                "    private Integer i;",
                "",
                "    @Override",
                "    public Builder i(int i) {",
                "      this.i = Preconditions.checkNotNull(i);",
                "      return this;",
                "    }",
                "",
                "    @Override",
                "    public SimpleComponent build() {",
                "      Preconditions.checkBuilderRequirement(i, Integer.class);",
                "      return new DaggerSimpleComponent(i);",
                "    }",
                "  }")
            .addLinesIf(
                FACTORY,
                "  private static final class Factory implements SimpleComponent.Factory {",
                "    @Override",
                "    public SimpleComponent create(int i) {",
                "      Preconditions.checkNotNull(i);",
                "      return new DaggerSimpleComponent(i);",
                "    }",
                "  }")
            .addLines(
                "}")
            .build();

    Compilation compilation = compile(componentFile);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void testPrivateCreatorFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(messages.isPrivate()).inFile(componentFile);
  }

  @Test
  public void testNonStaticCreatorFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(messages.mustBeStatic()).inFile(componentFile);
  }

  @Test
  public void testNonAbstractCreatorFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(messages.mustBeAbstract()).inFile(componentFile);
  }

  @Test
  public void testCreatorOneConstructorWithArgsFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(messages.invalidConstructor())
        .inFile(componentFile);
  }

  @Test
  public void testCreatorMoreThanOneConstructorFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(messages.invalidConstructor())
        .inFile(componentFile);
  }

  @Test
  public void testCreatorEnumFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(messages.mustBeClassOrInterface())
        .inFile(componentFile);
  }

  @Test
  public void testCreatorFactoryMethodReturnsWrongTypeFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(messages.factoryMethodMustReturnComponentType())
        .inFile(componentFile)
        .onLineContaining(process("String build();"));
  }

  @Test
  public void testCreatorSetterForNonBindsInstancePrimitiveFails() {
    JavaFileObject component =
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
            .build();
    Compilation compilation = compile(component);
    assertThat(compilation).failed();

    assertThat(compilation)
        .hadErrorContaining(messages.nonBindsInstanceParametersMayNotBePrimitives())
        .inFile(component)
        .onLineContaining("(long l)");
  }

  @Test
  public void testInheritedBuilderBuildReturnsWrongTypeFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                messages.inheritedFactoryMethodMustReturnComponentType(), process("build")))
        .inFile(componentFile)
        .onLineContaining(process("interface Builder"));
  }

  @Test
  public void testTwoFactoryMethodsFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(String.format(messages.twoFactoryMethods(), process("build")))
        .inFile(componentFile)
        .onLineContaining("SimpleComponent newSimpleComponent();");
  }

  @Test
  public void testInheritedTwoFactoryMethodsFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                messages.inheritedTwoFactoryMethods(), process("build()"), "newSimpleComponent()"))
        .inFile(componentFile)
        .onLineContaining(process("interface Builder"));
  }

  @Test
  public void testMultipleSettersPerTypeFails() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject componentFile =
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
            .build();
    Compilation compilation = compile(moduleFile, componentFile);
    assertThat(compilation).failed();
    String elements =
        creatorKind.equals(BUILDER)
            ? "[void test.SimpleComponent.Builder.set1(test.TestModule), "
                + "void test.SimpleComponent.Builder.set2(test.TestModule)]"
            : "[test.TestModule m1, test.TestModule m2]";
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                messages.multipleSettersForModuleOrDependencyType(), "test.TestModule", elements))
        .inFile(componentFile)
        .onLineContaining(process("interface Builder"));
  }

  @Test
  public void testMultipleSettersPerTypeIncludingResolvedGenericsFails() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject componentFile =
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
            .build();
    Compilation compilation = compile(moduleFile, componentFile);
    assertThat(compilation).failed();
    String elements =
        creatorKind.equals(BUILDER)
            ? "[void test.SimpleComponent.Builder.set1(test.TestModule), "
                + "void test.SimpleComponent.Builder.set2(test.TestModule)]"
            : "[test.TestModule m1, test.TestModule t]";
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                messages.multipleSettersForModuleOrDependencyType(), "test.TestModule", elements))
        .inFile(componentFile)
        .onLineContaining(process("interface Builder"));
  }

  @Test
  public void testExtraSettersFails() {
    JavaFileObject componentFile =
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
            .build();
    JavaFileObject abstractModule =
        JavaFileObjects.forSourceLines(
            "test.AbstractModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class AbstractModule {}");
    Compilation compilation = compile(componentFile, abstractModule);
    assertThat(compilation).failed();
    String elements =
        creatorKind.equals(BUILDER)
            ? "[void test.SimpleComponent.Builder.abstractModule(test.AbstractModule), "
                + "void test.SimpleComponent.Builder.other(String)]"
            : "[test.AbstractModule abstractModule, String s]";
    assertThat(compilation)
        .hadErrorContaining(String.format(messages.extraSetters(), elements))
        .inFile(componentFile)
        .onLineContaining(process("interface Builder"));
  }

  @Test
  public void testMissingSettersFail() {
    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject module2File =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject module3File =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject componentFile =
        preprocessedJavaFile(
            "test.TestComponent",
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
    JavaFileObject otherComponent =
        JavaFileObjects.forSourceLines(
            "test.OtherComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface OtherComponent {}");
    Compilation compilation =
        daggerCompiler()
            .compile(moduleFile, module2File, module3File, componentFile, otherComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            // Ignores Test2Module because we can construct it ourselves.
            // TODO(sameb): Ignore Test3Module because it's not used within transitive dependencies.
            String.format(
                messages.missingSetters(),
                "[test.TestModule, test.Test3Module, test.OtherComponent]"))
        .inFile(componentFile)
        .onLineContaining(process("interface Builder"));
  }

  @Test
  public void covariantFactoryMethodReturnType() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  Foo foo();",
            "}");

    JavaFileObject component =
        preprocessedJavaFile(
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

    Compilation compilation = compile(foo, supertype, component);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void covariantFactoryMethodReturnType_hasNewMethod() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  Foo foo();",
            "}");

    JavaFileObject component =
        preprocessedJavaFile(
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

    Compilation compilation = compile(foo, bar, supertype, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            process(
                "test.HasSupertype.Builder.build() returns test.Supertype, but test.HasSupertype "
                    + "declares additional component method(s): bar(). In order to provide "
                    + "type-safe access to these methods, override build() to return "
                    + "test.HasSupertype"))
        .inFile(component)
        .onLine(11);
  }

  @Test
  public void covariantFactoryMethodReturnType_hasNewMethod_factoryMethodInherited() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  Foo foo();",
            "}");

    JavaFileObject creatorSupertype =
        preprocessedJavaFile(
            "test.CreatorSupertype",
            "package test;",
            "",
            "interface CreatorSupertype {",
            "  Supertype build();",
            "}");

    JavaFileObject component =
        preprocessedJavaFile(
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

    Compilation compilation = compile(foo, bar, supertype, creatorSupertype, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            process(
                "test.HasSupertype.Builder.build() returns test.Supertype, but test.HasSupertype "
                    + "declares additional component method(s): bar(). In order to provide "
                    + "type-safe access to these methods, override build() to return "
                    + "test.HasSupertype"));
  }

  @Test
  public void testGenericsOnFactoryMethodFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(messages.methodsMayNotHaveTypeParameters())
        .inFile(componentFile)
        .onLineContaining(process("<T> SimpleComponent build();"));
  }

  @Test
  public void testGenericsOnInheritedFactoryMethodFails() {
    JavaFileObject componentFile =
        preprocessedJavaFile(
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
    Compilation compilation = compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                messages.inheritedMethodsMayNotHaveTypeParameters(), process("<T>build()")))
        .inFile(componentFile)
        .onLineContaining(process("interface Builder"));
  }
}
