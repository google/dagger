/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;
import dagger.internal.codegen.writer.StringLiteral;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.ErrorMessages.REFERENCED_MODULES_MUST_NOT_BE_ABSTRACT;

@RunWith(JUnit4.class)
public class ComponentProcessorTest {
  private static final StringLiteral NPE_LITERAL =
      StringLiteral.forValue(ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD);

  @Test public void componentOnConcreteClass() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "final class NotAComponent {}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("interface");
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
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("interface");
  }

  @Test public void componentOnAnnotation() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "@interface NotAComponent {}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("interface");
  }

  @Test public void nonModuleModule() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.NotAComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = Object.class)",
        "interface NotAComponent {}");
    assertAbout(javaSource()).that(componentFile)
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("is not annotated with @Module");
  }

  private void checkCannotReferToModuleOfType(String moduleType) {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        moduleType + " TestModule {}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.BadComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface BadComponent {}");
    assertAbout(javaSources()).that(ImmutableList.of(moduleFile, componentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
            String.format(REFERENCED_MODULES_MUST_NOT_BE_ABSTRACT, "test.TestModule"));
  }

  @Test public void cannotReferToAbstractClassModules() {
    checkCannotReferToModuleOfType("abstract class");
  }

  @Test public void cannotReferToInterfaceModules() {
    checkCannotReferToModuleOfType("interface");
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
    assertAbout(javaSources()).that(ImmutableList.of(parent, child, another, componentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile().withErrorContaining(
            "java.util.List<java.lang.Integer> is bound multiple times")
        .and().withErrorContaining(
            "@Provides List<Integer> test.ChildNumberModule.provideListB(Integer)")
        .and().withErrorContaining(
            "@Provides List<Integer> test.AnotherModule.provideListOfInteger()");
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
        "",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "  Lazy<SomeInjectableType> lazySomeInjectableType();",
        "  Provider<SomeInjectableType> someInjectableTypeProvider();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerSimpleComponent",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import dagger.internal.DoubleCheckLazy;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class DaggerSimpleComponent implements SimpleComponent {",
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
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize(final Builder builder) {",
        "  }",
        "",
        "  @Override",
        "  public SomeInjectableType someInjectableType() {",
        "    return SomeInjectableType_Factory.create().get();",
        "  }",
        "",
        "  @Override",
        "  public Lazy<SomeInjectableType> lazySomeInjectableType() {",
        "    return DoubleCheckLazy.create(SomeInjectableType_Factory.create());",
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
    assertAbout(javaSources()).that(ImmutableList.of(injectableTypeFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerSimpleComponent",
        "package test;",
        "",
        "import dagger.Lazy;",
        "import dagger.internal.DoubleCheckLazy;",
        "import dagger.internal.ScopedProvider;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
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
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize(final Builder builder) {",
        "    this.someInjectableTypeProvider =",
        "        ScopedProvider.create(SomeInjectableType_Factory.create());",
        "  }",
        "",
        "  @Override",
        "  public SomeInjectableType someInjectableType() {",
        "    return someInjectableTypeProvider.get();",
        "  }",
        "",
        "  @Override",
        "  public Lazy<SomeInjectableType> lazySomeInjectableType() {",
        "    return DoubleCheckLazy.create(someInjectableTypeProvider);",
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
    assertAbout(javaSources()).that(ImmutableList.of(injectableTypeFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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

    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerOuterType_SimpleComponent",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import javax.annotation.Generated;",
        "import test.OuterType.A;",
        "import test.OuterType.B;",
        "import test.OuterType.SimpleComponent;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class DaggerOuterType_SimpleComponent implements SimpleComponent {",
        "  private MembersInjector<B> bMembersInjector;",
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
        "  public static SimpleComponent create() {",
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize(final Builder builder) {",
        "    this.bMembersInjector =",
        "        OuterType$B_MembersInjector.create(OuterType$A_Factory.create());",
        "  }",
        "",
        "  @Override",
        "  public A a() {",
        "    return OuterType$A_Factory.create().get();",
        "  }",
        "",
        "  @Override",
        "  public void inject(B b) {",
        "    bMembersInjector.injectMembers(b);",
        "  }",
        "",
        "  public static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public SimpleComponent build() {",
        "      return new DaggerOuterType_SimpleComponent(this);",
        "    }",
        "  }",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(nestedTypesFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  A a();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerTestComponent",
        "package test;",
        "",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
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
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize(final Builder builder) {",
        "    this.bProvider = TestModule_BFactory.create(builder.testModule,",
        "        C_Factory.create());",
        "    this.aProvider = A_Factory.create(bProvider);",
        "  }",
        "",
        "  @Override",
        "  public A a() {",
        "    return aProvider.get();",
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
        "      if (testModule == null) {",
        "        throw new NullPointerException(\"testModule\");",
        "      }",
        "      this.testModule = testModule;",
        "      return this;",
        "    }",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(aFile, bFile, cFile, moduleFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "",
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
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
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
        "    return builder().build();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private TestModule testModule;",
        "    private ParentTestIncluded parentTestIncluded;",
        "    private AlwaysIncluded alwaysIncluded;",
        "    private DepModule depModule;",
        "    private ParentDepIncluded parentDepIncluded;",
        "    private RefByDep refByDep;",
        "",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      if (testModule == null) {",
        "        this.testModule = new TestModule();",
        "      }",
        "      if (parentTestIncluded == null) {",
        "        this.parentTestIncluded = new ParentTestIncluded();",
        "      }",
        "      if (alwaysIncluded == null) {",
        "        this.alwaysIncluded = new AlwaysIncluded();",
        "      }",
        "      if (depModule == null) {",
        "        this.depModule = new DepModule();",
        "      }",
        "      if (parentDepIncluded == null) {",
        "        this.parentDepIncluded = new ParentDepIncluded();",
        "      }",
        "      if (refByDep == null) {",
        "        this.refByDep = new RefByDep();",
        "      }",
        "      return new DaggerTestComponent(this);",
        "    }",
        "",
        "    public Builder testModule(TestModule testModule) {",
        "      if (testModule == null) {",
        "        throw new NullPointerException(\"testModule\");",
        "      }",
        "      this.testModule = testModule;",
        "      return this;",
        "    }",
        "",
        "    public Builder parentTestIncluded(ParentTestIncluded parentTestIncluded) {",
        "      if (parentTestIncluded == null) {",
        "        throw new NullPointerException(\"parentTestIncluded\");",
        "      }",
        "      this.parentTestIncluded = parentTestIncluded;",
        "      return this;",
        "    }",
        "",
        "    public Builder alwaysIncluded(AlwaysIncluded alwaysIncluded) {",
        "      if (alwaysIncluded == null) {",
        "        throw new NullPointerException(\"alwaysIncluded\");",
        "      }",
        "      this.alwaysIncluded = alwaysIncluded;",
        "      return this;",
        "    }",
        "",
        "    public Builder depModule(DepModule depModule) {",
        "      if (depModule == null) {",
        "        throw new NullPointerException(\"depModule\");",
        "      }",
        "      this.depModule = depModule;",
        "      return this;",
        "    }",
        "",
        "    public Builder parentDepIncluded(ParentDepIncluded parentDepIncluded) {",
        "      if (parentDepIncluded == null) {",
        "        throw new NullPointerException(\"parentDepIncluded\");",
        "      }",
        "      this.parentDepIncluded = parentDepIncluded;",
        "      return this;",
        "    }",
        "",
        "    public Builder refByDep(RefByDep refByDep) {",
        "      if (refByDep == null) {",
        "        throw new NullPointerException(\"refByDep\");",
        "      }",
        "      this.refByDep = refByDep;",
        "      return this;",
        "    }",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(always,
            testModule,
            parentTest,
            parentTestIncluded,
            depModule,
            refByDep,
            parentDep,
            parentDepIncluded,
            componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
    assertAbout(javaSources())
        .that(ImmutableList.of(rootModule, component))
        .processedWith(new ComponentProcessor())
        .failsToCompile();
    assertAbout(javaSources())
        .that(ImmutableList.of(rootModule, component))
        .processedWith(
            new ComponentProcessor(),
            new GeneratingProcessor(
                "test.GeneratedModule",
                "package test;",
                "",
                "import dagger.Module;",
                "",
                "@Module",
                "final class GeneratedModule {}"))
        .compilesWithoutError();
  }

  @Test public void testDefaultPackage() {
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
    assertAbout(javaSources())
        .that(ImmutableList.of(aModule, aClass, bClass, component))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
  }

  @Test public void setBindings() {
    JavaFileObject emptySetModuleFile = JavaFileObjects.forSourceLines("test.EmptySetModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import java.util.Collections;",
        "import java.util.Set;",
        "",
        "@Module",
        "final class EmptySetModule {",
        "  @Provides(type = SET_VALUES) Set<String> emptySet() { return Collections.emptySet(); }",
        "}");
    JavaFileObject setModuleFile = JavaFileObjects.forSourceLines("test.SetModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class SetModule {",
        "  @Provides(type = SET) String string() { return \"\"; }",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Set;",
        "",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = {EmptySetModule.class, SetModule.class})",
        "interface TestComponent {",
        "  Set<String> strings();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerTestComponent",
        "package test;",
        "",
        "import dagger.internal.SetFactory;",
        "import java.util.Set;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class DaggerTestComponent implements TestComponent {",
        "  private Provider<Set<String>> setOfStringContribution1Provider;",
        "  private Provider<Set<String>> setOfStringContribution2Provider;",
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
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize(final Builder builder) {",
        "    this.setOfStringContribution1Provider =",
        "        EmptySetModule_EmptySetFactory.create(builder.emptySetModule);",
        "    this.setOfStringContribution2Provider =",
        "        SetModule_StringFactory.create(builder.setModule);",
        "    this.setOfStringProvider = SetFactory.create(",
        "        setOfStringContribution1Provider, setOfStringContribution2Provider);",
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
        "      if (emptySetModule == null) {",
        "        throw new NullPointerException(\"emptySetModule\");",
        "      }",
        "      this.emptySetModule = emptySetModule;",
        "      return this;",
        "    }",
        "",
        "    public Builder setModule(SetModule setModule) {",
        "      if (setModule == null) {",
        "        throw new NullPointerException(\"setModule\");",
        "      }",
        "      this.setModule = setModule;",
        "      return this;",
        "    }",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(emptySetModuleFile, setModuleFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "",
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
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
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
        "    return builder().build();",
        "  }",
        "",
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
    assertAbout(javaSources())
        .that(ImmutableList.of(injectableTypeFile, injectedTypeFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerSimpleComponent",
        "package test;",
        "",
        "import dagger.internal.InstanceFactory;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
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
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize(final Builder builder) {",
        "    this.simpleComponentProvider = InstanceFactory.<SimpleComponent>create(this);",
        "    this.someInjectableTypeProvider =",
        "        SomeInjectableType_Factory.create(simpleComponentProvider);",
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
    assertAbout(javaSources()).that(ImmutableList.of(injectableTypeFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
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
        "    return builder().build();",
        "  }",
        "",
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
    assertAbout(javaSources())
        .that(ImmutableList.of(injectableTypeFile, injectedTypeFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "import dagger.MembersInjector;",
        "import dagger.internal.MembersInjectors;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
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
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize(final Builder builder) {",
        "    this.someInjectableTypeProvider =",
        "        SomeInjectableType_Factory.create((MembersInjector) MembersInjectors.noOp());",
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
    assertAbout(javaSources())
        .that(ImmutableList.of(genericType, injectableTypeFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "",
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
        "",
        "import javax.inject.Provider;",
        "",
        "@Component(dependencies = AComponent.class)",
        "interface BComponent {",
        "  B b();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerBComponent",
        "package test;",
        "",
        "import dagger.internal.Factory;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
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
        "  private void initialize(final Builder builder) {",
        "    this.aProvider = new Factory<A>() {",
        "      private final AComponent aComponent = builder.aComponent;",
        "      @Override public A get() {",
        "        A provided = aComponent.a();",
        "        if (provided == null) {",
        "          throw new NullPointerException(" + NPE_LITERAL + ");",
        "        }",
        "        return provided;",
        "      }",
        "    };",
        "    this.bProvider = B_Factory.create(aProvider);",
        "  }",
        "",
        "  @Override",
        "  public B b() {",
        "    return bProvider.get();",
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
        "        throw new IllegalStateException(\"aComponent must be set\");",
        "      }",
        "      return new DaggerBComponent(this);",
        "    }",
        "",
        "    public Builder aComponent(AComponent aComponent) {",
        "      if (aComponent == null) {",
        "        throw new NullPointerException(\"aComponent\");",
        "      }",
        "      this.aComponent = aComponent;",
        "      return this;",
        "    }",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(aFile, bFile, aComponentFile, bComponentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "",
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
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "import other.test.A;",
        "import other.test.TestModule;",
        "import other.test.TestModule_AFactory;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class DaggerTestComponent implements TestComponent {",
        "  private Provider<test.A> aProvider;",
        "  private Provider<A> aProvider1;",
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
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize(final Builder builder) {",
        "    this.aProvider = test.TestModule_AFactory.create(builder.testModule);",
        "    this.aProvider1 = TestModule_AFactory.create(builder.testModule1);",
        "  }",
        "",
        "  @Override",
        "  public test.A a() {",
        "    return aProvider.get();",
        "  }",
        "",
        "  @Override",
        "  public A otherA() {",
        "    return aProvider1.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private test.TestModule testModule;",
        "    private TestModule testModule1;",
        "",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      if (testModule == null) {",
        "        this.testModule = new test.TestModule();",
        "      }",
        "      if (testModule1 == null) {",
        "        this.testModule1 = new TestModule();",
        "      }",
        "      return new DaggerTestComponent(this);",
        "    }",
        "",
        "    public Builder testModule(test.TestModule testModule) {",
        "      if (testModule == null) {",
        "        throw new NullPointerException(\"testModule\");",
        "      }",
        "      this.testModule = testModule;",
        "      return this;",
        "    }",
        "",
        "    public Builder testModule(TestModule testModule) {",
        "      if (testModule == null) {",
        "        throw new NullPointerException(\"testModule\");",
        "      }",
        "      this.testModule1 = testModule;",
        "      return this;",
        "    }",
        "  }",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(aFile, otherAFile, moduleFile, otherModuleFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface TestComponent {",
        "  A a();",
        "  C c();",
        "  X x();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerTestComponent",
        "package test;",
        "",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
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
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize(final Builder builder) {",
        "    this.bProvider = B_Factory.create(C_Factory.create());",
        "    this.aProvider = A_Factory.create(bProvider);",
        "    this.xProvider = X_Factory.create(C_Factory.create());",
        "  }",
        "",
        "  @Override",
        "  public A a() {",
        "    return aProvider.get();",
        "  }",
        "",
        "  @Override",
        "  public C c() {",
        "    return C_Factory.create().get();",
        "  }",
        "",
        "  @Override",
        "  public X x() {",
        "    return xProvider.get();",
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
    assertAbout(javaSources())
        .that(ImmutableList.of(aFile, bFile, cFile, xFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "",
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
        "",
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
        "",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent extends SupertypeA, SupertypeB {",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerSimpleComponent",
        "package test;",
        "",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class DaggerSimpleComponent implements SimpleComponent {",
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
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize(final Builder builder) {}",
        "",
        "  @Override",
        "  public SomeInjectableType someInjectableType() {",
        "    return SomeInjectableType_Factory.create().get();",
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
    assertAbout(javaSources()).that(ImmutableList.of(
            injectableTypeFile, componentSupertypeAFile, componentSupertypeBFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "",
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
        "",
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
        "",
        "import javax.inject.Provider;",
        "",
        "@Component(dependencies = SimpleComponent.class)",
        "interface ComponentWithDep {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.DaggerSimpleComponent",
        "package test;",
        "",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class DaggerSimpleComponent implements SimpleComponent {",
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
        "    return builder().build();",
        "  }",
        "",
        "  private void initialize(final Builder builder) {}",
        "",
        "  @Override",
        "  public SomeInjectableType someInjectableType() {",
        "    return SomeInjectableType_Factory.create().get();",
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
    assertAbout(javaSources()).that(ImmutableList.of(
            injectableTypeFile, componentSupertype, depComponentFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
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
        "",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  C c();",
        "}");
    assertAbout(javaSources()).that(ImmutableList.of(aFile, bFile, cFile, componentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining(
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
        "import dagger.Lazy;",
        "",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface SimpleComponent {",
        "  SomeInjectableType someInjectableType();",
        "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(injectableTypeFile, componentFile))
        .processedWith(
            new ComponentProcessor(),
            new GeneratingProcessor("test.GeneratedType",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "final class GeneratedType {",
                "  @Inject GeneratedType() {}",
                "}"))
        .compilesWithoutError();
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
         "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
         "public final class DaggerSimpleComponent implements SimpleComponent {",
         "  private Provider<D> dProvider;",
         "",
         "  private DaggerSimpleComponent(Builder builder) {",
         "    assert builder != null;",
         "    initialize();",
         "  }",
         "",
         "  public static Builder builder() {",
         "    return new Builder();",
         "  }",
         "",
         "  public static SimpleComponent create() {",
         "    return builder().build();",
         "  }",
         "",
         "  private void initialize() {",
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
     assertAbout(javaSources()).that(ImmutableList.of(aFile, bFile, componentFile))
         .processedWith(new ComponentProcessor())
         .compilesWithoutError()
         .and().generatesSources(generatedComponent);
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
