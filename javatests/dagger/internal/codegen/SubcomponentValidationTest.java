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

import androidx.room.compiler.processing.util.Source;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SubcomponentValidationTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public SubcomponentValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  @Test public void factoryMethod_missingModulesWithParameters() {
    Source componentFile =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  ChildComponent newChildComponent();",
            "}");
    Source childComponentFile =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ModuleWithParameters.class)",
            "interface ChildComponent {",
            "  Object object();",
            "}");
    Source moduleFile =
        CompilerTests.javaSource(
            "test.ModuleWithParameters",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class ModuleWithParameters {",
            "  private final Object object;",
            "",
            "  ModuleWithParameters(Object object) {",
            "    this.object = object;",
            "  }",
            "",
            "  @Provides Object object() {",
            "    return object;",
            "  }",
            "}");
    CompilerTests.daggerCompiler(componentFile, childComponentFile, moduleFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "test.ChildComponent requires modules which have no visible default "
                          + "constructors. Add the following modules as parameters to this method: "
                          + "test.ModuleWithParameters")
                  .onSource(componentFile)
                  .onLineContaining("ChildComponent newChildComponent();");
            });
  }

  @Test
  public void factoryMethod_grandchild() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  ChildComponent newChildComponent();",
            "}");
    Source childComponent =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface ChildComponent {",
            "  GrandchildComponent newGrandchildComponent();",
            "}");
    Source grandchildComponent =
        CompilerTests.javaSource(
            "test.GrandchildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandchildModule.class)",
            "interface GrandchildComponent {",
            "  Object object();",
            "}");
    Source grandchildModule =
        CompilerTests.javaSource(
            "test.GrandchildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class GrandchildModule {",
            "  private final Object object;",
            "",
            "  GrandchildModule(Object object) {",
            "    this.object = object;",
            "  }",
            "",
            "  @Provides Object object() {",
            "    return object;",
            "  }",
            "}");
    CompilerTests.daggerCompiler(component, childComponent, grandchildComponent, grandchildModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "[ChildComponent.newGrandchildComponent()] "
                          + "GrandchildComponent requires modules which have no visible default "
                          + "constructors. Add the following modules as parameters to this method: "
                          + "GrandchildModule")
                  .onSource(component)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test public void factoryMethod_nonModuleParameter() {
    Source componentFile =
        CompilerTests.javaSource("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent(String someRandomString);",
        "}");
    Source childComponentFile =
        CompilerTests.javaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {}");
    CompilerTests.daggerCompiler(componentFile, childComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Subcomponent factory methods may only accept modules, but java.lang.String "
                          + "is not.")
                  .onSource(componentFile)
                  .onLine(7);
            });
  }

  @Test public void factoryMethod_duplicateParameter() {
    Source moduleFile =
        CompilerTests.javaSource("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class TestModule {}");
    Source componentFile =
        CompilerTests.javaSource("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent(TestModule testModule1, TestModule testModule2);",
        "}");
    Source childComponentFile =
        CompilerTests.javaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = TestModule.class)",
        "interface ChildComponent {}");
    CompilerTests.daggerCompiler(componentFile, childComponentFile, moduleFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "A module may only occur once as an argument in a Subcomponent factory "
                          + "method, but test.TestModule was already passed.")
                  .onSource(componentFile)
                  .onLine(7);
            });
  }

  @Test public void factoryMethod_superflouousModule() {
    Source moduleFile =
        CompilerTests.javaSource("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class TestModule {}");
    Source componentFile =
        CompilerTests.javaSource("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent(TestModule testModule);",
        "}");
    Source childComponentFile =
        CompilerTests.javaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {}");
    CompilerTests.daggerCompiler(moduleFile, componentFile, childComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "test.TestModule is present as an argument to the test.ChildComponent "
                          + "factory method, but is not one of the modules used to implement the "
                          + "subcomponent.")
                  .onSource(componentFile)
                  .onLine(7);
            });
  }

  @Test public void missingBinding() {
    Source moduleFile =
        CompilerTests.javaSource("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides String provideString(int i) {",
        "    return Integer.toString(i);",
        "  }",
        "}");
    Source componentFile =
        CompilerTests.javaSource("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent();",
        "}");
    Source childComponentFile =
        CompilerTests.javaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = TestModule.class)",
        "interface ChildComponent {",
        "  String string();",
        "}");
    CompilerTests.daggerCompiler(moduleFile, componentFile, childComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Integer cannot be provided without an @Inject constructor or an "
                          + "@Provides-annotated method")
                  .onSource(componentFile)
                  .onLineContaining("interface TestComponent");
            });
  }

  @Test public void subcomponentOnConcreteType() {
    Source subcomponentFile =
        CompilerTests.javaSource("test.NotASubcomponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "final class NotASubcomponent {}");
    CompilerTests.daggerCompiler(subcomponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("interface");
            });
  }

  @Test public void scopeMismatch() {
    Source componentFile =
        CompilerTests.javaSource("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Singleton;",
        "",
        "@Component",
        "@Singleton",
        "interface ParentComponent {",
        "  ChildComponent childComponent();",
        "}");
    Source subcomponentFile =
        CompilerTests.javaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = ChildModule.class)",
        "interface ChildComponent {",
        "  Object object();",
        "}");
    Source moduleFile =
        CompilerTests.javaSource("test.ChildModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Singleton;",
        "",
        "@Module",
        "final class ChildModule {",
        "  @Provides @Singleton Object provideObject() { return null; }",
        "}");
    CompilerTests.daggerCompiler(componentFile, subcomponentFile, moduleFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("@Singleton");
            });
  }

  @Test
  public void delegateFactoryNotCreatedForSubcomponentWhenProviderExistsInParent()
      throws Exception {
    Source parentComponentFile =
        CompilerTests.javaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component",
            "interface ParentComponent {",
            "  ChildComponent childComponent();",
            "  Dep1 dep1();",
            "  Dep2 dep2();",
            "}");
    Source childComponentFile =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {",
            "  Object object();",
            "}");
    Source childModuleFile =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "final class ChildModule {",
            "  @Provides Object provideObject(A a) { return null; }",
            "}");
    Source aFile =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject public A(NeedsDep1 a, Dep1 b, Dep2 c) { }",
            "  @Inject public void methodA() { }",
            "}");
    Source needsDep1File =
        CompilerTests.javaSource(
            "test.NeedsDep1",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class NeedsDep1 {",
            "  @Inject public NeedsDep1(Dep1 d) { }",
            "}");
    Source dep1File =
        CompilerTests.javaSource(
            "test.Dep1",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "final class Dep1 {",
            "  @Inject public Dep1() { }",
            "  @Inject public void dep1Method() { }",
            "}");
    Source dep2File =
        CompilerTests.javaSource(
            "test.Dep2",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "final class Dep2 {",
            "  @Inject public Dep2() { }",
            "  @Inject public void dep2Method() { }",
            "}");

    CompilerTests.daggerCompiler(
            parentComponentFile,
            childComponentFile,
            childModuleFile,
            aFile,
            needsDep1File,
            dep1File,
            dep2File)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerParentComponent"));
            });
  }

  @Test
  public void multipleSubcomponentsWithSameSimpleNamesCanExistInSameComponent() throws Exception {
    Source parent =
        CompilerTests.javaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  Foo.Sub newInstanceSubcomponent();",
            "  NoConflict newNoConflictSubcomponent();",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "interface Foo {",
            "  @Subcomponent interface Sub {",
            "    Bar.Sub newBarSubcomponent();",
            "  }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "interface Bar {",
            "  @Subcomponent interface Sub {",
            "    test.subpackage.Sub newSubcomponentInSubpackage();",
            "  }",
            "}");
    Source baz =
        CompilerTests.javaSource(
            "test.subpackage.Sub",
            "package test.subpackage;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent public interface Sub {}");
    Source noConflict =
        CompilerTests.javaSource(
            "test.NoConflict",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent interface NoConflict {}");

    CompilerTests.daggerCompiler(parent, foo, bar, baz, noConflict)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerParentComponent"));
            });
  }

  @Test
  public void subcomponentSimpleNamesDisambiguated() throws Exception {
    Source parent =
        CompilerTests.javaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  Sub newSubcomponent();",
            "}");
    Source sub =
        CompilerTests.javaSource(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent interface Sub {",
            "  test.deep.many.levels.that.match.test.Sub newDeepSubcomponent();",
            "}");
    Source deepSub =
        CompilerTests.javaSource(
            "test.deep.many.levels.that.match.test.Sub",
            "package test.deep.many.levels.that.match.test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent public interface Sub {}");

    CompilerTests.daggerCompiler(parent, sub, deepSub)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerParentComponent"));
            });
  }

  @Test
  public void subcomponentSimpleNamesDisambiguatedInRoot() throws Exception {
    Source parent =
        CompilerTests.javaSource(
            "ParentComponent",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  Sub newSubcomponent();",
            "}");
    Source sub =
        CompilerTests.javaSource(
            "Sub",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent interface Sub {",
            "  test.deep.many.levels.that.match.test.Sub newDeepSubcomponent();",
            "}");
    Source deepSub =
        CompilerTests.javaSource(
            "test.deep.many.levels.that.match.test.Sub",
            "package test.deep.many.levels.that.match.test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent public interface Sub {}");

    CompilerTests.daggerCompiler(parent, sub, deepSub)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("DaggerParentComponent"));
            });
  }

  @Test
  public void subcomponentImplNameUsesFullyQualifiedClassNameIfNecessary() throws Exception {
    Source parent =
        CompilerTests.javaSource(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  top1.a.b.c.d.E.F.Sub top1();",
            "  top2.a.b.c.d.E.F.Sub top2();",
            "}");
    Source top1 =
        CompilerTests.javaSource(
            "top1.a.b.c.d.E",
            "package top1.a.b.c.d;",
            "",
            "import dagger.Subcomponent;",
            "",
            "public interface E {",
            "  interface F {",
            "    @Subcomponent interface Sub {}",
            "  }",
            "}");
    Source top2 =
        CompilerTests.javaSource(
            "top2.a.b.c.d.E",
            "package top2.a.b.c.d;",
            "",
            "import dagger.Subcomponent;",
            "",
            "public interface E {",
            "  interface F {",
            "    @Subcomponent interface Sub {}",
            "  }",
            "}");

    CompilerTests.daggerCompiler(parent, top1, top2)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerParentComponent"));
            });
  }

  @Test
  public void subcomponentNamesShouldNotConflictWithParent()
      throws Exception {
    Source parent =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface C {",
            "  test.Foo.C newInstanceC();",
            "}");
    Source subcomponentWithSameSimpleNameAsParent =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "interface Foo {",
            "  @Subcomponent interface C {}",
            "}");

    CompilerTests.daggerCompiler(parent, subcomponentWithSameSimpleNameAsParent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerC"));
            });
  }

  @Test
  public void subcomponentBuilderNamesShouldNotConflict() throws Exception {
    Source parent =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Subcomponent;",
            "",
            "@Component",
            "interface C {",
            "  Foo.Sub.Builder fooBuilder();",
            "  Bar.Sub.Builder barBuilder();",
            "",
            "  interface Foo {",
            "    @Subcomponent",
            "    interface Sub {",
            "      @Subcomponent.Builder",
            "      interface Builder {",
            "        Sub build();",
            "      }",
            "    }",
            "  }",
            "",
            "  interface Bar {",
            "    @Subcomponent",
            "    interface Sub {",
            "      @Subcomponent.Builder",
            "      interface Builder {",
            "        Sub build();",
            "      }",
            "    }",
            "  }",
            "}");

    CompilerTests.daggerCompiler(parent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(
                  goldenFileRule.goldenSource("test/DaggerC"));
            });
  }

  @Test
  public void duplicateBindingWithSubcomponentDeclaration() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module(subcomponents = Sub.class)",
            "class TestModule {",
            "  @Provides Sub.Builder providesConflictsWithModuleSubcomponents() { return null; }",
            "  @Provides Object usesSubcomponentBuilder(Sub.Builder builder) {",
            "    return new Builder().toString();",
            "  }",
            "}");

    Source subcomponent =
        CompilerTests.javaSource(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Sub {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Sub build();",
            "  }",
            "}");

    Source component =
        CompilerTests.javaSource(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface C {",
            "  Object dependsOnBuilder();",
            "}");

    CompilerTests.daggerCompiler(module, component, subcomponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Sub.Builder is bound multiple times:");
              subject.hasErrorContaining(
                  "@Provides Sub.Builder TestModule.providesConflictsWithModuleSubcomponents()");
              subject.hasErrorContaining("@Module(subcomponents = Sub.class) for TestModule");
            });
  }

  @Test
  public void subcomponentDependsOnGeneratedType() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child.Builder childBuilder();",
            "}");
    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child extends ChildSupertype {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
            "}");
    Source childSupertype =
        CompilerTests.javaSource(
            "test.ChildSupertype",
            "package test;",
            "",
            "interface ChildSupertype {",
            "  GeneratedInjectType generatedType();",
            "}");
    TypeSpec generatedInjectType =
        TypeSpec.classBuilder("GeneratedInjectType")
            .addMethod(
                MethodSpec.constructorBuilder()
                    .addAnnotation(TypeNames.INJECT_JAVAX)
                    .build())
            .build();
    CompilerTests.daggerCompiler(parent, child, childSupertype)
        .withProcessingOptions(compilerMode.processorOptions())
        .withProcessingSteps(() -> new GeneratingProcessingStep("test", generatedInjectType))
        .compile(
          subject -> {
            subject.hasErrorCount(0);
            subject.hasWarningCount(0);
          });
  }
}
