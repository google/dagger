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
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
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

  @Test public void factoryMethod_missingModulesWithParameters() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = ModuleWithParameters.class)",
        "interface ChildComponent {",
        "  Object object();",
        "}");
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.ModuleWithParameters",
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
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(componentFile, childComponentFile, moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.ChildComponent requires modules which have no visible default constructors. "
                + "Add the following modules as parameters to this method: "
                + "test.ModuleWithParameters")
        .inFile(componentFile)
        .onLineContaining("ChildComponent newChildComponent();");
  }

  @Test
  public void factoryMethod_grandchild() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  ChildComponent newChildComponent();",
            "}");
    JavaFileObject childComponent =
        JavaFileObjects.forSourceLines(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface ChildComponent {",
            "  GrandchildComponent newGrandchildComponent();",
            "}");
    JavaFileObject grandchildComponent =
        JavaFileObjects.forSourceLines(
            "test.GrandchildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = GrandchildModule.class)",
            "interface GrandchildComponent {",
            "  Object object();",
            "}");
    JavaFileObject grandchildModule =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(component, childComponent, grandchildComponent, grandchildModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "[test.ChildComponent.newGrandchildComponent()] "
                + "test.GrandchildComponent requires modules which have no visible default "
                + "constructors. Add the following modules as parameters to this method: "
                + "test.GrandchildModule")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test public void factoryMethod_nonModuleParameter() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent(String someRandomString);",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Subcomponent factory methods may only accept modules, but java.lang.String is not.")
        .inFile(componentFile)
        .onLine(7)
        .atColumn(43);
  }

  @Test public void factoryMethod_duplicateParameter() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class TestModule {}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent(TestModule testModule1, TestModule testModule2);",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = TestModule.class)",
        "interface ChildComponent {}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(moduleFile, componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "A module may only occur once an an argument in a Subcomponent factory method, "
                + "but test.TestModule was already passed.")
        .inFile(componentFile)
        .onLine(7)
        .atColumn(71);
  }

  @Test public void factoryMethod_superflouousModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "",
        "@Module",
        "final class TestModule {}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent(TestModule testModule);",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface ChildComponent {}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(moduleFile, componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.TestModule is present as an argument to the test.ChildComponent factory method, "
                + "but is not one of the modules used to implement the subcomponent.")
        .inFile(componentFile)
        .onLine(7);
  }

  @Test public void missingBinding() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
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
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  ChildComponent newChildComponent();",
        "}");
    JavaFileObject childComponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = TestModule.class)",
        "interface ChildComponent {",
        "  String getString();",
        "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(moduleFile, componentFile, childComponentFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "java.lang.Integer cannot be provided without an @Inject constructor or an "
                + "@Provides-annotated method")
        .inFile(componentFile)
        .onLineContaining("interface TestComponent");
  }

  @Test public void subcomponentOnConcreteType() {
    JavaFileObject subcomponentFile = JavaFileObjects.forSourceLines("test.NotASubcomponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "final class NotASubcomponent {}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(subcomponentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("interface");
  }

  @Test public void scopeMismatch() {
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.ParentComponent",
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
    JavaFileObject subcomponentFile = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = ChildModule.class)",
        "interface ChildComponent {",
        "  Object getObject();",
        "}");
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.ChildModule",
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
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(componentFile, subcomponentFile, moduleFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("@Singleton");
  }

  @Test
  public void delegateFactoryNotCreatedForSubcomponentWhenProviderExistsInParent() {
    JavaFileObject parentComponentFile =
        JavaFileObjects.forSourceLines(
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
            "  Dep1 getDep1();",
            "  Dep2 getDep2();",
            "}");
    JavaFileObject childComponentFile =
        JavaFileObjects.forSourceLines(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {",
            "  Object getObject();",
            "}");
    JavaFileObject childModuleFile =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject aFile =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject public A(NeedsDep1 a, Dep1 b, Dep2 c) { }",
            "  @Inject public void methodA() { }",
            "}");
    JavaFileObject needsDep1File =
        JavaFileObjects.forSourceLines(
            "test.NeedsDep1",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class NeedsDep1 {",
            "  @Inject public NeedsDep1(Dep1 d) { }",
            "}");
    JavaFileObject dep1File =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject dep2File =
        JavaFileObjects.forSourceLines(
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

    JavaFileObject generatedComponent =
        compilerMode
            .javaFileBuilder("test.DaggerParentComponent")
            .addLines(
                "package test;",
                "",
                GENERATED_ANNOTATION,
                "final class DaggerParentComponent implements ParentComponent {")
            .addLinesIn(
                DEFAULT_MODE,
                "  @SuppressWarnings(\"unchecked\")",
                "  private void initialize() {",
                "    this.dep1Provider = DoubleCheck.provider(Dep1_Factory.create());",
                "    this.dep2Provider = DoubleCheck.provider(Dep2_Factory.create());",
                "  }",
                "")
            .addLines(
                "  @Override", //
                "  public Dep1 getDep1() {")
            .addLinesIn(
                FAST_INIT_MODE,
                "   Object local = dep1;",
                "    if (local instanceof MemoizedSentinel) {",
                "      synchronized (local) {",
                "        local = dep1;",
                "        if (local instanceof MemoizedSentinel) {",
                "          local = injectDep1(Dep1_Factory.newInstance());",
                "          dep1 = DoubleCheck.reentrantCheck(dep1, local);",
                "        }",
                "      }",
                "    }",
                "    return (Dep1) local;")
            .addLinesIn(
                DEFAULT_MODE, //
                "    return dep1Provider.get();")
            .addLines(
                "  }", //
                "",
                "  @Override",
                "  public Dep2 getDep2() {")
            .addLinesIn(
                FAST_INIT_MODE,
                "   Object local = dep2;",
                "    if (local instanceof MemoizedSentinel) {",
                "      synchronized (local) {",
                "        local = dep2;",
                "        if (local instanceof MemoizedSentinel) {",
                "          local = injectDep2(Dep2_Factory.newInstance());",
                "          dep2 = DoubleCheck.reentrantCheck(dep2, local);",
                "        }",
                "      }",
                "    }",
                "    return (Dep2) local;")
            .addLinesIn(
                DEFAULT_MODE, //
                "    return dep2Provider.get();")
            .addLines(
                "  }",
                "",
                "  @Override",
                "  public ChildComponent childComponent() {",
                "    return new ChildComponentImpl();",
                "  }",
                "")
            .addLinesIn(
                FAST_INIT_MODE,
                "  @CanIgnoreReturnValue",
                "  private Dep1 injectDep1(Dep1 instance) {",
                "    Dep1_MembersInjector.injectDep1Method(instance);",
                "    return instance;",
                "  }",
                "",
                "  @CanIgnoreReturnValue",
                "  private Dep2 injectDep2(Dep2 instance) {",
                "    Dep2_MembersInjector.injectDep2Method(instance);",
                "    return instance;",
                "  }")
            .addLines(
                "",
                "  private final class ChildComponentImpl implements ChildComponent {",
                "    private final ChildModule childModule;",
                "",
                "    private ChildComponentImpl() {",
                "      this.childModule = new ChildModule();",
                "    }",
                "")
            .addLinesIn(
                DEFAULT_MODE,
                "    private NeedsDep1 getNeedsDep1() {",
                "      return new NeedsDep1(DaggerParentComponent.this.dep1Provider.get());",
                "    }")
            .addLinesIn(
                FAST_INIT_MODE,
                "    private NeedsDep1 getNeedsDep1() {",
                "      return new NeedsDep1(DaggerParentComponent.this.getDep1());",
                "    }")
            .addLines(
                "    private A getA() {",
                "      return injectA(",
                "          A_Factory.newInstance(",
                "              getNeedsDep1(),")
            .addLinesIn(
                DEFAULT_MODE,
                "              DaggerParentComponent.this.dep1Provider.get(),",
                "              DaggerParentComponent.this.dep2Provider.get()));")
            .addLinesIn(
                FAST_INIT_MODE,
                "              DaggerParentComponent.this.getDep1(),",
                "              DaggerParentComponent.this.getDep2()));")
            .addLines(
                "    }",
                "",
                "    @Override",
                "    public Object getObject() {",
                "      return ChildModule_ProvideObjectFactory.provideObject(",
                "          childModule, getA());",
                "    }",
                "",
                "    @CanIgnoreReturnValue",
                "    private A injectA(A instance) {",
                "      A_MembersInjector.injectMethodA(instance);",
                "      return instance;",
                "    }",
                "  }",
                "}")
            .build();

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(
                parentComponentFile,
                childComponentFile,
                childModuleFile,
                aFile,
                needsDep1File,
                dep1File,
                dep2File);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParentComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test
  public void multipleSubcomponentsWithSameSimpleNamesCanExistInSameComponent() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject baz =
        JavaFileObjects.forSourceLines(
            "test.subpackage.Sub",
            "package test.subpackage;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent public interface Sub {}");
    JavaFileObject noConflict =
        JavaFileObjects.forSourceLines(
            "test.NoConflict",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent interface NoConflict {}");

    JavaFileObject componentGeneratedFile =
        JavaFileObjects.forSourceLines(
            "test.DaggerParentComponent",
            "package test;",
            "",
            "import test.subpackage.Sub;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerParentComponent implements ParentComponent {",
            "  @Override",
            "  public Foo.Sub newInstanceSubcomponent() {",
            "    return new F_SubImpl();",
            "  }",
            "",
            "  @Override",
            "  public NoConflict newNoConflictSubcomponent() {",
            "    return new NoConflictImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    public ParentComponent build() {",
            "      return new DaggerParentComponent();",
            "    }",
            "  }",
            "",
            "  private final class F_SubImpl implements Foo.Sub {",
            "    @Override",
            "    public Bar.Sub newBarSubcomponent() {",
            "      return new B_SubImpl();",
            "    }",
            "",
            "    private final class B_SubImpl implements Bar.Sub {",
            "      @Override",
            "      public Sub newSubcomponentInSubpackage() {",
            "        return new ts_SubImpl();",
            "      }",
            "",
            "      private final class ts_SubImpl implements Sub {}",
            "    }",
            "  }",
            "",
            "  private final class NoConflictImpl implements NoConflict {}",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(parent, foo, bar, baz, noConflict);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParentComponent")
        .containsElementsIn(componentGeneratedFile);
  }

  @Test
  public void subcomponentSimpleNamesDisambiguated() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.ParentComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  Sub newSubcomponent();",
            "}");
    JavaFileObject sub =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent interface Sub {",
            "  test.deep.many.levels.that.match.test.Sub newDeepSubcomponent();",
            "}");
    JavaFileObject deepSub =
        JavaFileObjects.forSourceLines(
            "test.deep.many.levels.that.match.test.Sub",
            "package test.deep.many.levels.that.match.test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent public interface Sub {}");

    JavaFileObject componentGeneratedFile =
        JavaFileObjects.forSourceLines(
            "test.DaggerParentComponent",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerParentComponent implements ParentComponent {",
            "  @Override",
            "  public Sub newSubcomponent() {",
            "    return new t_SubImpl();",
            "  }",
            "",
            "  static final class Builder {",
            "    public ParentComponent build() {",
            "      return new DaggerParentComponent();",
            "    }",
            "  }",
            "",
            "  private final class t_SubImpl implements Sub {",
            "    @Override",
            "    public test.deep.many.levels.that.match.test.Sub newDeepSubcomponent() {",
            "      return new tdmltmt_SubImpl();",
            "    }",
            "",
            "    private final class tdmltmt_SubImpl implements ",
            "        test.deep.many.levels.that.match.test.Sub {",
            "      private tdmltmt_SubImpl() {}",
            "    }",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(parent, sub, deepSub);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParentComponent")
        .containsElementsIn(componentGeneratedFile);
  }

  @Test
  public void subcomponentSimpleNamesDisambiguatedInRoot() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "ParentComponent",
            "import dagger.Component;",
            "",
            "@Component",
            "interface ParentComponent {",
            "  Sub newSubcomponent();",
            "}");
    JavaFileObject sub =
        JavaFileObjects.forSourceLines(
            "Sub",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent interface Sub {",
            "  test.deep.many.levels.that.match.test.Sub newDeepSubcomponent();",
            "}");
    JavaFileObject deepSub =
        JavaFileObjects.forSourceLines(
            "test.deep.many.levels.that.match.test.Sub",
            "package test.deep.many.levels.that.match.test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent public interface Sub {}");

    JavaFileObject componentGeneratedFile =
        JavaFileObjects.forSourceLines(
            "DaggerParentComponent",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerParentComponent implements ParentComponent {",
            "  @Override",
            "  public Sub newSubcomponent() {",
            "    return new $_SubImpl();",
            "  }",
            "",
            "  private final class $_SubImpl implements Sub {",
            "    private $_SubImpl() {}",
            "",
            "    @Override",
            "    public test.deep.many.levels.that.match.test.Sub newDeepSubcomponent() {",
            "      return new tdmltmt_SubImpl();",
            "    }",
            "",
            "    private final class tdmltmt_SubImpl implements ",
            "        test.deep.many.levels.that.match.test.Sub {",
            "      private tdmltmt_SubImpl() {}",
            "    }",
            "  }",
            "}",
            "");

    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(parent, sub, deepSub);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("DaggerParentComponent")
        .containsElementsIn(componentGeneratedFile);
  }

  @Test
  public void subcomponentImplNameUsesFullyQualifiedClassNameIfNecessary() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject top1 =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject top2 =
        JavaFileObjects.forSourceLines(
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

    JavaFileObject componentGeneratedFile =
        JavaFileObjects.forSourceLines(
            "test.DaggerParentComponent",
            "package test;",
            "",
            "import top1.a.b.c.d.E;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerParentComponent implements ParentComponent {",
            "  @Override",
            "  public E.F.Sub top1() {",
            "    return new F_SubImpl();",
            "  }",
            "",
            "  @Override",
            "  public top2.a.b.c.d.E.F.Sub top2() {",
            "    return new F2_SubImpl();",
            "  }",
            "",
            "  private final class F_SubImpl implements E.F.Sub {",
            "    private F_SubImpl() {}",
            "  }",
            "  private final class F2_SubImpl implements top2.a.b.c.d.E.F.Sub {",
            "    private F2_SubImpl() {}",
            "  }",
            "}");

    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(parent, top1, top2);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerParentComponent")
        .containsElementsIn(componentGeneratedFile);
  }

  @Test
  public void parentComponentNameShouldNotBeDisambiguatedWhenItConflictsWithASubcomponent() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface C {",
            "  test.Foo.C newInstanceC();",
            "}");
    JavaFileObject subcomponentWithSameSimpleNameAsParent =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "interface Foo {",
            "  @Subcomponent interface C {}",
            "}");

    JavaFileObject componentGeneratedFile =
        JavaFileObjects.forSourceLines(
            "test.DaggerC",
            "package test;",
            "",
            GENERATED_ANNOTATION,
            "final class DaggerC implements C {",
            "  @Override",
            "  public Foo.C newInstanceC() {",
            "    return new F_CImpl();",
            "  }",
            "",
            "  private final class F_CImpl implements Foo.C {}",
            "}");

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(parent, subcomponentWithSameSimpleNameAsParent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerC")
        .containsElementsIn(componentGeneratedFile);
  }

  @Test
  public void subcomponentBuilderNamesShouldNotConflict() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject componentGeneratedFile =
        JavaFileObjects.forSourceLines(
            "test.DaggerC",
            "package test;",
            "",
            IMPORT_GENERATED_ANNOTATION,
            "",
            GENERATED_ANNOTATION,
            "final class DaggerC implements C {",
            "  @Override",
            "  public C.Foo.Sub.Builder fooBuilder() {",
            "    return new F_SubBuilder();",
            "  }",
            "",
            "  @Override",
            "  public C.Bar.Sub.Builder barBuilder() {",
            "    return new B_SubBuilder();",
            "  }",
            "",
            // TODO(user): Reverse the order of subcomponent and builder so that subcomponent
            // comes first.
            "  private final class F_SubBuilder implements C.Foo.Sub.Builder {",
            "    @Override",
            "    public C.Foo.Sub build() {",
            "      return new F_SubImpl();",
            "    }",
            "  }",
            "",
            "  private final class F_SubImpl implements C.Foo.Sub {",
            "    private F_SubImpl() {}",
            "  }",
            "",
            "  private final class B_SubBuilder implements C.Bar.Sub.Builder {",
            "    @Override",
            "    public C.Bar.Sub build() {",
            "      return new B_SubImpl();",
            "    }",
            "  }",
            "",
            "  private final class B_SubImpl implements C.Bar.Sub {",
            "    private B_SubImpl() {}",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().withOptions(compilerMode.javacopts()).compile(parent);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerC")
        .containsElementsIn(componentGeneratedFile);
  }

  @Test
  public void duplicateBindingWithSubcomponentDeclaration() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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

    JavaFileObject subcomponent =
        JavaFileObjects.forSourceLines(
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

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Sub",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface C {",
            "  Object dependsOnBuilder();",
            "}");

    Compilation compilation =
        daggerCompiler()
            .withOptions(compilerMode.javacopts())
            .compile(module, component, subcomponent);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("test.Sub.Builder is bound multiple times:");
    assertThat(compilation)
        .hadErrorContaining(
            "@Provides test.Sub.Builder "
                + "test.TestModule.providesConflictsWithModuleSubcomponents()");
    assertThat(compilation)
        .hadErrorContaining("@Module(subcomponents = test.Sub.class) for test.TestModule");
  }

  @Test
  public void subcomponentDependsOnGeneratedType() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child.Builder childBuilder();",
            "}");

    JavaFileObject child =
        JavaFileObjects.forSourceLines(
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

    JavaFileObject childSupertype =
        JavaFileObjects.forSourceLines(
            "test.ChildSupertype",
            "package test;",
            "",
            "interface ChildSupertype {",
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
            .compile(parent, child, childSupertype);
    assertThat(compilation).succeededWithoutWarnings();
  }
}
