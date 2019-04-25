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
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.IMPORT_GENERATED_ANNOTATION;
import static dagger.internal.codegen.GeneratedLines.NPE_FROM_COMPONENT_METHOD;
import static dagger.internal.codegen.GeneratedLines.NPE_FROM_PROVIDES_METHOD;

import com.google.auto.common.MoreElements;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.MembersInjector;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComponentProcessorTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public ComponentProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
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

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(parent, child, another, componentFile);
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
            .withOptions(
                compilerMode.javacopts().append("-Adagger.privateMemberValidation=WARNING"))
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
        compilerMode
            .javaFileBuilder("test.DaggerSimpleComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.Lazy;",
                "import dagger.internal.DoubleCheck;",
                IMPORT_GENERATED_ANNOTATION,
                "import javax.inject.Provider;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerSimpleComponent implements SimpleComponent {")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private volatile Provider<SomeInjectableType> someInjectableTypeProvider;")
            .addLines(
                "  private DaggerSimpleComponent() {}",
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
                "  public Lazy<SomeInjectableType> lazySomeInjectableType() {")
            .addLinesIn(
                DEFAULT_MODE, //
                "    return DoubleCheck.lazy(SomeInjectableType_Factory.create());")
            .addLinesIn(
                FAST_INIT_MODE,
                "    return DoubleCheck.lazy(someInjectableTypeProvider());")
            .addLines(
                "  }",
                "",
                "  @Override",
                "  public Provider<SomeInjectableType> someInjectableTypeProvider() {")
            .addLinesIn(
                DEFAULT_MODE, //
                "    return SomeInjectableType_Factory.create();")
            .addLinesIn(
                FAST_INIT_MODE, //
                "    Object local = someInjectableTypeProvider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(0);",
                "      someInjectableTypeProvider = (Provider<SomeInjectableType>) local;",
                "    }",
                "    return (Provider<SomeInjectableType>) local;")
            .addLines(
                "  }",
                "",
                "  static final class Builder {",
                "    private Builder() {}",
                "",
                "    public SimpleComponent build() {",
                "      return new DaggerSimpleComponent();",
                "    }",
                "  }")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private final class SwitchingProvider<T> implements Provider<T> {",
                "    private final int id;",
                "",
                "    SwitchingProvider(int id) {",
                "      this.id = id;",
                "    }",
                "",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: return (T) new SomeInjectableType();",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }")
            .build();

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentFile);
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
        compilerMode
            .javaFileBuilder("test.DaggerSimpleComponent")
            .addLines(
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerSimpleComponent implements SimpleComponent {")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private volatile Object someInjectableType = new MemoizedSentinel();",
                "  private volatile Provider<SomeInjectableType> someInjectableTypeProvider;")
            .addLinesIn(
                DEFAULT_MODE,
                "  private Provider<SomeInjectableType> someInjectableTypeProvider;",
                "",
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.someInjectableTypeProvider =",
                "        DoubleCheck.provider(SomeInjectableType_Factory.create());",
                "  }",
                "")
            .addLines(
                "  @Override", //
                "  public SomeInjectableType someInjectableType() {")
            .addLinesIn(
                FAST_INIT_MODE,
                "    Object local = someInjectableType;",
                "    if (local instanceof MemoizedSentinel) {",
                "      synchronized (local) {",
                "        local = someInjectableType;",
                "        if (local instanceof MemoizedSentinel) {",
                "          local = new SomeInjectableType();",
                "          someInjectableType =",
                "              DoubleCheck.reentrantCheck(someInjectableType, local);",
                "        }",
                "      }",
                "    }",
                "    return (SomeInjectableType) local;")
            .addLinesIn(
                DEFAULT_MODE, //
                "    return someInjectableTypeProvider.get();")
            .addLines(
                "  }",
                "",
                "  @Override",
                "  public Lazy<SomeInjectableType> lazySomeInjectableType() {")
            .addLinesIn(
                DEFAULT_MODE, //
                "    return DoubleCheck.lazy(someInjectableTypeProvider);")
            .addLinesIn(
                FAST_INIT_MODE,
                "    return DoubleCheck.lazy(someInjectableTypeProvider());")
            .addLines(
                "  }",
                "",
                "  @Override",
                "  public Provider<SomeInjectableType> someInjectableTypeProvider() {")
            .addLinesIn(
                FAST_INIT_MODE, //
                "    Object local = someInjectableTypeProvider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(0);",
                "      someInjectableTypeProvider = (Provider<SomeInjectableType>) local;",
                "    }",
                "    return (Provider<SomeInjectableType>) local;")
            .addLinesIn(
                DEFAULT_MODE, //
                "    return someInjectableTypeProvider;")
            .addLines( //
                "  }")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private final class SwitchingProvider<T> implements Provider<T> {",
                "    private final int id;",
                "",
                "    SwitchingProvider(int id) {",
                "      this.id = id;",
                "    }",
                "",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0: return (T) DaggerSimpleComponent.this.someInjectableType();",
                "        default: throw new AssertionError(id);",
                "      }",
                "    }",
                "  }")
            .build();
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsElementsIn(generatedComponent);
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
        compilerMode
            .javaFileBuilder("test.DaggerOuterType_SimpleComponent")
            .addLines(
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerOuterType_SimpleComponent",
                "    implements OuterType.SimpleComponent {",
                "  private DaggerOuterType_SimpleComponent() {}",
                "",
                "  @Override",
                "  public OuterType.A a() {",
                "    return new OuterType.A();",
                "  }",
                "",
                "  @Override",
                "  public void inject(OuterType.B b) {",
                "    injectB(b);",
                "  }",
                "",
                "  @CanIgnoreReturnValue",
                "  private OuterType.B injectB(OuterType.B instance) {",
                "    OuterType_B_MembersInjector.injectA(instance, new OuterType.A());",
                "    return instance;",
                "  }",
                "}")
            .build();

    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(nestedTypesFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerOuterType_SimpleComponent")
        .containsElementsIn(generatedComponent);
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
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "",
                "import dagger.internal.Preconditions;",
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
                "  private B getB() {",
                "    return TestModule_BFactory.b(testModule, new C());",
                "  }",
                "",
                "  @Override",
                "  public A a() {",
                "    return new A(getB());",
                "  }",
                "",
                "  static final class Builder {",
                "    private TestModule testModule;",
                "",
                "    public Builder testModule(TestModule testModule) {",
                "      this.testModule = Preconditions.checkNotNull(testModule);",
                "      return this;",
                "    }",
                "",
                "    public TestComponent build() {",
                "      if (testModule == null) {",
                "        this.testModule = new TestModule();",
                "      }",
                "      return new DaggerTestComponent(testModule);",
                "    }",
                "  }",
                "}")
            .build();

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, moduleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
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
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");

    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {",
                "  private B getB() {",
                "    return TestModule_BFactory.b(new C());",
                "  }",
                "",
                "  @Override",
                "  public A a() {",
                "    return new A(getB());",
                "  }",
                "}")
            .build();

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, moduleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
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
        IMPORT_GENERATED_ANNOTATION,
        "",
        GENERATED_ANNOTATION,
        "final class DaggerTestComponent implements TestComponent {",
        "  static final class Builder {",
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
        "",
        "    public TestComponent build() {",
        "      return new DaggerTestComponent();",
        "    }",
        "  }",
        "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
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
        .containsElementsIn(generatedComponent);
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
    assertThat(
            daggerCompiler().withOptions(compilerMode.javacopts()).compile(rootModule, component))
        .failed();
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
    assertThat(
            daggerCompiler().withOptions(compilerMode.javacopts()).compile(subcomponent, component))
        .failed();
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

    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerParent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerParent implements Parent {",
            "",
            "  private DaggerParent() {}",
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
            "    return ParentModule_NotSubcomponentFactory.notSubcomponent();",
            "  }",
            "",
            "  static final class Builder {",
            "",
            "    private Builder() {}",
            "",
            "    @Deprecated",
            "    public Builder parentModule(ParentModule parentModule) {",
            "      Preconditions.checkNotNull(parentModule);",
            "      return this;",
            "    }",
            "",
            "    public Parent build() {",
            "      return new DaggerParent();",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(component, module, subcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .hasSourceEquivalentTo(generatedComponent);
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
    assertThat(
            daggerCompiler()
                .withOptions(compilerMode.javacopts())
                .compile(aModule, aClass, bClass, component))
        .succeeded();
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

    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerSimpleComponent")
            .addLines(
                "package test;",
                "",
                "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerSimpleComponent implements SimpleComponent {",
                "  @Override",
                "  public void inject(SomeInjectedType instance) {",
                "    injectSomeInjectedType(instance);",
                "  }",
                "",
                "  @Override",
                "  public SomeInjectedType injectAndReturn(SomeInjectedType instance) {",
                "    return injectSomeInjectedType(instance);",
                "  }",
                "",
                "  @CanIgnoreReturnValue",
                "  private SomeInjectedType injectSomeInjectedType(SomeInjectedType instance) {",
                "    SomeInjectedType_MembersInjector.injectInjectedField(",
                "        instance, new SomeInjectableType());",
                "    return instance;",
                "  }",
                "}")
            .build();

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, injectedTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsElementsIn(generatedComponent);
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
        "  Provider<SimpleComponent> selfProvider();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerSimpleComponent",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerSimpleComponent implements SimpleComponent {",
            "  private Provider<SimpleComponent> simpleComponentProvider;",
            "",
            "  @SuppressWarnings(\"unchecked\")",
            "  private void initialize() {",
            "    this.simpleComponentProvider = InstanceFactory.create((SimpleComponent) this);",
            "  }",
            "",
            "  @Override",
            "  public SomeInjectableType someInjectableType() {",
            "    return new SomeInjectableType(this)",
            "  }",
            "",
            "  @Override",
            "  public Provider<SimpleComponent> selfProvider() {",
            "    return simpleComponentProvider;",
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

    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerSimpleComponent")
            .addLines(
                "package test;",
                "",
                "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerSimpleComponent implements SimpleComponent {",
                "  @Override",
                "  public SomeInjectedType createAndInject() {",
                "    return injectSomeInjectedType(",
                "        SomeInjectedType_Factory.newInstance());",
                "  }",
                "",
                "  @CanIgnoreReturnValue",
                "  private SomeInjectedType injectSomeInjectedType(SomeInjectedType instance) {",
                "    SomeInjectedType_MembersInjector.injectInjectedField(",
                "        instance, new SomeInjectableType());",
                "    return instance;",
                "  }",
                "}")
            .build();

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, injectedTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsElementsIn(generatedComponent);
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
        "import javax.inject.Provider;",
        "",
        "final class B {",
        "  @Inject B(Provider<A> a) {}",
        "}");
    JavaFileObject aComponentFile = JavaFileObjects.forSourceLines("test.AComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface AComponent {",
        "  A a();",
        "}");
    JavaFileObject bComponentFile = JavaFileObjects.forSourceLines("test.AComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = AComponent.class)",
        "interface BComponent {",
        "  B b();",
        "}");
    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerBComponent")
            .addLines(
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerBComponent implements BComponent {")
            .addLinesIn(
                DEFAULT_MODE,
                "  private Provider<A> aProvider;")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private final AComponent aComponent;",
                "  private volatile Provider<A> aProvider;",
                "",
                "  private DaggerBComponent(AComponent aComponentParam) {",
                "    this.aComponent = aComponentParam;",
                "  }",
                "",
                "  private Provider<A> getAProvider() {",
                "    Object local = aProvider;",
                "    if (local == null) {",
                "      local = new SwitchingProvider<>(0);",
                "      aProvider = (Provider<A>) local;",
                "    }",
                "    return (Provider<A>) local;",
                "  }")
            .addLinesIn(
                DEFAULT_MODE,
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize(final AComponent aComponentParam) {",
                "    this.aProvider = new test_AComponent_a(aComponentParam);",
                "  }")
            .addLines(
                "",
                "  @Override",
                "  public B b() {")
            .addLinesIn(
                DEFAULT_MODE,
                "    return new B(aProvider);")
            .addLinesIn(
                FAST_INIT_MODE,
                "    return new B(getAProvider());")
            .addLines(
                "  }",
                "",
                "  static final class Builder {",
                "    private AComponent aComponent;",
                "",
                "    public Builder aComponent(AComponent aComponent) {",
                "      this.aComponent = Preconditions.checkNotNull(aComponent);",
                "      return this;",
                "    }",
                "",
                "    public BComponent build() {",
                "      Preconditions.checkBuilderRequirement(aComponent, AComponent.class);",
                "      return new DaggerBComponent(aComponent);",
                "    }",
                "  }")
            .addLinesIn(
                DEFAULT_MODE,
                "  private static class test_AComponent_a implements Provider<A> {",
                "    private final AComponent aComponent;",
                "    ",
                "    test_AComponent_a(AComponent aComponent) {",
                "        this.aComponent = aComponent;",
                "    }",
                "    ",
                "    @Override()",
                "    public A get() {",
                "      return Preconditions.checkNotNull(",
                "          aComponent.a(), " + NPE_FROM_COMPONENT_METHOD + ");",
                "    }",
                "  }",
                "}")
            .addLinesIn(
                FAST_INIT_MODE,
                "  private final class SwitchingProvider<T> implements Provider<T> {",
                "    @SuppressWarnings(\"unchecked\")",
                "    @Override",
                "    public T get() {",
                "      switch (id) {",
                "        case 0:",
                "          return (T)",
                "              Preconditions.checkNotNull(",
                "                  DaggerBComponent.this.aComponent.a(),",
                "                  " + NPE_FROM_COMPONENT_METHOD + ");",
                "        default:",
                "          throw new AssertionError(id);",
                "      }",
                "    }",
                "  }")
            .build();
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(aFile, bFile, aComponentFile, bComponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerBComponent")
        .containsElementsIn(generatedComponent);
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
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  private final TestModule testModule;",
            "  private final other.test.TestModule testModule2;",
            "",
            "  private DaggerTestComponent(",
            "      TestModule testModuleParam,",
            "      other.test.TestModule testModule2Param) {",
            "    this.testModule = testModuleParam;",
            "    this.testModule2 = testModule2Param;",
            "  }",
            "",
            "  @Override",
            "  public A a() {",
            "    return TestModule_AFactory.a(testModule);",
            "  }",
            "",
            "  @Override",
            "  public other.test.A otherA() {",
            "    return other.test.TestModule_AFactory.a(testModule2);",
            "  }",
            "",
            "  static final class Builder {",
            "    private TestModule testModule;",
            "    private other.test.TestModule testModule2;",
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
            "",
            "    public TestComponent build() {",
            "      if (testModule == null) {",
            "        this.testModule = new TestModule();",
            "      }",
            "      if (testModule2 == null) {",
            "        this.testModule2 = new other.test.TestModule();",
            "      }",
            "      return new DaggerTestComponent(testModule, testModule2);",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(aFile, otherAFile, moduleFile, otherModuleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test public void ignoresDependencyMethodsFromObject() {
    JavaFileObject injectedTypeFile =
        JavaFileObjects.forSourceLines(
            "test.InjectedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class InjectedType {",
            "  @Inject InjectedType(",
            "      String stringInjection,",
            "      int intInjection,",
            "      AComponent aComponent,",
            "      Class<AComponent> aClass) {}",
            "}");
    JavaFileObject aComponentFile =
        JavaFileObjects.forSourceLines(
            "test.AComponent",
            "package test;",
            "",
            "class AComponent {",
            "  String someStringInjection() {",
            "    return \"injectedString\";",
            "  }",
            "",
            "  int someIntInjection() {",
            "    return 123;",
            "  }",
            "",
            "  Class<AComponent> someClassInjection() {",
            "    return AComponent.class;",
            "  }",
            "",
            "  @Override",
            "  public String toString() {",
            "    return null;",
            "  }",
            "",
            "  @Override",
            "  public int hashCode() {",
            "    return 456;",
            "  }",
            "",
            "  @Override",
            "  public AComponent clone() {",
            "    return null;",
            "  }",
            "}");
    JavaFileObject bComponentFile =
        JavaFileObjects.forSourceLines(
            "test.AComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = AComponent.class)",
            "interface BComponent {",
            "  InjectedType injectedType();",
            "}");

    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerBComponent",
            "package test;",
            "",
            "import dagger.internal.Preconditions;",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerBComponent implements BComponent {",
            "  private final AComponent aComponent;",
            "",
            "  private DaggerBComponent(AComponent aComponentParam) {",
            "    this.aComponent = aComponentParam;",
            "  }",
            "",
            "  @Override",
            "  public InjectedType injectedType() {",
            "    return new InjectedType(",
            "        Preconditions.checkNotNull(",
            "            aComponent.someStringInjection(),",
            "            \"Cannot return null from a non-@Nullable component method\"),",
            "        aComponent.someIntInjection(),",
            "        aComponent,",
            "        Preconditions.checkNotNull(",
            "            aComponent.someClassInjection(),",
            "            \"Cannot return null from a non-@Nullable component method\"));",
            "  }",
            "}");

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(injectedTypeFile, aComponentFile, bComponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerBComponent")
        .containsElementsIn(generatedComponent);
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
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "",
                IMPORT_GENERATED_ANNOTATION,
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {",
                "  private B getB() {",
                "    return new B(new C());",
                "  }",
                "",
                "  @Override",
                "  public A a() {",
                "    return new A(getB());",
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
                "}")
            .build();

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, xFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerSimpleComponent implements SimpleComponent {",
            "  private DaggerSimpleComponent() {}",
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
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    public SimpleComponent build() {",
            "      return new DaggerSimpleComponent();",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
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
        "",
        "@Component",
        "interface Supertype {",
        "  SomeInjectableType someInjectableType();",
        "}");
    JavaFileObject depComponentFile = JavaFileObjects.forSourceLines("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SimpleComponent extends Supertype {",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerSimpleComponent",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerSimpleComponent implements SimpleComponent {",
            "  @Override",
            "  public SomeInjectableType someInjectableType() {",
            "    return new SomeInjectableType();",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentSupertype, depComponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .containsElementsIn(generatedComponent);
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
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.B<? extends test.A> cannot be provided without an @Provides-annotated method");
  }

  // https://github.com/google/dagger/issues/630
  @Test
  public void arrayKeyRequiresAtProvides() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  String[] array();",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("String[] cannot be provided without an @Provides-annotated method");
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
            .withOptions(compilerMode.javacopts())
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
            .withOptions(compilerMode.javacopts())
            .compile(componentFile, interfaceFile);
    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.DaggerSimpleComponent");
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
                compilerMode
                    .javacopts()
                    .append(
                        "-Xlint:-processing",
                        "-Adagger.warnIfInjectionFactoryNotGeneratedUpstream=enabled"))
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
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(aScope, aClass);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Scope annotations are not allowed on @Inject constructors")
        .inFile(aClass)
        .onLine(6);
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
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerParent implements Parent {",
            "  private DaggerParent() {",
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
            "  static final class Builder {",
            "    private Builder() {}",
            "",
            "    @Deprecated",
            "    public Builder testModule(TestModule testModule) {",
            "      Preconditions.checkNotNull(testModule);",
            "      return this;",
            "    }",
            "",
            "    public Parent build() {",
            "      return new DaggerParent();",
            "    }",
            "  }",
            "}");

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(foo, module, component, prunedSubcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .hasSourceEquivalentTo(generated);
  }

  @Test
  public void bindsToDuplicateBinding_bindsKeyIsNotDuplicated() {
    JavaFileObject firstModule =
        JavaFileObjects.forSourceLines(
            "test.FirstModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class FirstModule {",
            "  @Provides static String first() { return \"first\"; }",
            "}");
    JavaFileObject secondModule =
        JavaFileObjects.forSourceLines(
            "test.SecondModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class SecondModule {",
            "  @Provides static String second() { return \"second\"; }",
            "}");
    JavaFileObject bindsModule =
        JavaFileObjects.forSourceLines(
            "test.BindsModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class BindsModule {",
            "  @Binds abstract Object bindToDuplicateBinding(String duplicate);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = {FirstModule.class, SecondModule.class, BindsModule.class})",
            "interface TestComponent {",
            "  Object notDuplicated();",
            "}");

    Compilation compilation =
        daggerCompiler().compile(firstModule, secondModule, bindsModule, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining("java.lang.String is bound multiple times")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void nullIncorrectlyReturnedFromNonNullableInlinedProvider() {
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.TestModule",
                    "package test;",
                    "",
                    "import dagger.Module;",
                    "import dagger.Provides;",
                    "",
                    "@Module",
                    "public abstract class TestModule {",
                    "  @Provides static String nonNullableString() { return \"string\"; }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.InjectsMember",
                    "package test;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "public class InjectsMember {",
                    "  @Inject String member;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.TestComponent",
                    "package test;",
                    "",
                    "import dagger.Component;",
                    "",
                    "@Component(modules = TestModule.class)",
                    "interface TestComponent {",
                    "  String nonNullableString();",
                    "  void inject(InjectsMember member);",
                    "}"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.TestModule_NonNullableStringFactory")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.TestModule_NonNullableStringFactory",
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "public final class TestModule_NonNullableStringFactory",
                "    implements Factory<String> {",
                "  @Override",
                "  public String get() {",
                "    return nonNullableString();",
                "  }",
                "",
                "  public static String nonNullableString() {",
                "    return Preconditions.checkNotNull(",
                "        TestModule.nonNullableString(), " + NPE_FROM_PROVIDES_METHOD + ");",
                "  }",
                "}"));

    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {",
                "  @Override",
                "  public String nonNullableString() {",
                "    return TestModule_NonNullableStringFactory.nonNullableString());",
                "  }",
                "",
                "  @Override",
                "  public void inject(InjectsMember member) {",
                "    injectInjectsMember(member);",
                "  }",
                "",
                "  @CanIgnoreReturnValue",
                "  private InjectsMember injectInjectsMember(InjectsMember instance) {",
                "    InjectsMember_MembersInjector.injectMember(instance,",
                "        TestModule_NonNullableStringFactory.nonNullableString());",
                "    return instance;",
                "  }",
                "}")
            .build();

    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void nullCheckingIgnoredWhenProviderReturnsPrimitive() {
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.TestModule",
                    "package test;",
                    "",
                    "import dagger.Module;",
                    "import dagger.Provides;",
                    "",
                    "@Module",
                    "public abstract class TestModule {",
                    "  @Provides static int primitiveInteger() { return 1; }",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.InjectsMember",
                    "package test;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "public class InjectsMember {",
                    "  @Inject Integer member;",
                    "}"),
                JavaFileObjects.forSourceLines(
                    "test.TestComponent",
                    "package test;",
                    "",
                    "import dagger.Component;",
                    "",
                    "@Component(modules = TestModule.class)",
                    "interface TestComponent {",
                    "  Integer nonNullableInteger();",
                    "  void inject(InjectsMember member);",
                    "}"));
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.TestModule_PrimitiveIntegerFactory")
        .containsElementsIn(
            JavaFileObjects.forSourceLines(
                "test.TestModule_PrimitiveIntegerFactory",
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "public final class TestModule_PrimitiveIntegerFactory",
                "    implements Factory<Integer> {",
                "",
                "  @Override",
                "  public Integer get() {",
                "    return primitiveInteger();",
                "  }",
                "",
                "  public static int primitiveInteger() {",
                "    return TestModule.primitiveInteger();",
                "  }",
                "}"));

    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerTestComponent")
            .addLines(
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerTestComponent implements TestComponent {",
                "  @Override",
                "  public Integer nonNullableInteger() {",
                "    return TestModule.primitiveInteger();",
                "  }",
                "",
                "  @Override",
                "  public void inject(InjectsMember member) {",
                "    injectInjectsMember(member);",
                "  }",
                "",
                "  @CanIgnoreReturnValue",
                "  private InjectsMember injectInjectsMember(InjectsMember instance) {",
                "    InjectsMember_MembersInjector.injectMember(",
                "        instance, TestModule.primitiveInteger());",
                "    return instance;",
                "  }",
                "}")
            .build();

    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void privateMethodUsedOnlyInChildDoesNotUseQualifiedThis() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules=TestModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides @Singleton static Number number() {",
            "    return 3;",
            "  }",
            "",
            "  @Provides static String string(Number number) {",
            "    return number.toString();",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  String string();",
            "}");

    JavaFileObject expectedPattern =
        JavaFileObjects.forSourceLines(
            "test.DaggerParent",
            "package test;",
            GENERATED_ANNOTATION,
            "final class DaggerParent implements Parent {",
            "  private String getString() {",
            "    return TestModule_StringFactory.string(numberProvider.get());",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(parent, testModule, child);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .containsElementsIn(expectedPattern);
  }

  @Test
  public void componentMethodInChildCallsComponentMethodInParent() {
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  String string();",
            "}");
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules=TestModule.class)",
            "interface Parent extends Supertype {",
            "  Child child();",
            "}");
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides @Singleton static Number number() {",
            "    return 3;",
            "  }",
            "",
            "  @Provides static String string(Number number) {",
            "    return number.toString();",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child extends Supertype {}");

    JavaFileObject expectedPattern =
        JavaFileObjects.forSourceLines(
            "test.DaggerParent",
            "package test;",
            GENERATED_ANNOTATION,
            "final class DaggerParent implements Parent {",
            "  private final class ChildImpl implements Child {",
            "    @Override",
            "    public String string() {",
            "      return DaggerParent.this.string();",
            "    }",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(supertype, parent, testModule, child);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .containsElementsIn(expectedPattern);
  }

  @Test
  public void justInTimeAtInjectConstructor_hasGeneratedQualifier() {
    JavaFileObject injected =
        JavaFileObjects.forSourceLines(
            "test.Injected",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Injected {",
            "  @Inject Injected(@GeneratedQualifier String string) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String unqualified() {",
            "    return new String();",
            "  }",
            "",
            "  @Provides",
            "  @GeneratedQualifier",
            "  static String qualified() {",
            "    return new String();",
            "  }",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Injected injected();",
            "}");

    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  @Override",
            "  public Injected injected() {",
            // Ensure that the qualified @Provides method is used. It's also probably more likely
            // that if the qualifier type hasn't been generated, a duplicate binding error will be
            // reported, since the annotation won't be recognized as a qualifier and instead as an
            // ordinary annotation.
            "    return new Injected(TestModule_QualifiedFactory.qualified());",
            "  }",
            "}");

    Compilation compilation =
        daggerCompiler(
                new GeneratingProcessor(
                    "test.GeneratedQualifier",
                    "package test;",
                    "",
                    "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
                    "",
                    "import java.lang.annotation.Retention;",
                    "import javax.inject.Qualifier;",
                    "",
                    "@Retention(RUNTIME)",
                    "@Qualifier",
                    "@interface GeneratedQualifier {}"))
            .compile(injected, module, component);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void moduleHasGeneratedQualifier() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String unqualified() {",
            "    return new String();",
            "  }",
            "",
            "  @Provides",
            "  @GeneratedQualifier",
            "  static String qualified() {",
            "    return new String();",
            "  }",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  String unqualified();",
            "}");

    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerTestComponent implements TestComponent {",
            "  @Override",
            "  public String unqualified() {",
            // Ensure that the unqualified @Provides method is used. It's also probably more likely
            // if the qualifier hasn't been generated, a duplicate binding exception will be thrown
            // since the annotation won't be considered a qualifier
            "    return TestModule_UnqualifiedFactory.unqualified();",
            "  }",
            "}");

    Compilation compilation =
        daggerCompiler(
            new GeneratingProcessor(
                "test.GeneratedQualifier",
                "package test;",
                "",
                "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
                "",
                "import java.lang.annotation.Retention;",
                "import javax.inject.Qualifier;",
                "",
                "@Retention(RUNTIME)",
                "@Qualifier",
                "@interface GeneratedQualifier {}"))
            .compile(module, component);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void publicComponentType() {
    JavaFileObject publicComponent =
        JavaFileObjects.forSourceLines(
            "test.PublicComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "public interface PublicComponent {}");
    Compilation compilation = daggerCompiler().compile(publicComponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerPublicComponent")
        .hasSourceEquivalentTo(
            JavaFileObjects.forSourceLines(
                "test.DaggerPublicComponent",
                "package test;",
                "",
                IMPORT_GENERATED_ANNOTATION,
                "",
                GENERATED_ANNOTATION,
                "public final class DaggerPublicComponent implements PublicComponent {",
                "  private DaggerPublicComponent() {}",
                "",
                "  public static Builder builder() {",
                "    return new Builder();",
                "  }",
                "",
                "  public static PublicComponent create() {",
                "    return new Builder().build();",
                "  }",
                "",
                "  public static final class Builder {",
                "    private Builder() {}",
                "",
                "    public PublicComponent build() {",
                "      return new DaggerPublicComponent();",
                "    }",
                "  }",
                "}"));
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
}
