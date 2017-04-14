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
import static com.google.testing.compile.Compiler.javac;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

import com.google.auto.common.MoreElements;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import com.squareup.javapoet.CodeBlock;
import dagger.MembersInjector;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ComponentProcessorTest {
  private static final CodeBlock NPE_LITERAL =
      CodeBlocks.stringLiteral(ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD);

  @Test public void componentOnConcreteClass() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "final class NotAComponent {}");
    Compilation compilation = daggerCompiler().compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test public void componentOnEnum() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "enum NotAComponent {",
        "  INSTANCE",
        "}");
    Compilation compilation = daggerCompiler().compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test public void componentOnAnnotation() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "@interface NotAComponent {}");
    Compilation compilation = daggerCompiler().compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test public void nonModuleModule() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = Object.class)",
        "interface NotAComponent {}");
    Compilation compilation = daggerCompiler().compile(componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("is not annotated with @Module");
  }

  @Test
  public void componentWithInvalidModule() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.BadModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class BadModule {",
            "  @Binds abstract Object noParameters();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.BadComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = BadModule.class)",
            "interface BadComponent {",
            "  Object object();",
            "}");
    Compilation compilation = daggerCompiler().compile(module, component);
    assertThat(compilation)
        .hadErrorContaining("test.BadModule has errors")
        .inFile(component)
        .onLine(5);
  }

  @Test public void doubleBindingFromResolvedModules() {
    JavaFileObject parent = JavaFileObjects.forSourceLines("test.ParentModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.util.List;",
        "",
        "@Module",
        "abstract class ParentModule<A> {",
        "  @Provides List<A> provideListB(A a) { return null; }",
        "}");
    JavaFileObject child = JavaFileObjects.forSourceLines("test.ChildModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "class ChildNumberModule extends ParentModule<Integer> {",
        "  @Provides Integer provideInteger() { return null; }",
        "}");
    JavaFileObject another = JavaFileObjects.forSourceLines("test.AnotherModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.util.List;",
        "",
        "@Module",
        "class AnotherModule {",
        "  @Provides List<Integer> provideListOfInteger() { return null; }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.BadComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.List;",
        "",
        "@Component(modules = {ChildNumberModule.class, AnotherModule.class})",
        "interface BadComponent {",
        "  List<Integer> listOfInteger();",
        "}");

    Compilation compilation = daggerCompiler().compile(parent, child, another, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("java.util.List<java.lang.Integer> is bound multiple times");
    assertThat(compilation)
        .hadErrorContaining("@Provides List<Integer> test.ChildNumberModule.provideListB(Integer)");
    assertThat(compilation)
        .hadErrorContaining("@Provides List<Integer> test.AnotherModule.provideListOfInteger()");
  }

  @Test public void privateNestedClassWithWarningThatIsAnErrorInComponent() {
    JavaFileObject outerClass = JavaFileObjects.forSourceLines("test.OuterClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class OuterClass {",
        "  @Inject OuterClass(InnerClass innerClass) {}",
        "",
        "  private static final class InnerClass {",
        "    @Inject InnerClass() {}",
        "  }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.BadComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface BadComponent {",
        "  OuterClass outerClass();",
        "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions("-Adagger.privateMemberValidation=WARNING")
            .compile(outerClass, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private classes");
  }

  @Test public void simpleComponent() {
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
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "  Lazy<SomeInjectableType> lazySomeInjectableType();",
        "  Provider<SomeInjectableType> someInjectableTypeProvider();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerSimpleComponent",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import dagger.internal.DoubleCheck;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerSimpleComponent implements SimpleComponent {",
            "  private DaggerSimpleComponent(Builder builder) {",
            "    assert builder != null;",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static SimpleComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public SomeInjectableType someInjectableType() {",
            "    return new SomeInjectableType();",
            "  }",
            "",
            "  @Override",
            "  public Lazy<SomeInjectableType> lazySomeInjectableType() {",
            "    return DoubleCheck.lazy(SomeInjectableType_Factory.create());",
            "  }",
            "",
            "  @Override",
            "  public Provider<SomeInjectableType> someInjectableTypeProvider() {",
            "    return SomeInjectableType_Factory.create();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public SimpleComponent build() {",
            "      return new DaggerSimpleComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void componentWithScope() {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Singleton;",
        "",
        "@Singleton",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "import javax.inject.Singleton;",
        "",
        "@Singleton",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "  Lazy<SomeInjectableType> lazySomeInjectableType();",
        "  Provider<SomeInjectableType> someInjectableTypeProvider();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerSimpleComponent",
            "package test;",
            "",
            "import dagger.Lazy;",
            "import dagger.internal.DoubleCheck;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerSimpleComponent implements SimpleComponent {",
            "  private Provider<SomeInjectableType> someInjectableTypeProvider;",
            "",
            "  private DaggerSimpleComponent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static SimpleComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.someInjectableTypeProvider =",
            "        DoubleCheck.provider(SomeInjectableType_Factory.create());",
            "  }",
            "",
            "  @Override",
            "  public SomeInjectableType someInjectableType() {",
            "    return someInjectableTypeProvider.get();",
            "  }",
            "",
            "  @Override",
            "  public Lazy<SomeInjectableType> lazySomeInjectableType() {",
            "    return DoubleCheck.lazy(someInjectableTypeProvider);",
            "  }",
            "",
            "  @Override",
            "  public Provider<SomeInjectableType> someInjectableTypeProvider() {",
            "    return someInjectableTypeProvider;",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public SimpleComponent build() {",
            "      return new DaggerSimpleComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void simpleComponentWithNesting() {
    JavaFileObject nestedTypesFile = JavaFileObjects.forSourceLines("test.OuterType",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Inject;",
        "",
        "final class OuterType {",
        "  static class A {",
        "    @Inject A() {}",
        "  }",
        "  static class B {",
        "    @Inject A a;",
        "  }",
        "  @Component interface SimpleComponent {",
        "    A a();",
        "    void inject(B b);",
        "  }",
        "}");

    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerOuterType_SimpleComponent",
            "package test;",
            "",
            "import dagger.MembersInjector;",
            "import javax.annotation.Generated;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerOuterType_SimpleComponent",
            "    implements OuterType.SimpleComponent {",
            "  private MembersInjector<OuterType.B> bMembersInjector;",
            "",
            "  private DaggerOuterType_SimpleComponent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static OuterType.SimpleComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.bMembersInjector =",
            "        OuterType_B_MembersInjector.create(OuterType_A_Factory.create());",
            "  }",
            "",
            "  @Override",
            "  public OuterType.A a() {",
            "    return new OuterType.A();",
            "  }",
            "",
            "  @Override",
            "  public void inject(OuterType.B b) {",
            "    bMembersInjector.injectMembers(b);",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public OuterType.SimpleComponent build() {",
            "      return new DaggerOuterType_SimpleComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(nestedTypesFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerOuterType_SimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void componentWithModule() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A(B b) {}",
        "}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "interface B {}");
    JavaFileObject cFile = JavaFileObjects.forSourceLines("test.C",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class C {",
        "  @Inject C() {}",
        "}");

    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides B b(C c) { return null; }",
        "}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  A a();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private Provider<B> bProvider;",
            "  private Provider<A> aProvider;",
            "",
            "  private DaggerTestComponent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.bProvider = TestModule_BFactory.create(builder.testModule,",
            "        C_Factory.create());",
            "    this.aProvider = A_Factory.create(bProvider);",
            "  }",
            "",
            "  @Override",
            "  public A a() {",
            "    return new A(bProvider.get());",
            "  }",
            "",
            "  public static final class Builder {",
            "    private TestModule testModule;",
            "",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      if (testModule == null) {",
            "        this.testModule = new TestModule();",
            "      }",
            "      return new DaggerTestComponent(this);",
            "    }",
            "",
            "    public Builder testModule(TestModule testModule) {",
            "      this.testModule = Preconditions.checkNotNull(testModule);",
            "      return this;",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().compile(aFile, bFile, cFile, moduleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void componentWithAbstractModule() {
    JavaFileObject aFile =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(B b) {}",
            "}");
    JavaFileObject bFile =
        JavaFileObjects.forSourceLines("test.B",
            "package test;",
            "",
            "interface B {}");
    JavaFileObject cFile =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class C {",
            "  @Inject C() {}",
            "}");

    JavaFileObject moduleFile =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides static B b(C c) { return null; }",
            "}");

    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private Provider<B> bProvider;",
            "  private Provider<A> aProvider;",
            "",
            "  private DaggerTestComponent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.bProvider = TestModule_BFactory.create(C_Factory.create());",
            "    this.aProvider = A_Factory.create(bProvider);",
            "  }",
            "",
            "  @Override",
            "  public A a() {",
            "    return new A(TestModule.b(new C()));",
            "  }",
            "",
            "  public static final class Builder {",
            "",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().compile(aFile, bFile, cFile, moduleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void transitiveModuleDeps() {
    JavaFileObject always = JavaFileObjects.forSourceLines("test.AlwaysIncluded",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class AlwaysIncluded {}");
    JavaFileObject testModule = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {DepModule.class, AlwaysIncluded.class})",
        "final class TestModule extends ParentTestModule {}");
    JavaFileObject parentTest = JavaFileObjects.forSourceLines("test.ParentTestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {ParentTestIncluded.class, AlwaysIncluded.class})",
        "class ParentTestModule {}");
    JavaFileObject parentTestIncluded = JavaFileObjects.forSourceLines("test.ParentTestIncluded",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = AlwaysIncluded.class)",
        "final class ParentTestIncluded {}");
    JavaFileObject depModule = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {RefByDep.class, AlwaysIncluded.class})",
        "final class DepModule extends ParentDepModule {}");
    JavaFileObject refByDep = JavaFileObjects.forSourceLines("test.RefByDep",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = AlwaysIncluded.class)",
        "final class RefByDep extends ParentDepModule {}");
    JavaFileObject parentDep = JavaFileObjects.forSourceLines("test.ParentDepModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {ParentDepIncluded.class, AlwaysIncluded.class})",
        "class ParentDepModule {}");
    JavaFileObject parentDepIncluded = JavaFileObjects.forSourceLines("test.ParentDepIncluded",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = AlwaysIncluded.class)",
        "final class ParentDepIncluded {}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "}");
    // Generated code includes all includes, but excludes the parent modules.
    // The "always" module should only be listed once.
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerTestComponent",
        "package test;",
        "",
        "import dagger.internal.Preconditions;",
        "import javax.annotation.Generated;",
        "",
        GENERATED_ANNOTATION,
        "public final class DaggerTestComponent implements TestComponent {",
        "",
        "  private DaggerTestComponent(Builder builder) {",
        "    assert builder != null;",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static TestComponent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      return new DaggerTestComponent(this);",
        "    }",
        "",
        "    @Deprecated",
        "    public Builder testModule(TestModule testModule) {",
        "      Preconditions.checkNotNull(testModule)",
        "      return this;",
        "    }",
        "",
        "    @Deprecated",
        "    public Builder parentTestIncluded(ParentTestIncluded parentTestIncluded) {",
        "      Preconditions.checkNotNull(parentTestIncluded)",
        "      return this;",
        "    }",
        "",
        "    @Deprecated",
        "    public Builder alwaysIncluded(AlwaysIncluded alwaysIncluded) {",
        "      Preconditions.checkNotNull(alwaysIncluded)",
        "      return this;",
        "    }",
        "",
        "    @Deprecated",
        "    public Builder depModule(DepModule depModule) {",
        "      Preconditions.checkNotNull(depModule)",
        "      return this;",
        "    }",
        "",
        "    @Deprecated",
        "    public Builder parentDepIncluded(ParentDepIncluded parentDepIncluded) {",
        "      Preconditions.checkNotNull(parentDepIncluded)",
        "      return this;",
        "    }",
        "",
        "    @Deprecated",
        "    public Builder refByDep(RefByDep refByDep) {",
        "      Preconditions.checkNotNull(refByDep)",
        "      return this;",
        "    }",
        "  }",
        "}");
    Compilation compilation =
        daggerCompiler()
            .compile(
                always,
                testModule,
                parentTest,
                parentTestIncluded,
                depModule,
                refByDep,
                parentDep,
                parentDepIncluded,
                componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test
  public void generatedTransitiveModule() {
    JavaFileObject rootModule = JavaFileObjects.forSourceLines("test.RootModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = GeneratedModule.class)",
        "final class RootModule {}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = RootModule.class)",
        "interface TestComponent {}");
    assertThat(daggerCompiler().compile(rootModule, component)).failed();
    assertThat(
            daggerCompiler(
                    new GeneratingProcessor(
                        "test.GeneratedModule",
                        "package test;",
                        "",
                        "import dagger.Module;",
                        "",
                        "@Module",
                        "final class GeneratedModule {}"))
                .compile(rootModule, component))
        .succeeded();
  }

  @Test
  public void generatedModuleInSubcomponent() {
    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GeneratedModule.class)",
            "interface ChildComponent {}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  ChildComponent childComponent();",
            "}");
    assertThat(daggerCompiler().compile(subcomponent, component)).failed();
    assertThat(
            daggerCompiler(
                    new GeneratingProcessor(
                        "test.GeneratedModule",
                        "package test;",
                        "",
                        "import dagger.Module;",
                        "",
                        "@Module",
                        "final class GeneratedModule {}"))
                .compile(subcomponent, component))
        .succeeded();
  }

  @Test
  public void subcomponentOmitsInheritedBindings() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides @IntoSet static Object parentObject() {",
            "    return \"parent object\";",
            "  }",
            "",
            "  @Provides @IntoMap @StringKey(\"parent key\") Object parentKeyObject() {",
            "    return \"parent value\";",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  Set<Object> objectSet();",
            "  Map<String, Object> objectMap();",
            "}");
    JavaFileObject expected =
        JavaFileObjects.forSourceLines(
            "test.DaggerParent",
            "package test;",
            "",
            "import dagger.internal.MapFactory;",
            "import dagger.internal.MapProviderFactory;",
            "import dagger.internal.Preconditions;",
            "import dagger.internal.SetFactory;",
            "import java.util.Map;",
            "import java.util.Set;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerParent implements Parent {",
            "  private Provider<Object> parentKeyObjectProvider;",
            "",
            "  private DaggerParent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Parent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.parentKeyObjectProvider =",
            "        ParentModule_ParentKeyObjectFactory.create(builder.parentModule);",
            "  }",
            "",
            "  @Override",
            "  public Child child() {",
            "    return new ChildImpl();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private ParentModule parentModule;",
            "",
            "    private Builder() {}",
            "",
            "    public Parent build() {",
            "      if (parentModule == null) {",
            "        this.parentModule = new ParentModule();",
            "      }",
            "      return new DaggerParent(this);",
            "    }",
            "",
            "    public Builder parentModule(ParentModule parentModule) {",
            "      this.parentModule = Preconditions.checkNotNull(parentModule);",
            "      return this;",
            "    }",
            "  }",
            "",
            "  private final class ChildImpl implements Child {",
            "    private Provider<Set<Object>> setOfObjectProvider;",
            "    private Provider<Map<String, Provider<Object>>>",
            "        mapOfStringAndProviderOfObjectProvider;",
            "    private Provider<Map<String, Object>> mapOfStringAndObjectProvider;",
            "",
            "    private ChildImpl() {",
            "      initialize();",
            "    }",
            "",
            "    @SuppressWarnings(\"unchecked\")",
            "    private void initialize() {",
            "      this.setOfObjectProvider = SetFactory.<Object>builder(1, 0)",
            "          .addProvider(ParentModule_ParentObjectFactory.create()).build();",
            "      this.mapOfStringAndProviderOfObjectProvider =",
            "          MapProviderFactory.<String, Object>builder(1)",
            "              .put(\"parent key\", DaggerParent.this.parentKeyObjectProvider)",
            "              .build();",
            "      this.mapOfStringAndObjectProvider = MapFactory.create(",
            "          mapOfStringAndProviderOfObjectProvider);",
            "    }",
            "",
            "    @Override",
            "    public Set<Object> objectSet() {",
            "      return setOfObjectProvider.get();",
            "    }",
            "",
            "    @Override",
            "    public Map<String, Object> objectMap() {",
            "      return mapOfStringAndObjectProvider.get();",
            "    }",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(parent, parentModule, child);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .hasSourceEquivalentTo(expected);
  }

  @Test
  public void subcomponentNotGeneratedIfNotUsedInGraph() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  String notSubcomponent();",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module(subcomponents = Child.class)",
            "class ParentModule {",
            "  @Provides static String notSubcomponent() { return new String(); }",
            "}");

    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
            "}");

    JavaFileObject generatedComponentWithoutSubcomponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerParent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            "import javax.annotation.Generated;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerParent implements Parent {",
            "",
            "  private DaggerParent(Builder builder) {",
            "    assert builder != null;",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Parent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public String notSubcomponent() {",
            "    return ParentModule.notSubcomponent();",
            "  }",
            "",
            "  public static final class Builder {",
            "",
            "    private Builder() {",
            "    }",
            "",
            "    public Parent build() {",
            "      return new DaggerParent(this);",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder parentModule(ParentModule parentModule) {",
            "      Preconditions.checkNotNull(parentModule);",
            "      return this;",
            "    }",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component, module, subcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .hasSourceEquivalentTo(generatedComponentWithoutSubcomponent);
  }

  @Test
  public void testDefaultPackage() {
    JavaFileObject aClass = JavaFileObjects.forSourceLines("AClass", "class AClass {}");
    JavaFileObject bClass = JavaFileObjects.forSourceLines("BClass",
        "import javax.inject.Inject;",
        "",
        "class BClass {",
        "  @Inject BClass(AClass a) {}",
        "}");
    JavaFileObject aModule = JavaFileObjects.forSourceLines("AModule",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module class AModule {",
        "  @Provides AClass aClass() {",
        "    return new AClass();",
        "  }",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("SomeComponent",
        "import dagger.Component;",
        "",
        "@Component(modules = AModule.class)",
        "interface SomeComponent {",
        "  BClass bClass();",
        "}");
    assertThat(daggerCompiler().compile(aModule, aClass, bClass, component)).succeeded();
  }

  @Test public void setBindings() {
    JavaFileObject emptySetModuleFile = JavaFileObjects.forSourceLines("test.EmptySetModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.ElementsIntoSet;",
        "import java.util.Collections;",
        "import java.util.Set;",
        "",
        "@Module",
        "final class EmptySetModule {",
        "  @Provides @ElementsIntoSet Set<String> emptySet() { return Collections.emptySet(); }",
        "}");
    JavaFileObject setModuleFile = JavaFileObjects.forSourceLines("test.SetModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoSet;",
        "",
        "@Module",
        "final class SetModule {",
        "  @Provides @IntoSet String string() { return \"\"; }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Set;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = {EmptySetModule.class, SetModule.class})",
        "interface TestComponent {",
        "  Set<String> strings();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            "import dagger.internal.SetFactory;",
            "import java.util.Set;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private Provider<Set<String>> emptySetProvider;",
            "  private Provider<String> stringProvider;",
            "  private Provider<Set<String>> setOfStringProvider;",
            "",
            "  private DaggerTestComponent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.emptySetProvider =",
            "        EmptySetModule_EmptySetFactory.create(builder.emptySetModule);",
            "    this.stringProvider =",
            "        SetModule_StringFactory.create(builder.setModule);",
            "    this.setOfStringProvider = ",
            "        SetFactory.<String>builder(1, 1)",
            "            .addCollectionProvider(emptySetProvider)",
            "            .addProvider(stringProvider)",
            "            .build();",
            "  }",
            "",
            "  @Override",
            "  public Set<String> strings() {",
            "    return setOfStringProvider.get();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private EmptySetModule emptySetModule;",
            "    private SetModule setModule;",
            "",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      if (emptySetModule == null) {",
            "        this.emptySetModule = new EmptySetModule();",
            "      }",
            "      if (setModule == null) {",
            "        this.setModule = new SetModule();",
            "      }",
            "      return new DaggerTestComponent(this);",
            "    }",
            "",
            "    public Builder emptySetModule(EmptySetModule emptySetModule) {",
            "      this.emptySetModule = Preconditions.checkNotNull(emptySetModule);",
            "      return this;",
            "    }",
            "",
            "    public Builder setModule(SetModule setModule) {",
            "      this.setModule = Preconditions.checkNotNull(setModule);",
            "      return this;",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().compile(emptySetModuleFile, setModuleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void membersInjection() {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject injectedTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectedType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectedType {",
        "  @Inject SomeInjectableType injectedField;",
        "  SomeInjectedType() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  void inject(SomeInjectedType instance);",
        "  SomeInjectedType injectAndReturn(SomeInjectedType instance);",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerSimpleComponent",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import javax.annotation.Generated;",
        "",
        GENERATED_ANNOTATION,
        "public final class DaggerSimpleComponent implements SimpleComponent {",
        "  private MembersInjector<SomeInjectedType> someInjectedTypeMembersInjector;",
        "",
        "  private DaggerSimpleComponent(Builder builder) {",
        "    assert builder != null;",
        "    initialize(builder);",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static SimpleComponent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  @SuppressWarnings(\"unchecked\")",
        "  private void initialize(final Builder builder) {",
        "    this.someInjectedTypeMembersInjector =",
        "        SomeInjectedType_MembersInjector.create(SomeInjectableType_Factory.create());",
        "  }",
        "",
        "  @Override",
        "  public void inject(SomeInjectedType instance) {",
        "    someInjectedTypeMembersInjector.injectMembers(instance);",
        "  }",
        "",
        "  @Override",
        "  public SomeInjectedType injectAndReturn(SomeInjectedType instance) {",
        "    someInjectedTypeMembersInjector.injectMembers(instance);",
        "    return instance;",
        "  }",
        "",
        "  public static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public SimpleComponent build() {",
        "      return new DaggerSimpleComponent(this);",
        "    }",
        "  }",
        "}");
    Compilation compilation =
        daggerCompiler().compile(injectableTypeFile, injectedTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void componentInjection() {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType(SimpleComponent component) {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerSimpleComponent",
            "package test;",
            "",
            "import dagger.internal.InstanceFactory;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerSimpleComponent implements SimpleComponent {",
            "  private Provider<SimpleComponent> simpleComponentProvider;",
            "  private Provider<SomeInjectableType> someInjectableTypeProvider;",
            "",
            "  private DaggerSimpleComponent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static SimpleComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.simpleComponentProvider = InstanceFactory.<SimpleComponent>create(this);",
            "    this.someInjectableTypeProvider =",
            "        SomeInjectableType_Factory.create(simpleComponentProvider);",
            "  }",
            "",
            "  @Override",
            "  public SomeInjectableType someInjectableType() {",
            "    return new SomeInjectableType(simpleComponentProvider.get())",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public SimpleComponent build() {",
            "      return new DaggerSimpleComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void membersInjectionInsideProvision() {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject injectedTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectedType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectedType {",
        "  @Inject SomeInjectableType injectedField;",
        "  @Inject SomeInjectedType() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectedType createAndInject();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerSimpleComponent",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class DaggerSimpleComponent implements SimpleComponent {",
        "  private MembersInjector<SomeInjectedType> someInjectedTypeMembersInjector;",
        "  private Provider<SomeInjectedType> someInjectedTypeProvider;",
        "",
        "  private DaggerSimpleComponent(Builder builder) {",
        "    assert builder != null;",
        "    initialize(builder);",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static SimpleComponent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  @SuppressWarnings(\"unchecked\")",
        "  private void initialize(final Builder builder) {",
        "    this.someInjectedTypeMembersInjector =",
        "        SomeInjectedType_MembersInjector.create(SomeInjectableType_Factory.create());",
        "    this.someInjectedTypeProvider =",
        "        SomeInjectedType_Factory.create(someInjectedTypeMembersInjector);",
        "  }",
        "",
        "  @Override",
        "  public SomeInjectedType createAndInject() {",
        "    return someInjectedTypeProvider.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public SimpleComponent build() {",
        "      return new DaggerSimpleComponent(this);",
        "    }",
        "  }",
        "}");
    Compilation compilation =
        daggerCompiler().compile(injectableTypeFile, injectedTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void injectionWithGenericBaseClass() {
    JavaFileObject genericType = JavaFileObjects.forSourceLines("test.AbstractGenericType",
        "package test;",
        "",
        "abstract class AbstractGenericType<T> {",
        "}");
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType extends AbstractGenericType<String> {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerSimpleComponent",
        "package test;",
        "",
        "import dagger.internal.MembersInjectors;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        GENERATED_ANNOTATION,
        "public final class DaggerSimpleComponent implements SimpleComponent {",
        "  private Provider<SomeInjectableType> someInjectableTypeProvider;",
        "",
        "  private DaggerSimpleComponent(Builder builder) {",
        "    assert builder != null;",
        "    initialize(builder);",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static SimpleComponent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  @SuppressWarnings(\"unchecked\")",
        "  private void initialize(final Builder builder) {",
        "    this.someInjectableTypeProvider =",
        "        SomeInjectableType_Factory.create(MembersInjectors.<SomeInjectableType>noOp());",
        "  }",
        "",
        "  @Override",
        "  public SomeInjectableType someInjectableType() {",
        "    return someInjectableTypeProvider.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public SimpleComponent build() {",
        "      return new DaggerSimpleComponent(this);",
        "    }",
        "  }",
        "}");
    Compilation compilation =
        daggerCompiler().compile(genericType, injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void componentDependency() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A() {}",
        "}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class B {",
        "  @Inject B(A a) {}",
        "}");
    JavaFileObject aComponentFile = JavaFileObjects.forSourceLines("test.AComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface AComponent {",
        "  A a();",
        "}");
    JavaFileObject bComponentFile = JavaFileObjects.forSourceLines("test.AComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component(dependencies = AComponent.class)",
        "interface BComponent {",
        "  B b();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerBComponent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerBComponent implements BComponent {",
            "  private Provider<A> aProvider;",
            "  private Provider<B> bProvider;",
            "",
            "  private DaggerBComponent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.aProvider = new test_AComponent_a(builder.aComponent);",
            "    this.bProvider = B_Factory.create(aProvider);",
            "  }",
            "",
            "  @Override",
            "  public B b() {",
            "    return new B(aProvider.get());",
            "  }",
            "",
            "  public static final class Builder {",
            "    private AComponent aComponent;",
            "",
            "    private Builder() {",
            "    }",
            "",
            "    public BComponent build() {",
            "      if (aComponent == null) {",
            "        throw new IllegalStateException(AComponent.class.getCanonicalName()",
            "            + \" must be set\");",
            "      }",
            "      return new DaggerBComponent(this);",
            "    }",
            "",
            "    public Builder aComponent(AComponent aComponent) {",
            "      this.aComponent = Preconditions.checkNotNull(aComponent);",
            "      return this;",
            "    }",
            "  }",
            "",
            "  private static class test_AComponent_a implements Provider<A> {",
            "    private final AComponent aComponent;",
            "    ",
            "    test_AComponent_a(AComponent aComponent) {",
            "        this.aComponent = aComponent;",
            "    }",
            "    ",
            "    @Override()",
            "    public A get() {",
            "      return Preconditions.checkNotNull(aComponent.a(), " + NPE_LITERAL + ");",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().compile(aFile, bFile, aComponentFile, bComponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerBComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void moduleNameCollision() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "public final class A {}");
    JavaFileObject otherAFile = JavaFileObjects.forSourceLines("other.test.A",
        "package other.test;",
        "",
        "public final class A {}");

    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "public final class TestModule {",
        "  @Provides A a() { return null; }",
        "}");
    JavaFileObject otherModuleFile = JavaFileObjects.forSourceLines("other.test.TestModule",
        "package other.test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "public final class TestModule {",
        "  @Provides A a() { return null; }",
        "}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = {TestModule.class, other.test.TestModule.class})",
        "interface TestComponent {",
        "  A a();",
        "  other.test.A otherA();",
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
        "  private Provider<A> aProvider;",
        "  private Provider<other.test.A> aProvider2;",
        "",
        "  private DaggerTestComponent(Builder builder) {",
        "    assert builder != null;",
        "    initialize(builder);",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static TestComponent create() {",
        "    return new Builder().build();",
        "  }",
        "",
        "  @SuppressWarnings(\"unchecked\")",
        "  private void initialize(final Builder builder) {",
        "    this.aProvider = TestModule_AFactory.create(builder.testModule);",
        "    this.aProvider2 = other.test.TestModule_AFactory.create(builder.testModule2);",
        "  }",
        "",
        "  @Override",
        "  public A a() {",
        "    return aProvider.get();",
        "  }",
        "",
        "  @Override",
        "  public other.test.A otherA() {",
        "    return aProvider2.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private TestModule testModule;",
        "    private other.test.TestModule testModule2;",
        "",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      if (testModule == null) {",
        "        this.testModule = new TestModule();",
        "      }",
        "      if (testModule2 == null) {",
        "        this.testModule2 = new other.test.TestModule();",
        "      }",
        "      return new DaggerTestComponent(this);",
        "    }",
        "",
        "    public Builder testModule(TestModule testModule) {",
        "      this.testModule = Preconditions.checkNotNull(testModule);",
        "      return this;",
        "    }",
        "",
        "    public Builder testModule(other.test.TestModule testModule) {",
        "      this.testModule2 = Preconditions.checkNotNull(testModule);",
        "      return this;",
        "    }",
        "  }",
        "}");
    Compilation compilation =
        daggerCompiler().compile(aFile, otherAFile, moduleFile, otherModuleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void resolutionOrder() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A(B b) {}",
        "}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class B {",
        "  @Inject B(C c) {}",
        "}");
    JavaFileObject cFile = JavaFileObjects.forSourceLines("test.C",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class C {",
        "  @Inject C() {}",
        "}");
    JavaFileObject xFile = JavaFileObjects.forSourceLines("test.X",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class X {",
        "  @Inject X(C c) {}",
        "}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface TestComponent {",
        "  A a();",
        "  C c();",
        "  X x();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import javax.annotation.Generated;",
            "import javax.inject.Provider;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private Provider<B> bProvider;",
            "  private Provider<A> aProvider;",
            "  private Provider<X> xProvider;",
            "",
            "  private DaggerTestComponent(Builder builder) {",
            "    assert builder != null;",
            "    initialize(builder);",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize(final Builder builder) {",
            "    this.bProvider = B_Factory.create(C_Factory.create());",
            "    this.aProvider = A_Factory.create(bProvider);",
            "    this.xProvider = X_Factory.create(C_Factory.create());",
            "  }",
            "",
            "  @Override",
            "  public A a() {",
            "    return new A(new B(new C()));",
            "  }",
            "",
            "  @Override",
            "  public C c() {",
            "    return new C();",
            "  }",
            "",
            "  @Override",
            "  public X x() {",
            "    return new X(new C());",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(aFile, bFile, cFile, xFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void simpleComponent_redundantComponentMethod() {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject componentSupertypeAFile = JavaFileObjects.forSourceLines("test.SupertypeA",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SupertypeA {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject componentSupertypeBFile = JavaFileObjects.forSourceLines("test.SupertypeB",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SupertypeB {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent extends SupertypeA, SupertypeB {",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
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
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static SimpleComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public SomeInjectableType someInjectableType() {",
            "    return new SomeInjectableType();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public SimpleComponent build() {",
            "      return new DaggerSimpleComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .compile(
                injectableTypeFile,
                componentSupertypeAFile,
                componentSupertypeBFile,
                componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void simpleComponent_inheritedComponentMethodDep() {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    JavaFileObject componentSupertype = JavaFileObjects.forSourceLines("test.Supertype",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface Supertype {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject depComponentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent extends Supertype {",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.ComponentWithDep",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component(dependencies = SimpleComponent.class)",
        "interface ComponentWithDep {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
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
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static SimpleComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public SomeInjectableType someInjectableType() {",
            "    return new SomeInjectableType();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public SimpleComponent build() {",
            "      return new DaggerSimpleComponent(this);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .compile(injectableTypeFile, componentSupertype, depComponentFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
  }

  @Test public void wildcardGenericsRequiresAtProvides() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A() {}",
        "}");
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class B<T> {",
        "  @Inject B(T t) {}",
        "}");
    JavaFileObject cFile = JavaFileObjects.forSourceLines("test.C",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class C {",
        "  @Inject C(B<? extends A> bA) {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Lazy;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  C c();",
        "}");
    Compilation compilation = daggerCompiler().compile(aFile, bFile, cFile, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.B<? extends test.A> cannot be provided without an @Provides-annotated method");
  }

  @Test
  public void componentImplicitlyDependsOnGeneratedType() {
    JavaFileObject injectableTypeFile = JavaFileObjects.forSourceLines("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType(GeneratedType generatedType) {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "}");
    Compilation compilation =
        daggerCompiler(
                new GeneratingProcessor(
                    "test.GeneratedType",
                    "package test;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "final class GeneratedType {",
                    "  @Inject GeneratedType() {}",
                    "}"))
            .compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.DaggerSimpleComponent");
  }

  @Test
  public void componentSupertypeDependsOnGeneratedType() {
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SimpleComponent extends SimpleComponentInterface {}");
    JavaFileObject interfaceFile =
        JavaFileObjects.forSourceLines(
            "test.SimpleComponentInterface",
            "package test;",
            "",
            "interface SimpleComponentInterface {",
            "  GeneratedType generatedType();",
            "}");
    Compilation compilation =
        daggerCompiler(
                new GeneratingProcessor(
                    "test.GeneratedType",
                    "package test;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "final class GeneratedType {",
                    "  @Inject GeneratedType() {}",
                    "}"))
            .compile(componentFile, interfaceFile);
    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.DaggerSimpleComponent");
  }

  @Test
  @Ignore // modify this test as necessary while debugging for your situation.
  @SuppressWarnings("unused")
  public void genericTestToLetMeDebugInEclipse() {
    JavaFileObject aFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
         "import javax.inject.Inject;",
         "",
         "public final class A {",
         "  @Inject A() {}",
         "}");
     JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
         "package test;",
         "",
         "import javax.inject.Inject;",
         "import javax.inject.Provider;",
         "",
         "public class B<T> {",
         "  @Inject B() {}",
         "}");
     JavaFileObject dFile = JavaFileObjects.forSourceLines("test.sub.D",
         "package test.sub;",
         "",
         "import javax.inject.Inject;",
         "import javax.inject.Provider;",
         "import test.B;",
         "",
         "public class D {",
         "  @Inject D(B<A.InA> ba) {}",
         "}");
     JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
         "package test;",
         "",
         "import dagger.Component;",
         "import dagger.Lazy;",
         "",
         "import javax.inject.Provider;",
         "",
         "@Component",
         "interface SimpleComponent {",
         "  B<A> d();",
         "  Provider<B<A>> d2();",
         "}");
     JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
         "test.DaggerSimpleComponent",
         "package test;",
         "",
         "import javax.annotation.Generated;",
         "import javax.inject.Provider;",
         "",
         GENERATED_ANNOTATION,
         "public final class DaggerSimpleComponent implements SimpleComponent {",
         "  private Provider<D> dProvider;",
         "",
         "  private DaggerSimpleComponent(Builder builder) {",
         "    assert builder != null;",
         "    initialize(builder);",
         "  }",
         "",
         "  public static Builder builder() {",
         "    return new Builder();",
         "  }",
         "",
         "  public static SimpleComponent create() {",
         "    return new Builder().build();",
         "  }",
         "",
         "  @SuppressWarnings(\"unchecked\")",
         "  private void initialize(final Builder builder) {",
         "    this.dProvider = new D_Factory(B_Factory.INSTANCE);",
         "  }",
         "",
         "  @Override",
         "  public D d() {",
         "    return dProvider.get();",
         "  }",
         "",
         "  public static final class Builder {",
         "    private Builder() {",
         "    }",
         "",
         "    public SimpleComponent build() {",
         "      return new DaggerSimpleComponent(this);",
         "    }",
         "  }",
         "}");
    Compilation compilation = daggerCompiler().compile(aFile, bFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(generatedComponent);
   }

  /**
   * We warn when generating a {@link MembersInjector} for a type post-hoc (i.e., if Dagger wasn't
   * invoked when compiling the type). But Dagger only generates {@link MembersInjector}s for types
   * with {@link Inject @Inject} constructors if they have any injection sites, and it only
   * generates them for types without {@link Inject @Inject} constructors if they have local
   * (non-inherited) injection sites. So make sure we warn in only those cases where running the
   * Dagger processor actually generates a {@link MembersInjector}.
   */
  @Test
  public void unprocessedMembersInjectorNotes() {
    Compilation compilation =
        javac()
            .withOptions(
                "-Xlint:-processing",
                 "-Adagger.warnIfInjectionFactoryNotGeneratedUpstream=enabled")
            .withProcessors(
                new ElementFilteringComponentProcessor(
                    Predicates.not(
                        element ->
                            MoreElements.getPackage(element)
                                .getQualifiedName()
                                .contentEquals("test.inject"))))
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.TestComponent",
                    "package test;",
                    "",
                    "import dagger.Component;",
                    "",
                    "@Component(modules = TestModule.class)",
                    "interface TestComponent {",
                    "  void inject(test.inject.NoInjectMemberNoConstructor object);",
                    "  void inject(test.inject.NoInjectMemberWithConstructor object);",
                    "  void inject(test.inject.LocalInjectMemberNoConstructor object);",
                    "  void inject(test.inject.LocalInjectMemberWithConstructor object);",
                    "  void inject(test.inject.ParentInjectMemberNoConstructor object);",
                    "  void inject(test.inject.ParentInjectMemberWithConstructor object);",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.TestModule",
                    "package test;",
                    "",
                    "import dagger.Module;",
                    "import dagger.Provides;",
                    "",
                    "@Module",
                    "class TestModule {",
                    "  @Provides static Object object() {",
                    "    return \"object\";",
                    "  }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.inject.NoInjectMemberNoConstructor",
                    "package test.inject;",
                    "",
                    "public class NoInjectMemberNoConstructor {",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.inject.NoInjectMemberWithConstructor",
                    "package test.inject;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "public class NoInjectMemberWithConstructor {",
                    "  @Inject NoInjectMemberWithConstructor() {}",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.inject.LocalInjectMemberNoConstructor",
                    "package test.inject;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "public class LocalInjectMemberNoConstructor {",
                    "  @Inject Object object;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.inject.LocalInjectMemberWithConstructor",
                    "package test.inject;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "public class LocalInjectMemberWithConstructor {",
                    "  @Inject LocalInjectMemberWithConstructor() {}",
                    "  @Inject Object object;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.inject.ParentInjectMemberNoConstructor",
                    "package test.inject;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "public class ParentInjectMemberNoConstructor",
                    "    extends LocalInjectMemberNoConstructor {}"),
                JavaFileObjects.forSourceLines(
                    "test.inject.ParentInjectMemberWithConstructor",
                    "package test.inject;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "public class ParentInjectMemberWithConstructor",
                    "    extends LocalInjectMemberNoConstructor {",
                    "  @Inject ParentInjectMemberWithConstructor() {}",
                    "}"));

    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .hadNoteContaining(
            "Generating a MembersInjector for "
                + "test.inject.LocalInjectMemberNoConstructor. "
                + "Prefer to run the dagger processor over that class instead.");
    assertThat(compilation)
        .hadNoteContaining(
            "Generating a MembersInjector for "
                + "test.inject.LocalInjectMemberWithConstructor. "
                + "Prefer to run the dagger processor over that class instead.");
    assertThat(compilation)
        .hadNoteContaining(
            "Generating a MembersInjector for "
                + "test.inject.ParentInjectMemberWithConstructor. "
                + "Prefer to run the dagger processor over that class instead.");
    assertThat(compilation).hadNoteCount(3);
  }

  @Test
  public void scopeAnnotationOnInjectConstructorNotValid() {
    JavaFileObject aScope =
        JavaFileObjects.forSourceLines(
            "test.AScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface AScope {}");
    JavaFileObject aClass =
        JavaFileObjects.forSourceLines(
            "test.AClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class AClass {",
            "  @Inject @AScope AClass() {}",
            "}");
    Compilation compilation = daggerCompiler().compile(aScope, aClass);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Scope annotations are not allowed on @Inject constructors.")
        .inFile(aClass)
        .onLine(6);
  }

  @Test
  public void attemptToInjectWildcardGenerics() {
    JavaFileObject testComponent =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Lazy;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Lazy<? extends Number> wildcardNumberLazy();",
            "  Provider<? super Number> wildcardNumberProvider();",
            "}");
    Compilation compilation = daggerCompiler().compile(testComponent);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("wildcard type").inFile(testComponent).onLine(9);
    assertThat(compilation).hadErrorContaining("wildcard type").inFile(testComponent).onLine(10);
  }

  @Test
  public void unusedSubcomponents_dontResolveExtraBindingsInParentComponents() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "class Foo {",
            "  @Inject Foo() {}",
            "}");

    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(subcomponents = Pruned.class)",
            "class TestModule {}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = TestModule.class)",
            "interface Parent {}");

    JavaFileObject prunedSubcomponent =
        JavaFileObjects.forSourceLines(
            "test.Pruned",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Pruned {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Pruned build();",
            "  }",
            "",
            "  Foo foo();",
            "}");
    JavaFileObject generated =
        JavaFileObjects.forSourceLines(
            "test.DaggerParent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            "import javax.annotation.Generated;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerParent implements Parent {",
            "  private DaggerParent(Builder builder) {",
            "    assert builder != null;",
            "  }",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static Parent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {}",
            "",
            "    public Parent build() {",
            "      return new DaggerParent(this);",
            "    }",
            "",
            "    @Deprecated",
            "    public Builder testModule(TestModule testModule) {",
            "      Preconditions.checkNotNull(testModule);",
            "      return this;",
            "    }",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(foo, module, component, prunedSubcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .hasSourceEquivalentTo(generated);
  }

  // TODO(b/34107586): Fix and enable test.
  @Test
  @Ignore
  public void invalidComponentDependencies() {
    JavaFileObject testComponent =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = int.class)",
            "interface TestComponent {}");
    Compilation compilation = daggerCompiler().compile(testComponent);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("int is not a valid component dependency type");
  }

  @Test
  public void invalidComponentModules() {
    JavaFileObject testComponent =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = int.class)",
            "interface TestComponent {}");
    Compilation compilation = daggerCompiler().compile(testComponent);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("int is not a valid module type");
  }

  @Test
  public void moduleInDependencies() {
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class TestModule {",
            "  @Provides String s() { return null; }",
            "}");
    JavaFileObject testComponent =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = TestModule.class)",
            "interface TestComponent {}");
    Compilation compilation = daggerCompiler().compile(testModule, testComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.TestModule is a module, which cannot be a component dependency");
  }

  @Test
  public void bindsInstanceInModule() {
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @BindsInstance abstract void str(String string);",
            "}");
    Compilation compilation = daggerCompiler().compile(testModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@BindsInstance methods should not be included in @Modules. Did you mean @Binds");
  }

  @Test
  public void bindsInstanceInComponent() {
    JavaFileObject testComponent =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @BindsInstance String s(String s);",
            "}");
    Compilation compilation = daggerCompiler().compile(testComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@BindsInstance methods should not be included in @Components. "
                + "Did you mean to put it in a @Component.Builder?");
  }

  @Test
  public void bindsInstanceNotAbstract() {
    JavaFileObject notAbstract =
        JavaFileObjects.forSourceLines(
            "test.BindsInstanceNotAbstract",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "class BindsInstanceNotAbstract {",
            "  @BindsInstance BindsInstanceNotAbstract bind(int unused) { return this; }",
            "}");
    Compilation compilation = daggerCompiler().compile(notAbstract);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@BindsInstance methods must be abstract")
        .inFile(notAbstract)
        .onLine(7);
  }

  @Test
  public void bindsInstanceNoParameters() {
    JavaFileObject notAbstract =
        JavaFileObjects.forSourceLines(
            "test.BindsInstanceNoParameters",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "",
            "interface BindsInstanceNoParameters {",
            "  @BindsInstance void noParams();",
            "}");
    Compilation compilation = daggerCompiler().compile(notAbstract);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@BindsInstance methods should have exactly one parameter for the bound type")
        .inFile(notAbstract)
        .onLine(6);
  }

  @Test
  public void bindsInstanceManyParameters() {
    JavaFileObject notAbstract =
        JavaFileObjects.forSourceLines(
            "test.BindsInstanceNoParameter",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "",
            "interface BindsInstanceManyParameters {",
            "  @BindsInstance void manyParams(int i, long l);",
            "}");
    Compilation compilation = daggerCompiler().compile(notAbstract);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@BindsInstance methods should have exactly one parameter for the bound type")
        .inFile(notAbstract)
        .onLine(6);
  }

  @Test
  public void bindsInstanceFrameworkType() {
    JavaFileObject bindsFrameworkType =
        JavaFileObjects.forSourceLines(
            "test.BindsInstanceFrameworkType",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.producers.Producer;",
            "import javax.inject.Provider;",
            "",
            "interface BindsInstanceFrameworkType {",
            "  @BindsInstance void bindsProvider(Provider<Object> objectProvider);",
            "  @BindsInstance void bindsProducer(Producer<Object> objectProducer);",
            "}");
    Compilation compilation = daggerCompiler().compile(bindsFrameworkType);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@BindsInstance parameters may not be framework types")
        .inFile(bindsFrameworkType)
        .onLine(8);

    assertThat(compilation)
        .hadErrorContaining("@BindsInstance parameters may not be framework types")
        .inFile(bindsFrameworkType)
        .onLine(9);
  }

  private static Compiler daggerCompiler(Processor... extraProcessors) {
    return javac().withProcessors(Lists.asList(new ComponentProcessor(), extraProcessors));
  }

  /**
   * A {@link ComponentProcessor} that excludes elements using a {@link Predicate}.
   */
  private static final class ElementFilteringComponentProcessor extends AbstractProcessor {
    private final ComponentProcessor componentProcessor = new ComponentProcessor();
    private final Predicate<? super Element> filter;

    /**
     * Creates a {@link ComponentProcessor} that only processes elements that match {@code filter}.
     */
    public ElementFilteringComponentProcessor(Predicate<? super Element> filter) {
      this.filter = filter;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
      super.init(processingEnv);
      componentProcessor.init(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return componentProcessor.getSupportedAnnotationTypes();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return componentProcessor.getSupportedSourceVersion();
    }

    @Override
    public Set<String> getSupportedOptions() {
      return componentProcessor.getSupportedOptions();
    }

    @Override
    public boolean process(
        Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
      return componentProcessor.process(
          annotations,
          new RoundEnvironment() {
            @Override
            public boolean processingOver() {
              return roundEnv.processingOver();
            }

            @Override
            public Set<? extends Element> getRootElements() {
              return Sets.filter(roundEnv.getRootElements(), filter);
            }

            @Override
            public Set<? extends Element> getElementsAnnotatedWith(Class<? extends Annotation> a) {
              return Sets.filter(roundEnv.getElementsAnnotatedWith(a), filter);
            }

            @Override
            public Set<? extends Element> getElementsAnnotatedWith(TypeElement a) {
              return Sets.filter(roundEnv.getElementsAnnotatedWith(a), filter);
            }

            @Override
            public boolean errorRaised() {
              return roundEnv.errorRaised();
            }
          });
    }
  }

  /**
   * A simple {@link Processor} that generates one source file.
   */
  private static final class GeneratingProcessor extends AbstractProcessor {
    private final String generatedClassName;
    private final String generatedSource;
    private boolean processed;

    GeneratingProcessor(String generatedClassName, String... source) {
      this.generatedClassName = generatedClassName;
      this.generatedSource = Joiner.on("\n").join(source);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (!processed) {
        processed = true;
        try (Writer writer =
                processingEnv.getFiler().createSourceFile(generatedClassName).openWriter()) {
          writer.append(generatedSource);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      return false;
    }
  }
}
