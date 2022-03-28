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
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.auto.common.MoreElements;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.MembersInjector;
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
        compilerWithOptions(compilerMode.javacopts())
            .compile(parent, child, another, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("List<Integer> is bound multiple times");
    assertThat(compilation)
        .hadErrorContaining("@Provides List<Integer> ChildNumberModule.provideListB(Integer)");
    assertThat(compilation)
        .hadErrorContaining("@Provides List<Integer> AnotherModule.provideListOfInteger()");
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
        compilerWithOptions(
                compilerMode.javacopts().append("-Adagger.privateMemberValidation=WARNING"))
            .compile(outerClass, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Dagger does not support injection into private classes");
  }

  @Test public void simpleComponent() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerSimpleComponent"));
  }

  @Test public void componentWithScope() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerSimpleComponent"));
  }

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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, moduleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void componentWithAbstractModule() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, moduleFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test public void transitiveModuleDeps() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
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
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
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
            compilerWithOptions(compilerMode.javacopts()).compile(rootModule, component))
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
            compilerWithOptions(compilerMode.javacopts()).compile(subcomponent, component))
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
            compilerWithOptions(compilerMode.javacopts())
                .compile(aModule, aClass, bClass, component))
        .succeeded();
  }

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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerSimpleComponent"));
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
    JavaFileObject bComponentFile = JavaFileObjects.forSourceLines("test.BComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = AComponent.class)",
        "interface BComponent {",
        "  B b();",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, bFile, aComponentFile, bComponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerBComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerBComponent"));
  }

  @Test public void primitiveComponentDependency() throws Exception {
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class B {",
        "  @Inject B(Provider<Integer> i) {}",
        "}");
    JavaFileObject intComponentFile = JavaFileObjects.forSourceLines("test.IntComponent",
        "package test;",
        "",
        "interface IntComponent {",
        "  int i();",
        "}");
    JavaFileObject bComponentFile = JavaFileObjects.forSourceLines("test.BComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = IntComponent.class)",
        "interface BComponent {",
        "  B b();",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(bFile, intComponentFile, bComponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerBComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerBComponent"));
  }


  @Test public void arrayComponentDependency() throws Exception {
    JavaFileObject bFile = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class B {",
        "  @Inject B(Provider<String[]> i) {}",
        "}");
    JavaFileObject arrayComponentFile = JavaFileObjects.forSourceLines("test.ArrayComponent",
        "package test;",
        "",
        "interface ArrayComponent {",
        "  String[] strings();",
        "}");
    JavaFileObject bComponentFile = JavaFileObjects.forSourceLines("test.BComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = ArrayComponent.class)",
        "interface BComponent {",
        "  B b();",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(bFile, arrayComponentFile, bComponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerBComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerBComponent"));
  }

  @Test public void dependencyNameCollision() throws Exception {
    JavaFileObject a1 = JavaFileObjects.forSourceLines("pkg1.A",
        "package pkg1;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class A {",
        "  @Inject A() {}",
        "}");
    JavaFileObject a2 = JavaFileObjects.forSourceLines("pkg2.A",
        "package pkg2;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class A {",
        "  @Inject A() {}",
        "}");
    JavaFileObject a1Component = JavaFileObjects.forSourceLines("pkg1.AComponent",
        "package pkg1;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "public interface AComponent {",
        "  A a();",
        "}");
    JavaFileObject a2Component = JavaFileObjects.forSourceLines("pkg2.AComponent",
        "package pkg2;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "public interface AComponent {",
        "  A a();",
        "}");
    JavaFileObject bComponent = JavaFileObjects.forSourceLines("test.BComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = {pkg1.AComponent.class, pkg2.AComponent.class})",
        "interface BComponent {",
        "  B b();",
        "}");
    JavaFileObject b = JavaFileObjects.forSourceLines("test.B",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class B {",
        "  @Inject B(Provider<pkg1.A> a1, Provider<pkg2.A> a2) {}",
        "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(a1, a2, b, a1Component, a2Component, bComponent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerBComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerBComponent"));
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
            "test.BComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = AComponent.class)",
            "interface BComponent {",
            "  InjectedType injectedType();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectedTypeFile, aComponentFile, bComponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerBComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerBComponent"));
  }

  @Test public void resolutionOrder() throws Exception {
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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(aFile, bFile, cFile, xFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

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

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(injectableTypeFile, componentSupertype, depComponentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerSimpleComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerSimpleComponent"));
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
        compilerWithOptions(compilerMode.javacopts()).compile(component);
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
        compilerWithOptions(compilerMode.javacopts()).compile(aScope, aClass);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@Scope annotations are not allowed on @Inject constructors")
        .inFile(aClass)
        .onLine(6);
  }

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
        .hadErrorContaining("String is bound multiple times")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

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

    Compilation compilation = daggerCompiler().compile(parent, testModule, child);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerParent"));
  }

  @Test
  public void componentMethodInChildCallsComponentMethodInParent() throws Exception {
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

    Compilation compilation = daggerCompiler().compile(supertype, parent, testModule, child);
    assertThat(compilation).succeededWithoutWarnings();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerParent"));
  }

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
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

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
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

  @Test
  public void publicComponentType() throws Exception {
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
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerPublicComponent"));
  }

  @Test
  public void componentFactoryInterfaceTest() {
    JavaFileObject parentInterface =
        JavaFileObjects.forSourceLines(
            "test.ParentInterface",
            "package test;",
            "",
            "interface ParentInterface extends ChildInterface.Factory {}");

    JavaFileObject childInterface =
        JavaFileObjects.forSourceLines(
            "test.ChildInterface",
            "package test;",
            "",
            "interface ChildInterface {",
            "  interface Factory {",
            "    ChildInterface child(ChildModule childModule);",
            "  }",
            "}");

    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent extends ParentInterface, Child.Factory {}");

    JavaFileObject child =
        JavaFileObjects.forSourceLines(
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

    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
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

    Compilation compilation =
        daggerCompiler().compile(parentInterface, childInterface, parent, child, childModule);
    assertThat(compilation).succeeded();
  }

  @Test
  public void providerComponentType() throws Exception {
    JavaFileObject entryPoint =
        JavaFileObjects.forSourceLines(
            "test.SomeEntryPoint",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "public class SomeEntryPoint {",
            "  @Inject SomeEntryPoint(Foo foo, Provider<Foo> fooProvider) {}",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class Bar {",
            "  @Inject Bar() {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts()).compile(component, foo, bar, entryPoint);

    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .hasSourceEquivalentTo(goldenFileRule.goldenFile("test.DaggerTestComponent"));
  }

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
        daggerCompiler(
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
