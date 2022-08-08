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
import static dagger.internal.codegen.Compilers.compilerWithOptions;

import androidx.room.compiler.processing.util.Source;
import com.google.auto.common.MoreElements;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.MembersInjector;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
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
import org.junit.Rule;
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

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public ComponentProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test public void doubleBindingFromResolvedModules() {
    Source parent = CompilerTests.javaSource("test.ParentModule",
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
    Source child = CompilerTests.javaSource("test.ChildNumberModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "class ChildNumberModule extends ParentModule<Integer> {",
        "  @Provides Integer provideInteger() { return null; }",
        "}");
    Source another = CompilerTests.javaSource("test.AnotherModule",
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
    Source componentFile = CompilerTests.javaSource("test.BadComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.List;",
        "",
        "@Component(modules = {ChildNumberModule.class, AnotherModule.class})",
        "interface BadComponent {",
        "  List<Integer> listOfInteger();",
        "}");

    CompilerTests.daggerCompiler(parent, child, another, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("List<Integer> is bound multiple times");
              subject.hasErrorContaining(
                  "@Provides List<Integer> ChildNumberModule.provideListB(Integer)");
              subject.hasErrorContaining(
                  "@Provides List<Integer> AnotherModule.provideListOfInteger()");
            });
  }

  @Test public void privateNestedClassWithWarningThatIsAnErrorInComponent() {
    Source outerClass = CompilerTests.javaSource("test.OuterClass",
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
    Source componentFile = CompilerTests.javaSource("test.BadComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface BadComponent {",
        "  OuterClass outerClass();",
        "}");
    CompilerTests.daggerCompiler(outerClass, componentFile)
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.privateMemberValidation", "WARNING")
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Dagger does not support injection into private classes");
            });
  }

  @Test public void simpleComponent() throws Exception {
    Source injectableTypeFile = CompilerTests.javaSource("test/SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    Source componentFile = CompilerTests.javaSource("test/SimpleComponent",
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

    CompilerTests.daggerCompiler(injectableTypeFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerSimpleComponent"));
            });
  }

  @Test public void componentWithScope() throws Exception {
    Source injectableTypeFile = CompilerTests.javaSource("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Singleton;",
        "",
        "@Singleton",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    Source componentFile = CompilerTests.javaSource("test.SimpleComponent",
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

    CompilerTests.daggerCompiler(injectableTypeFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerSimpleComponent"));
            });
  }

  // TODO(b/241158653): Requires adding XProcessing implementation of erasure (b/231189307).
  @Test public void simpleComponentWithNesting() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(nestedTypesFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerOuterType_SimpleComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerOuterType_SimpleComponent"));
  }

  @Test public void componentWithModule() throws Exception {
    Source aFile = CompilerTests.javaSource("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A(B b) {}",
        "}");
    Source bFile = CompilerTests.javaSource("test.B",
        "package test;",
        "",
        "interface B {}");
    Source cFile = CompilerTests.javaSource("test.C",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class C {",
        "  @Inject C() {}",
        "}");

    Source moduleFile = CompilerTests.javaSource("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides B b(C c) { return null; }",
        "}");

    Source componentFile = CompilerTests.javaSource("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  A a();",
        "}");

    CompilerTests.daggerCompiler(aFile, bFile, cFile, moduleFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void componentWithAbstractModule() throws Exception {
    Source aFile = CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(B b) {}",
            "}");
    Source bFile = CompilerTests.javaSource("test.B",
            "package test;",
            "",
            "interface B {}");
    Source cFile = CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class C {",
            "  @Inject C() {}",
            "}");

    Source moduleFile = CompilerTests.javaSource(
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

    Source componentFile = CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");

    CompilerTests.daggerCompiler(aFile, bFile, cFile, moduleFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test public void transitiveModuleDeps() throws Exception {
    Source always = CompilerTests.javaSource("test.AlwaysIncluded",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class AlwaysIncluded {}");
    Source testModule = CompilerTests.javaSource("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {DepModule.class, AlwaysIncluded.class})",
        "final class TestModule extends ParentTestModule {}");
    Source parentTest = CompilerTests.javaSource("test.ParentTestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {ParentTestIncluded.class, AlwaysIncluded.class})",
        "class ParentTestModule {}");
    Source parentTestIncluded = CompilerTests.javaSource("test.ParentTestIncluded",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = AlwaysIncluded.class)",
        "final class ParentTestIncluded {}");
    Source depModule = CompilerTests.javaSource("test.DepModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {RefByDep.class, AlwaysIncluded.class})",
        "final class DepModule extends ParentDepModule {}");
    Source refByDep = CompilerTests.javaSource("test.RefByDep",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = AlwaysIncluded.class)",
        "final class RefByDep extends ParentDepModule {}");
    Source parentDep = CompilerTests.javaSource("test.ParentDepModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = {ParentDepIncluded.class, AlwaysIncluded.class})",
        "class ParentDepModule {}");
    Source parentDepIncluded = CompilerTests.javaSource("test.ParentDepIncluded",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module(includes = AlwaysIncluded.class)",
        "final class ParentDepIncluded {}");

    Source componentFile = CompilerTests.javaSource("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "}");

    CompilerTests.daggerCompiler(
                always,
                testModule,
                parentTest,
                parentTestIncluded,
                depModule,
                refByDep,
                parentDep,
                parentDepIncluded,
                componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  // TODO(b/241158653): Requires allowing extra processors with CompilerTests.daggerCompiler().
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
            compilerWithOptions(compilerMode.javacopts()).compile(rootModule, component))
        .failed();
    assertThat(
            Compilers.daggerCompiler(
                    new GeneratingProcessor(
                        "test.GeneratedModule",
                        "package test;",
                        "",
                        "import dagger.Module;",
                        "",
                        "@Module",
                        "final class GeneratedModule {}"))
                .withOptions(
                    ImmutableList.builder()
                        .addAll(Compilers.DEFAULT_JAVACOPTS)
                        .addAll(compilerMode.javacopts())
                        .build())
                .compile(rootModule, component))
        .succeeded();
  }

  // TODO(b/241158653): Requires allowing extra processors with CompilerTests.daggerCompiler().
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
            compilerWithOptions(compilerMode.javacopts()).compile(subcomponent, component))
        .failed();
    assertThat(
            Compilers.daggerCompiler(
                    new GeneratingProcessor(
                        "test.GeneratedModule",
                        "package test;",
                        "",
                        "import dagger.Module;",
                        "",
                        "@Module",
                        "final class GeneratedModule {}"))
                .withOptions(
                    ImmutableList.builder()
                        .addAll(Compilers.DEFAULT_JAVACOPTS)
                        .addAll(compilerMode.javacopts())
                        .build())
                .compile(subcomponent, component))
        .succeeded();
  }

  // TODO(b/241158653): Requires adding XProcessing implementation of isSubtype (b/231189791).
  @Test
  public void subcomponentNotGeneratedIfNotUsedInGraph() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(component, module, subcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerParent"));
  }

  @Test
  public void testDefaultPackage() {
    Source aClass = CompilerTests.javaSource("AClass", "class AClass {}");
    Source bClass = CompilerTests.javaSource("BClass",
        "import javax.inject.Inject;",
        "",
        "class BClass {",
        "  @Inject BClass(AClass a) {}",
        "}");
    Source aModule = CompilerTests.javaSource("AModule",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module class AModule {",
        "  @Provides AClass aClass() {",
        "    return new AClass();",
        "  }",
        "}");
    Source component = CompilerTests.javaSource("SomeComponent",
        "import dagger.Component;",
        "",
        "@Component(modules = AModule.class)",
        "interface SomeComponent {",
        "  BClass bClass();",
        "}");

    CompilerTests.daggerCompiler(aModule, aClass, bClass, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  // TODO(b/241158653): Requires adding XProcessing implementation of erasure (b/231189307).
  @Test public void membersInjection() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, injectedTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerSimpleComponent"));
  }

  @Test public void componentInjection() throws Exception {
    Source injectableTypeFile = CompilerTests.javaSource("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType(SimpleComponent component) {}",
        "}");
    Source componentFile = CompilerTests.javaSource("test.SimpleComponent",
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

    CompilerTests.daggerCompiler(injectableTypeFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerSimpleComponent"));
            });
  }

  @Test public void membersInjectionInsideProvision() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, injectedTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerSimpleComponent"));
  }

  @Test public void componentDependency() throws Exception {
    Source aFile = CompilerTests.javaSource("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A() {}",
        "}");
    Source bFile = CompilerTests.javaSource("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class B {",
        "  @Inject B(Provider<A> a) {}",
        "}");
    Source aComponentFile = CompilerTests.javaSource("test.AComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface AComponent {",
        "  A a();",
        "}");
    Source bComponentFile = CompilerTests.javaSource("test.BComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = AComponent.class)",
        "interface BComponent {",
        "  B b();",
        "}");

    CompilerTests.daggerCompiler(aFile, bFile, aComponentFile, bComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerBComponent"));
            });
  }

  @Test public void primitiveComponentDependency() throws Exception {
    Source bFile = CompilerTests.javaSource("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class B {",
        "  @Inject B(Provider<Integer> i) {}",
        "}");
    Source intComponentFile = CompilerTests.javaSource("test.IntComponent",
        "package test;",
        "",
        "interface IntComponent {",
        "  int i();",
        "}");
    Source bComponentFile = CompilerTests.javaSource("test.BComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = IntComponent.class)",
        "interface BComponent {",
        "  B b();",
        "}");

    CompilerTests.daggerCompiler(bFile, intComponentFile, bComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerBComponent"));
            });
  }

  @Test public void arrayComponentDependency() throws Exception {
    Source bFile = CompilerTests.javaSource("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class B {",
        "  @Inject B(Provider<String[]> i) {}",
        "}");
    Source arrayComponentFile = CompilerTests.javaSource("test.ArrayComponent",
        "package test;",
        "",
        "interface ArrayComponent {",
        "  String[] strings();",
        "}");
    Source bComponentFile = CompilerTests.javaSource("test.BComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = ArrayComponent.class)",
        "interface BComponent {",
        "  B b();",
        "}");

    CompilerTests.daggerCompiler(bFile, arrayComponentFile, bComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerBComponent"));
            });
  }

  @Test public void dependencyNameCollision() throws Exception {
    Source a1 = CompilerTests.javaSource("pkg1.A",
        "package pkg1;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class A {",
        "  @Inject A() {}",
        "}");
    Source a2 = CompilerTests.javaSource("pkg2.A",
        "package pkg2;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class A {",
        "  @Inject A() {}",
        "}");
    Source a1Component = CompilerTests.javaSource("pkg1.AComponent",
        "package pkg1;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "public interface AComponent {",
        "  A a();",
        "}");
    Source a2Component = CompilerTests.javaSource("pkg2.AComponent",
        "package pkg2;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "public interface AComponent {",
        "  A a();",
        "}");
    Source bComponent = CompilerTests.javaSource("test.BComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = {pkg1.AComponent.class, pkg2.AComponent.class})",
        "interface BComponent {",
        "  B b();",
        "}");
    Source b = CompilerTests.javaSource("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class B {",
        "  @Inject B(Provider<pkg1.A> a1, Provider<pkg2.A> a2) {}",
        "}");

    CompilerTests.daggerCompiler(a1, a2, b, a1Component, a2Component, bComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerBComponent"));
            });
  }

  @Test public void moduleNameCollision() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, otherAFile, moduleFile, otherModuleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test public void ignoresDependencyMethodsFromObject() throws Exception {
    Source injectedTypeFile =
        CompilerTests.javaSource(
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
    Source aComponentFile =
        CompilerTests.javaSource(
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
    Source bComponentFile =
        CompilerTests.javaSource(
            "test.BComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = AComponent.class)",
            "interface BComponent {",
            "  InjectedType injectedType();",
            "}");

    CompilerTests.daggerCompiler(injectedTypeFile, aComponentFile, bComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerBComponent"));
            });
  }

  @Test public void resolutionOrder() throws Exception {
    Source aFile = CompilerTests.javaSource("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A(B b) {}",
        "}");
    Source bFile = CompilerTests.javaSource("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class B {",
        "  @Inject B(C c) {}",
        "}");
    Source cFile = CompilerTests.javaSource("test.C",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class C {",
        "  @Inject C() {}",
        "}");
    Source xFile = CompilerTests.javaSource("test.X",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class X {",
        "  @Inject X(C c) {}",
        "}");

    Source componentFile = CompilerTests.javaSource("test.TestComponent",
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

    CompilerTests.daggerCompiler(aFile, bFile, cFile, xFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  // TODO(b/241158653): Requires adding XProcessing implementation of javacOverrides (b/231172867).
  @Test public void simpleComponent_redundantComponentMethod() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(
                injectableTypeFile,
                componentSupertypeAFile,
                componentSupertypeBFile,
                componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerSimpleComponent"));
  }

  @Test public void simpleComponent_inheritedComponentMethodDep() throws Exception {
    Source injectableTypeFile = CompilerTests.javaSource("test.SomeInjectableType",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class SomeInjectableType {",
        "  @Inject SomeInjectableType() {}",
        "}");
    Source componentSupertype = CompilerTests.javaSource("test.Supertype",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface Supertype {",
        "  SomeInjectableType someInjectableType();",
        "}");
    Source depComponentFile = CompilerTests.javaSource("test.SimpleComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface SimpleComponent extends Supertype {",
        "}");

    CompilerTests.daggerCompiler(injectableTypeFile, componentSupertype, depComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerSimpleComponent"));
            });
  }

  // TODO(b/241158653): Requires adding XProcessing implementation of erasure (b/231189307).
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
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.B<? extends test.A> cannot be provided without an @Provides-annotated method");
  }

  // https://github.com/google/dagger/issues/630
  @Test
  public void arrayKeyRequiresAtProvides() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  String[] array();",
            "}");

    CompilerTests.daggerCompiler(component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "String[] cannot be provided without an @Provides-annotated method");
            });
  }

  // TODO(b/241158653): Requires allowing extra processors with CompilerTests.daggerCompiler().
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
        Compilers.daggerCompiler(
                new GeneratingProcessor(
                    "test.GeneratedType",
                    "package test;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "final class GeneratedType {",
                    "  @Inject GeneratedType() {}",
                    "}"))
            .withOptions(
                ImmutableList.builder()
                    .addAll(Compilers.DEFAULT_JAVACOPTS)
                    .addAll(compilerMode.javacopts())
                    .build())
            .compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.DaggerSimpleComponent");
  }

  // TODO(b/241158653): Requires allowing extra processors with CompilerTests.daggerCompiler().
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
        Compilers.daggerCompiler(
                new GeneratingProcessor(
                    "test.GeneratedType",
                    "package test;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "final class GeneratedType {",
                    "  @Inject GeneratedType() {}",
                    "}"))
            .withOptions(
                ImmutableList.builder()
                    .addAll(Compilers.DEFAULT_JAVACOPTS)
                    .addAll(compilerMode.javacopts())
                    .build())
            .compile(componentFile, interfaceFile);
    assertThat(compilation).succeeded();
    assertThat(compilation).generatedSourceFile("test.DaggerSimpleComponent");
  }

  // TODO(b/241158653): Requires adding XProcessing implementation of erasure (b/231189307).
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
    Source aScope =
        CompilerTests.javaSource(
            "test.AScope",
            "package test;",
            "",
            "import javax.inject.Scope;",
            "",
            "@Scope",
            "@interface AScope {}");
    Source aClass =
        CompilerTests.javaSource(
            "test.AClass",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class AClass {",
            "  @Inject @AScope AClass() {}",
            "}");
    CompilerTests.daggerCompiler(aScope, aClass)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining("@Scope annotations are not allowed on @Inject constructors")
                  .onSource(aClass)
                  .onLine(6);
            });
  }

  // TODO(b/241158653): Requires adding XProcessing implementation of isSubtype (b/231189791).
  @Test
  public void unusedSubcomponents_dontResolveExtraBindingsInParentComponents() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(foo, module, component, prunedSubcomponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerParent"));
  }

  @Test
  public void bindsToDuplicateBinding_bindsKeyIsNotDuplicated() {
    Source firstModule =
        CompilerTests.javaSource(
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
    Source secondModule =
        CompilerTests.javaSource(
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
    Source bindsModule =
        CompilerTests.javaSource(
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
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = {FirstModule.class, SecondModule.class, BindsModule.class})",
            "interface TestComponent {",
            "  Object notDuplicated();",
            "}");

    CompilerTests.daggerCompiler(firstModule, secondModule, bindsModule, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("String is bound multiple times")
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  // TODO(b/241158653): Requires adding XProcessing implementation of erasure (b/231189307).
  @Test
  public void nullIncorrectlyReturnedFromNonNullableInlinedProvider() throws Exception {
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
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
        .hasSourceEquivalentTo(
            goldenFileRule.goldenFile("test.TestModule_NonNullableStringFactory"));
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  // TODO(b/241158653): Requires adding XProcessing implementation of erasure (b/231189307).
  @Test
  public void nullCheckingIgnoredWhenProviderReturnsPrimitive() throws Exception {
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
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
        .hasSourceEquivalentTo(
            goldenFileRule.goldenFile("test.TestModule_PrimitiveIntegerFactory"));
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void privateMethodUsedOnlyInChildDoesNotUseQualifiedThis() throws Exception {
    Source parent =
        CompilerTests.javaSource(
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
    Source testModule =
        CompilerTests.javaSource(
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
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child {",
            "  String string();",
            "}");

    CompilerTests.daggerCompiler(parent, testModule, child)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              // TODO(bcorso): Replace with subject.succeededWithoutWarnings()
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerParent"));
            });
  }

  @Test
  public void componentMethodInChildCallsComponentMethodInParent() throws Exception {
    Source supertype =
        CompilerTests.javaSource(
            "test.Supertype",
            "package test;",
            "",
            "interface Supertype {",
            "  String string();",
            "}");
    Source parent =
        CompilerTests.javaSource(
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
    Source testModule =
        CompilerTests.javaSource(
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
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child extends Supertype {}");

    CompilerTests.daggerCompiler(supertype, parent, testModule, child)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              // TODO(bcorso): Replace with subject.succeededWithoutWarnings()
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerParent"));
            });
  }

  // TODO(b/241158653): Requires allowing extra processors with CompilerTests.daggerCompiler().
  @Test
  public void justInTimeAtInjectConstructor_hasGeneratedQualifier() throws Exception {
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

    Compilation compilation =
        Compilers.daggerCompiler(
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
            .withOptions(
                ImmutableList.builder()
                    .addAll(Compilers.DEFAULT_JAVACOPTS)
                    .addAll(compilerMode.javacopts())
                    .build())
            .compile(injected, module, component);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  // TODO(b/241158653): Requires allowing extra processors with CompilerTests.daggerCompiler().
  @Test
  public void moduleHasGeneratedQualifier() throws Exception {
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

    Compilation compilation =
        Compilers.daggerCompiler(
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
            .withOptions(
                ImmutableList.builder()
                    .addAll(Compilers.DEFAULT_JAVACOPTS)
                    .addAll(compilerMode.javacopts())
                    .build())
            .compile(module, component);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void publicComponentType() throws Exception {
    Source publicComponent =
        CompilerTests.javaSource(
            "test.PublicComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "public interface PublicComponent {}");
    CompilerTests.daggerCompiler(publicComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerPublicComponent"));
            });
  }

  @Test
  public void componentFactoryInterfaceTest() {
    Source parentInterface =
        CompilerTests.javaSource(
            "test.ParentInterface",
            "package test;",
            "",
            "interface ParentInterface extends ChildInterface.Factory {}");

    Source childInterface =
        CompilerTests.javaSource(
            "test.ChildInterface",
            "package test;",
            "",
            "interface ChildInterface {",
            "  interface Factory {",
            "    ChildInterface child(ChildModule childModule);",
            "  }",
            "}");

    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent extends ParentInterface, Child.Factory {}");

    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child extends ChildInterface {",
            "  interface Factory extends ChildInterface.Factory {",
            "    @Override Child child(ChildModule childModule);",
            "  }",
            "}");

    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides",
            "  int provideInt() {",
            "    return 0;",
            "  }",
            "}");

    CompilerTests.daggerCompiler(parentInterface, childInterface, parent, child, childModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void providerComponentType() throws Exception {
    Source entryPoint =
        CompilerTests.javaSource(
            "test.SomeEntryPoint",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "public class SomeEntryPoint {",
            "  @Inject SomeEntryPoint(Foo foo, Provider<Foo> fooProvider) {}",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class Bar {",
            "  @Inject Bar() {}",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "public interface TestComponent {",
            "  SomeEntryPoint someEntryPoint();",
            "}");
    CompilerTests.daggerCompiler(component, foo, bar, entryPoint)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  // TODO(b/241158653): Requires allowing extra processors with CompilerTests.daggerCompiler().
  @Test
  public void injectedTypeHasGeneratedParam() {
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "public final class Foo {",
            "",
            "  @Inject",
            "  public Foo(GeneratedParam param) {}",
            "",
            "  @Inject",
            "  public void init() {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Foo foo();",
            "}");

    Compilation compilation =
        Compilers.daggerCompiler(
                new GeneratingProcessor(
                    "test.GeneratedParam",
                    "package test;",
                    "",
                    "import javax.inject.Inject;",
                    "",
                    "public final class GeneratedParam {",
                    "",
                    "  @Inject",
                    "  public GeneratedParam() {}",
                    "}"))
            .withOptions(
                ImmutableList.builder()
                    .addAll(Compilers.DEFAULT_JAVACOPTS)
                    .addAll(compilerMode.javacopts())
                    .build())
            .compile(foo, component);
    assertThat(compilation).succeededWithoutWarnings();
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
