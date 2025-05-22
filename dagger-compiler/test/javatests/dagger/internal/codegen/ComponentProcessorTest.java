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

import static com.google.common.truth.Truth.assertThat;
import static dagger.internal.codegen.xprocessing.XFunSpecs.constructorBuilder;
import static org.junit.Assert.assertThrows;

import androidx.room.compiler.codegen.XClassName;
import androidx.room.compiler.codegen.XTypeSpec;
import androidx.room.compiler.processing.util.CompilationResultSubject;
import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dagger.internal.codegen.xprocessing.XTypeNames;
import dagger.internal.codegen.xprocessing.XTypeSpecs;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.util.Collection;
import javax.tools.Diagnostic;
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

  private static final Source NULLABLE =
      CompilerTests.javaSource(
          "test.Nullable", // force one-string-per-line format
          "package test;",
          "",
          "public @interface Nullable {}");

  private static final XTypeSpec GENERATED_INJECT_TYPE =
      XTypeSpecs.classBuilder("GeneratedInjectType")
          .addFunction(
              constructorBuilder()
                  .addAnnotation(XClassName.get("javax.inject", "Inject"))
                  .build())
          .build();

  private static final XTypeSpec GENERATED_QUALIFIER =
      XTypeSpecs.annotationBuilder("GeneratedQualifier")
          .addAnnotation(XClassName.get("javax.inject", "Qualifier"))
          .build();

  private static final XTypeSpec GENERATED_MODULE =
      XTypeSpecs.classBuilder("GeneratedModule")
          .addAnnotation(XTypeNames.MODULE)
          .build();


  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public ComponentProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void doubleBindingFromResolvedModules() {
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

  @Test
  public void privateNestedClassWithWarningThatIsAnErrorInComponent() {
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
            subject ->
                // Because it is just a warning until it is used, the factory still gets generated
                // which has errors from referencing the private class, so there are extra errors.
                // Hence we don't assert on the number of errors.
                subject.hasErrorContaining(
                    "Dagger does not support injection into private classes"));
  }

  @Test
  public void simpleComponent() throws Exception {
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

  @Test
  public void componentWithScope() throws Exception {
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

  @Test
  public void simpleComponentWithNesting() throws Exception {
    Source nestedTypesFile = CompilerTests.javaSource("test.OuterType",
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

    CompilerTests.daggerCompiler(nestedTypesFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerOuterType_SimpleComponent"));
            });
  }

  @Test
  public void componentWithModule() throws Exception {
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

  @Test
  public void transitiveModuleDeps() throws Exception {
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

  @Test
  public void generatedTransitiveModule() {
    Source rootModule =
        CompilerTests.javaSource(
            "test.RootModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(includes = GeneratedModule.class)",
            "final class RootModule {}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = RootModule.class)",
            "interface TestComponent {}");

    CompilerTests.daggerCompiler(rootModule, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasError());

    CompilerTests.daggerCompiler(rootModule, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .withProcessingSteps(() -> new GeneratingProcessingStep("test", GENERATED_MODULE))
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void generatedModuleInSubcomponent() {
    Source subcomponent =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GeneratedModule.class)",
            "interface ChildComponent {}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  ChildComponent childComponent();",
            "}");
    CompilerTests.daggerCompiler(subcomponent, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasError());

    CompilerTests.daggerCompiler(subcomponent, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .withProcessingSteps(() -> new GeneratingProcessingStep("test", GENERATED_MODULE))
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void subcomponentNotGeneratedIfNotUsedInGraph() throws Exception {
    Source component =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  String notSubcomponent();",
            "}");
    Source module =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module(subcomponents = Child.class)",
            "class ParentModule {",
            "  @Provides static String notSubcomponent() { return new String(); }",
            "}");

    Source subcomponent =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(component, module, subcomponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerParent"));
            });
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

  @Test
  public void membersInjection() throws Exception {
    Source injectableTypeFile = CompilerTests.javaSource("test.SomeInjectableType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class SomeInjectableType {",
            "  @Inject SomeInjectableType() {}",
            "}");
    Source injectedTypeFile = CompilerTests.javaSource("test.SomeInjectedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class SomeInjectedType {",
            "  @Inject SomeInjectableType injectedField;",
            "  SomeInjectedType() {}",
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
            "  void inject(SomeInjectedType instance);",
            "  SomeInjectedType injectAndReturn(SomeInjectedType instance);",
            "}");

    CompilerTests.daggerCompiler(injectableTypeFile, injectedTypeFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerSimpleComponent"));
            });
  }

  @Test
  public void componentInjection() throws Exception {
    Source injectableTypeFile =
        CompilerTests.javaSource(
            "test.SomeInjectableType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class SomeInjectableType {",
            "  @Inject SomeInjectableType(SimpleComponent component) {}",
            "}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.SimpleComponent",
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

  @Test
  public void membersInjectionInsideProvision() throws Exception {
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
    Source injectedTypeFile =
        CompilerTests.javaSource(
            "test.SomeInjectedType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class SomeInjectedType {",
            "  @Inject SomeInjectableType injectedField;",
            "  @Inject SomeInjectedType() {}",
            "}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  SomeInjectedType createAndInject();",
            "}");
    CompilerTests.daggerCompiler(injectableTypeFile, injectedTypeFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
          subject -> {
            subject.hasErrorCount(0);
            subject.generatedSourceFileWithPath("test/DaggerSimpleComponent.java");
          });
  }

  @Test
  public void componentDependency() throws Exception {
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

  @Test
  public void componentWithNullableDependency() throws Exception {
    Source bFile =
        CompilerTests.javaSource(
            "test.B",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class B {",
            "  @Inject B(Provider<String> a) {}",
            "}");
    Source nullableStringComponentFile =
        CompilerTests.javaSource(
            "test.NullableStringComponent",
            "package test;",
            "",
            "interface NullableStringComponent {",
            "  @Nullable String nullableString();",
            "}");
    Source bComponentFile =
        CompilerTests.javaSource(
            "test.BComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(dependencies = NullableStringComponent.class)",
            "interface BComponent {",
            "  B b();",
            "}");

    CompilerTests.daggerCompiler(nullableStringComponentFile, bFile, bComponentFile, NULLABLE)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerBComponent"));
            });
  }

  @Test
  public void primitiveComponentDependency() throws Exception {
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

  @Test
  public void arrayComponentDependency() throws Exception {
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

  @Test
  public void dependencyNameCollision() throws Exception {
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

  @Test
  public void moduleNameCollision() throws Exception {
    Source aFile =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "public final class A {}");
    Source otherAFile =
        CompilerTests.javaSource(
            "other.test.A",
            "package other.test;",
            "",
            "public final class A {}");
    Source moduleFile =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "public final class TestModule {",
            "  @Provides A a() { return null; }",
            "}");
    Source otherModuleFile =
        CompilerTests.javaSource(
            "other.test.TestModule",
            "package other.test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "public final class TestModule {",
            "  @Provides A a() { return null; }",
            "}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
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
    CompilerTests.daggerCompiler(aFile, otherAFile, moduleFile, otherModuleFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void ignoresDependencyMethodsFromObject() throws Exception {
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

  @Test
  public void resolutionOrder() throws Exception {
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

  @Test
  public void simpleComponent_redundantComponentMethod() throws Exception {
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
    Source componentSupertypeAFile =
        CompilerTests.javaSource(
            "test.SupertypeA",
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
    Source componentSupertypeBFile =
        CompilerTests.javaSource(
            "test.SupertypeB",
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
    Source componentFile =
        CompilerTests.javaSource(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Lazy;",
            "import javax.inject.Provider;",
            "",
            "@Component",
            "interface SimpleComponent extends SupertypeA, SupertypeB {",
            "}");
    CompilerTests.daggerCompiler(
            injectableTypeFile, componentSupertypeAFile, componentSupertypeBFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerSimpleComponent"));
            });
  }

  @Test
  public void simpleComponent_inheritedComponentMethodDep() throws Exception {
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

  @Test
  public void wildcardGenericsRequiresAtProvides() {
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
            "final class B<T> {",
            "  @Inject B(T t) {}",
            "}");
    Source cFile = CompilerTests.javaSource("test.C",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class C {",
            "  @Inject C(B<? extends A> bA) {}",
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
            "  C c();",
            "}");

    CompilerTests.daggerCompiler(aFile, bFile, cFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "test.B<? extends test.A> cannot be provided without an @Provides-annotated "
                      + "method");
            });
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

  @Test
  public void componentImplicitlyDependsOnGeneratedType() {
    Source injectableTypeFile =
        CompilerTests.javaSource(
            "test.SomeInjectableType",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class SomeInjectableType {",
            "  @Inject SomeInjectableType(GeneratedInjectType generatedInjectType) {}",
            "}");
    Source componentFile =
        CompilerTests.javaSource(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SimpleComponent {",
            "  SomeInjectableType someInjectableType();",
            "}");
    CompilerTests.daggerCompiler(injectableTypeFile, componentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .withProcessingSteps(() -> new GeneratingProcessingStep("test", GENERATED_INJECT_TYPE))
        .compile(
          subject -> {
            subject.hasErrorCount(0);
            subject.generatedSourceFileWithPath("test/DaggerSimpleComponent.java");
          });
  }

  @Test
  public void componentSupertypeDependsOnGeneratedType() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.SimpleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SimpleComponent extends SimpleComponentInterface {}");
    Source interfaceFile =
        CompilerTests.javaSource(
            "test.SimpleComponentInterface",
            "package test;",
            "",
            "interface SimpleComponentInterface {",
            "  GeneratedInjectType generatedInjectType();",
            "}");
    CompilerTests.daggerCompiler(componentFile, interfaceFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .withProcessingSteps(() -> new GeneratingProcessingStep("test", GENERATED_INJECT_TYPE))
        .compile(
          subject -> {
            subject.hasErrorCount(0);
            subject.generatedSourceFileWithPath("test/DaggerSimpleComponent.java");
          });
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
    Source testClasses =
        CompilerTests.javaSource(
            "dagger.internal.codegen.ComponentProcessorTestClasses",
            "package dagger.internal.codegen;",
            "",
            "import javax.inject.Inject;",
            "",
            "public final class ComponentProcessorTestClasses {",
            "  public static final class NoInjectMemberNoConstructor {}",
            "",
            "  public static final class NoInjectMemberWithConstructor {",
            "     @Inject",
            "     NoInjectMemberWithConstructor() {}",
            "  }",
            "",
            "  public abstract static class LocalInjectMemberNoConstructor {",
            "    @Inject Object object;",
            "  }",
            "",
            "  public static final class LocalInjectMemberWithConstructor {",
            "    @SuppressWarnings(\"BadInject\") // Ignore this check as we want to test this case"
                + " in particular.",
            "    @Inject Object object;",
            "",
            "    @Inject",
            "    LocalInjectMemberWithConstructor() {}",
            "  }",
            "",
            "  public static final class ParentInjectMemberNoConstructor extends"
                + " LocalInjectMemberNoConstructor {",
            "  }",
            "",
            "  public static final class ParentInjectMemberWithConstructor",
            "      extends LocalInjectMemberNoConstructor {",
            "    @Inject",
            "    ParentInjectMemberWithConstructor() {}",
            "  }",
            "",
            "  private ComponentProcessorTestClasses() {}",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.ComponentProcessorTestClasses;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  void inject(ComponentProcessorTestClasses.NoInjectMemberNoConstructor object);",
            "  void inject(ComponentProcessorTestClasses.NoInjectMemberWithConstructor object);",
            "  void inject(ComponentProcessorTestClasses.LocalInjectMemberNoConstructor object);",
            "  void inject(ComponentProcessorTestClasses.LocalInjectMemberWithConstructor object);",
            "  void inject(ComponentProcessorTestClasses.ParentInjectMemberNoConstructor object);",
            "  void inject(",
            "      ComponentProcessorTestClasses.ParentInjectMemberWithConstructor object);",
            "}");
    Source module =
        CompilerTests.javaSource(
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
            "}");
    CompilerTests.daggerCompiler(module, component)
        .withAdditionalClasspath(CompilerTests.libraryCompiler(testClasses).compile())
        .withProcessingOptions(
            ImmutableMap.<String, String>builder()
                .putAll(compilerMode.processorOptions())
                .put("dagger.warnIfInjectionFactoryNotGeneratedUpstream", "enabled")
                .buildOrThrow())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);

              String generatedFileTemplate =
                  "dagger/internal/codegen/ComponentProcessorTestClasses_%s_MembersInjector.java";
              String noteTemplate =
                  "Generating a MembersInjector for "
                      + "dagger.internal.codegen.ComponentProcessorTestClasses.%s.";

              // Assert that we generate sources and notes for the following classes.
              ImmutableList.of(
                      "LocalInjectMemberNoConstructor",
                      "LocalInjectMemberWithConstructor",
                      "ParentInjectMemberWithConstructor")
                  .forEach(
                      className -> {
                        subject.generatedSourceFileWithPath(
                            String.format(generatedFileTemplate, className));
                        subject.hasNoteContaining(String.format(noteTemplate, className));
                      });

              // Assert that we **do not** generate sources and notes for the following classes.
              ImmutableList.of(
                      "ParentInjectMemberNoConstructor",
                      "NoInjectMemberNoConstructor",
                      "NoInjectMemberWithConstructor")
                  .forEach(
                      className -> {
                        assertFileNotGenerated(
                            subject, String.format(generatedFileTemplate, className));
                        assertDoesNotHaveNoteContaining(
                            subject, String.format(noteTemplate, className));
                      });
            });
  }

  private void assertFileNotGenerated(CompilationResultSubject subject, String filePath) {
    // TODO(b/303653163): replace this with better XProcessing API once we have the ability to get a
    // list of all generated sources.
    AssertionError error =
        assertThrows(
            AssertionError.class,
            () -> subject.generatedSourceFileWithPath(filePath));
    assertThat(error).hasMessageThat().contains("Didn't generate file");
  }

  private void assertDoesNotHaveNoteContaining(CompilationResultSubject subject, String content) {
    assertThat(
            subject.getCompilationResult().getDiagnostics().get(Diagnostic.Kind.NOTE).stream()
                .filter(diagnostic -> diagnostic.getMsg().contains(content)))
        .isEmpty();
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

  @Test
  public void unusedSubcomponents_dontResolveExtraBindingsInParentComponents() throws Exception {
    Source foo =
        CompilerTests.javaSource(
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

    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module(subcomponents = Pruned.class)",
            "class TestModule {}");

    Source component =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = TestModule.class)",
            "interface Parent {}");

    Source prunedSubcomponent =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo, module, component, prunedSubcomponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerParent"));
            });
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

  @Test
  public void nullIncorrectlyReturnedFromNonNullableInlinedProvider() throws Exception {
     CompilerTests.daggerCompiler(
             CompilerTests.javaSource(
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
            CompilerTests.javaSource(
                "test.InjectsMember",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "public class InjectsMember {",
                "  @Inject String member;",
                "}"),
            CompilerTests.javaSource(
                "test.TestComponent",
                "package test;",
                "",
                "import dagger.Component;",
                "",
                "@Component(modules = TestModule.class)",
                "interface TestComponent {",
                "  String nonNullableString();",
                "  void inject(InjectsMember member);",
                "}"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              // TODO(bcorso): Replace with subject.succeededWithoutWarnings()
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/TestModule_NonNullableStringFactory"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  @Test
  public void nullCheckingIgnoredWhenProviderReturnsPrimitive() throws Exception {
    CompilerTests.daggerCompiler(
            CompilerTests.javaSource(
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
            CompilerTests.javaSource(
                "test.InjectsMember",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "public class InjectsMember {",
                "  @Inject Integer member;",
                "}"),
            CompilerTests.javaSource(
                "test.TestComponent",
                "package test;",
                "",
                "import dagger.Component;",
                "",
                "@Component(modules = TestModule.class)",
                "interface TestComponent {",
                "  Integer nonNullableInteger();",
                "  void inject(InjectsMember member);",
                "}"))
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              // TODO(bcorso): Replace with subject.succeededWithoutWarnings()
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/TestModule_PrimitiveIntegerFactory"));
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
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

  @org.junit.Ignore // TODO(b/394093156): This is a known issue with JDK17.
  @Test
  public void justInTimeAtInjectConstructor_hasGeneratedQualifier() throws Exception {
    Source injected =
        CompilerTests.javaSource(
            "test.Injected",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Injected {",
            "  @Inject Injected(@GeneratedQualifier String string) {}",
            "}");
    Source module =
        CompilerTests.javaSource(
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
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Injected injected();",
            "}");
    CompilerTests.daggerCompiler(injected, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .withProcessingSteps(() -> new GeneratingProcessingStep("test", GENERATED_QUALIFIER))
        .compile(
          subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
          });
  }

  @org.junit.Ignore // TODO(b/394093156): This is a known issue with JDK17.
  @Test
  public void moduleHasGeneratedQualifier() throws Exception {
    Source module =
        CompilerTests.javaSource(
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
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  String unqualified();",
            "}");

    CompilerTests.daggerCompiler(module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .withProcessingSteps(() -> new GeneratingProcessingStep("test", GENERATED_QUALIFIER))
        .compile(
          subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
          });
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

  @Test
  public void injectedTypeHasGeneratedParam() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "public final class Foo {",
            "",
            "  @Inject",
            "  public Foo(GeneratedInjectType param) {}",
            "",
            "  @Inject",
            "  public void init() {}",
            "}");
    Source component =
        CompilerTests.javaSource(
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

    CompilerTests.daggerCompiler(foo, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .withProcessingSteps(() -> new GeneratingProcessingStep("test", GENERATED_INJECT_TYPE))
        .compile(
          subject -> {
              subject.hasErrorCount(0);
              subject.hasWarningCount(0);
          });
  }

  @Test
  public void abstractVarFieldInComponent_failsValidation() {
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [TestModule::class])",
            "interface TestComponent {",
            " var foo: String",
            "}");

    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides",
            "  static String provideString() { return \"hello\"; }",
            "}");

    CompilerTests.daggerCompiler(component, module)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Cannot use 'abstract var' property in a component declaration to get a binding."
                      + " Use 'val' or 'fun' instead: foo");
            });
  }

  @Test
  public void nonAbstractVarFieldInComponent_passesValidation() {
    Source component =
        CompilerTests.kotlinSource(
            "test.TestComponent.kt",
            "package test",
            "",
            "import dagger.Component",
            "",
            "@Component(modules = [TestModule::class])",
            "abstract class TestComponent {",
            " var foo: String = \"hello\"",
            "}");

    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides",
            "  static String provideString() { return \"hello\"; }",
            "}");

    CompilerTests.daggerCompiler(component, module)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }
}
