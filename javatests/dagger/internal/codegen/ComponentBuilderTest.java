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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {@link dagger.Component.Builder} */
@RunWith(Parameterized.class)
public class ComponentBuilderTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ComponentBuilderTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final ErrorMessages.ComponentCreatorMessages MSGS =
      ErrorMessages.ComponentCreatorMessages.INSTANCE;

  @Test
  public void testEmptyBuilder() {
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
        JavaFileObjects.forSourceLines(
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
        JavaFileObjects.forSourceLines(
            "test.DaggerSimpleComponent",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerSimpleComponent implements SimpleComponent {",
            "  private static final class Builder implements SimpleComponent.Builder {",
            "    @Override",
            "    public SimpleComponent build() {",
            "      return new DaggerSimpleComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void testUsesBuildAndSetterNames() {
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
            "  @Provides String string() { return null; }",
            "}");

    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
            "    Builder setTestModule(TestModule testModule);",
            "    TestComponent create();",
            "  }",
            "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(moduleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void testIgnoresModulesNotInApi() {
    JavaFileObject module1 =
        JavaFileObjects.forSourceLines(
            "test.TestModule1",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule1 {",
            "  @Provides String string() { return null; }",
            "}");
    JavaFileObject module2 =
        JavaFileObjects.forSourceLines(
            "test.TestModule2",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule2 {",
            "  @Provides Integer integer() { return null; }",
            "}");

    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
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
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private TestModule1 testModule1;",
            "  private TestModule2 testModule2;",
            "",
            "  private DaggerTestComponent(Builder builder) {",
            "    this.testModule1 = builder.testModule1;",
            "    this.testModule2 = builder.testModule2;",
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
            "    return TestModule1_StringFactory.proxyString(testModule1);",
            "  }",
            "",
            "  @Override",
            "  public Integer integer() {",
            "    return TestModule2_IntegerFactory.proxyInteger(testModule2);",
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
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(module1, module2, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void testMoreThanOneBuilderFails() {
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
            "  @Component.Builder",
            "  interface Builder2 {",
            "     SimpleComponent build();",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.moreThanOne(),
                "[test.SimpleComponent.Builder, test.SimpleComponent.Builder2]"))
        .inFile(componentFile);
  }

  @Test
  public void testBuilderGenericsFails() {
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
            "  interface Builder<T> {",
            "     SimpleComponent build();",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(MSGS.generics()).inFile(componentFile);
  }

  @Test
  public void testBuilderNotInComponentFails() {
    JavaFileObject builder =
        JavaFileObjects.forSourceLines(
            "test.Builder",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component.Builder",
            "interface Builder {}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(builder);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(MSGS.mustBeInComponent()).inFile(builder);
  }

  @Test
  public void testBuilderMissingBuildMethodFails() {
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
            "  interface Builder {}",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(MSGS.missingBuildMethod()).inFile(componentFile);
  }

  @Test
  public void testBuilderBindsInstanceNoCreateGenerated() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  Object object();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    @BindsInstance Builder object(Object object);",
            "    SimpleComponent build();",
            "  }",
            "}");

    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerSimpleComponent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerSimpleComponent implements SimpleComponent {",
            "  private Object object;",
            "",
            "  private DaggerSimpleComponent(Builder builder) {",
            "    this.object = builder.object;",
            "  }",
            "",
            "  public static SimpleComponent.Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  @Override",
            "  public Object object() {",
            "    return object;",
            "  }",
            "",
            "  private static final class Builder implements SimpleComponent.Builder {",
            "    private Object object;",
            "",
            "    @Override",
            "    public SimpleComponent build() {",
            "      Preconditions.checkBuilderRequirement(object, Object.class);",
            "      return new DaggerSimpleComponent(this);",
            "    }",
            "",
            "    @Override",
            "    public Builder object(Object object) {",
            "      this.object = Preconditions.checkNotNull(object);",
            "      return this;",
            "    }",
            "  }",
            "}");

    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void testPrivateBuilderFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(MSGS.isPrivate()).inFile(componentFile);
  }

  @Test
  public void testNonStaticBuilderFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(MSGS.mustBeStatic()).inFile(componentFile);
  }

  @Test
  public void testNonAbstractBuilderFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(MSGS.mustBeAbstract()).inFile(componentFile);
  }

  @Test
  public void testBuilderOneCxtorWithArgsFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(MSGS.cxtorOnlyOneAndNoArgs()).inFile(componentFile);
  }

  @Test
  public void testBuilderMoreThanOneCxtorFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(MSGS.cxtorOnlyOneAndNoArgs()).inFile(componentFile);
  }

  @Test
  public void testBuilderEnumFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining(MSGS.mustBeClassOrInterface()).inFile(componentFile);
  }

  @Test
  public void testBuilderBuildReturnsWrongTypeFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.buildMustReturnComponentType())
        .inFile(componentFile)
        .onLineContaining("String build();");
  }

  @Test
  public void builderMethodTakesPrimitive() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Object object();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    Builder primitive(long l);",
            "    TestComponent build();",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@Component.Builder methods that are not annotated with @BindsInstance must take "
                + "either a module or a component dependency, not a primitive")
        .inFile(component)
        .onLineContaining("primitive(long l);");
  }

  @Test
  public void testInheritedBuilderBuildReturnsWrongTypeFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(String.format(MSGS.inheritedBuildMustReturnComponentType(), "build"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @Test
  public void testTwoBuildMethodsFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
            "    SimpleComponent create();",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(String.format(MSGS.twoBuildMethods(), "build"))
        .inFile(componentFile)
        .onLineContaining("SimpleComponent create();");
  }

  @Test
  public void testInheritedTwoBuildMethodsFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
            "    SimpleComponent create();",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(String.format(MSGS.inheritedTwoBuildMethods(), "build()", "create()"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @Test
  public void testMoreThanOneArgFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
            "    Builder set(String s, Integer i);",
            "    Builder set(Number n, Double d);",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.methodsMustTakeOneArg())
        .inFile(componentFile)
        .onLineContaining("Builder set(String s, Integer i);");
    assertThat(compilation)
        .hadErrorContaining(MSGS.methodsMustTakeOneArg())
        .inFile(componentFile)
        .onLineContaining("Builder set(Number n, Double d);");
  }

  @Test
  public void testInheritedMoreThanOneArgFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
            "    Builder set1(String s, Integer i);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.inheritedMethodsMustTakeOneArg(), "set1(java.lang.String,java.lang.Integer)"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @Test
  public void testSetterReturningNonVoidOrBuilderFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
            "    String set(Integer i);",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.methodsMustReturnVoidOrBuilder())
        .inFile(componentFile)
        .onLineContaining("String set(Integer i);");
  }

  @Test
  public void testInheritedSetterReturningNonVoidOrBuilderFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
            "    String set(Integer i);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(MSGS.inheritedMethodsMustReturnVoidOrBuilder(), "set(java.lang.Integer)"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @Test
  public void testGenericsOnSetterMethodFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
            "    <T> Builder set(T t);",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(MSGS.methodsMayNotHaveTypeParameters())
        .inFile(componentFile)
        .onLineContaining("<T> Builder set(T t);");
  }

  @Test
  public void testGenericsOnInheritedSetterMethodFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
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
            "    <T> Builder set(T t);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent {}",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(MSGS.inheritedMethodsMayNotHaveTypeParameters(), "<T>set(T)"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
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
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "abstract class SimpleComponent {",
            "  abstract String s();",
            "",
            "  @Component.Builder",
            "  interface Builder {",
            "    SimpleComponent build();",
            "    void set1(TestModule s);",
            "    void set2(TestModule s);",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(moduleFile, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.manyMethodsForType(),
                "test.TestModule",
                "[set1(test.TestModule), set2(test.TestModule)]"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
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
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "abstract class SimpleComponent {",
            "  abstract String s();",
            "",
            "  interface Parent<T> {",
            "    void set1(T t);",
            "  }",
            "",
            "  @Component.Builder",
            "  interface Builder extends Parent<TestModule> {",
            "    SimpleComponent build();",
            "    void set2(TestModule s);",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(moduleFile, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.manyMethodsForType(), "test.TestModule", "[set1(T), set2(test.TestModule)]"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @Test
  public void testExtraSettersFails() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
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
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(componentFile, abstractModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                MSGS.extraSetters(),
                "[void test.SimpleComponent.Builder.abstractModule(test.AbstractModule), "
                    + "void test.SimpleComponent.Builder.other(String)]"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
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
        JavaFileObjects.forSourceLines(
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
                MSGS.missingSetters(), "[test.TestModule, test.Test3Module, test.OtherComponent]"))
        .inFile(componentFile)
        .onLineContaining("interface Builder");
  }

  @Test
  public void covariantBuildMethodReturnType() {
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
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(foo, supertype, component);
    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void covariantBuildMethodReturnType_hasNewMethod() {
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
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(foo, bar, supertype, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            "test.HasSupertype.Builder.build() returns test.Supertype, but test.HasSupertype "
                + "declares additional component method(s): bar(). In order to provide type-safe "
                + "access to these methods, override build() to return test.HasSupertype")
        .inFile(component)
        .onLine(11);
  }

  @Test
  public void covariantBuildMethodReturnType_hasNewMethod_buildMethodInherited() {
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

    JavaFileObject builderSupertype =
        JavaFileObjects.forSourceLines(
            "test.BuilderSupertype",
            "package test;",
            "",
            "interface BuilderSupertype {",
            "  Supertype build();",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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
            "  interface Builder extends BuilderSupertype {}",
            "}");

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(foo, bar, supertype, builderSupertype, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .hadWarningContaining(
            "[test.BuilderSupertype.build()] test.HasSupertype.Builder.build() returns "
                + "test.Supertype, but test.HasSupertype declares additional component method(s): "
                + "bar(). In order to provide type-safe access to these methods, override build() "
                + "to return test.HasSupertype");
  }
}
