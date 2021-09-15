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
import static dagger.internal.codegen.TestUtils.message;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MissingBindingSuggestionsTest {
  private static JavaFileObject injectable(String className, String constructorParams) {
    return JavaFileObjects.forSourceLines("test." + className,
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class " + className +" {",
        "  @Inject " + className + "(" + constructorParams + ") {}",
        "}");
  }

  private static JavaFileObject emptyInterface(String interfaceName) {
    return JavaFileObjects.forSourceLines("test." + interfaceName,
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "interface " + interfaceName +" {}");
  }

  @Test public void suggestsBindingInSeparateComponent() {
    JavaFileObject fooComponent = JavaFileObjects.forSourceLines("test.FooComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface FooComponent {",
        "  Foo getFoo();",
        "}");
    JavaFileObject barModule = JavaFileObjects.forSourceLines("test.BarModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "final class BarModule {",
        "  @Provides Bar provideBar() {return null;}",
        "}");
    JavaFileObject barComponent = JavaFileObjects.forSourceLines("test.BarComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = {BarModule.class})",
        "interface BarComponent {",
        "  Bar getBar();",
        "}");
    JavaFileObject foo = injectable("Foo", "Bar bar");
    JavaFileObject bar = emptyInterface("Bar");

    JavaFileObject topComponent = JavaFileObjects.forSourceLines("test.TopComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TopComponent {",
        "  FooComponent getFoo();",
        "  BarComponent getBar(BarModule barModule);",
        "}");

    Compilation compilation =
        daggerCompiler().compile(fooComponent, barComponent, topComponent, foo, bar, barModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation).hadErrorContaining("A binding for Bar exists in BarComponent:");
  }

  @Test public void suggestsBindingInNestedSubcomponent() {
    JavaFileObject fooComponent = JavaFileObjects.forSourceLines("test.FooComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "interface FooComponent {",
        "  Foo getFoo();",
        "}");
    JavaFileObject barComponent = JavaFileObjects.forSourceLines("test.BarComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent()",
        "interface BarComponent {",
        "  BazComponent getBaz();",
        "}");
    JavaFileObject bazModule = JavaFileObjects.forSourceLines("test.BazModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "final class BazModule {",
        "  @Provides Baz provideBaz() {return null;}",
        "}");
    JavaFileObject bazComponent = JavaFileObjects.forSourceLines("test.BazComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent(modules = {BazModule.class})",
        "interface BazComponent {",
        "  Baz getBaz();",
        "}");
    JavaFileObject foo = injectable("Foo", "Baz baz");
    JavaFileObject baz = emptyInterface("Baz");

    JavaFileObject topComponent = JavaFileObjects.forSourceLines("test.TopComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TopComponent {",
        "  FooComponent getFoo();",
        "  BarComponent getBar();",
        "}");

    Compilation compilation =
        daggerCompiler()
            .compile(fooComponent, barComponent, bazComponent, topComponent, foo, baz, bazModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation).hadErrorContaining("A binding for Baz exists in BazComponent:");
  }

  @Test
  public void missingBindingInParentComponent() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Foo foo();",
            "  Bar bar();",
            "  Child child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules=BazModule.class)",
            "interface Child {",
            "  Foo foo();",
            "  Baz baz();",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "Foo",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "Bar",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(Baz baz) {}",
            "}");
    JavaFileObject baz = JavaFileObjects.forSourceLines("Baz", "class Baz {}");
    JavaFileObject bazModule = JavaFileObjects.forSourceLines(
        "BazModule",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@Module",
        "final class BazModule {",
        "  @Provides Baz provideBaz() {return new Baz();}",
        "}");

    Compilation compilation = daggerCompiler().compile(parent, child, foo, bar, baz, bazModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "\033[1;31m[Dagger/MissingBinding]\033[0m Baz cannot be provided without an "
                    + "@Inject constructor or an @Provides-annotated method.",
                "A binding for Baz exists in Child:",
                "    Baz is injected at",
                "        [Parent] Bar(baz)",
                "    Bar is requested at",
                "        [Parent] Parent.bar()",
                "The following other entry points also depend on it:",
                "    Parent.foo()",
                "    Child.foo() [Parent → Child]"))
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  @Test
  public void missingBindingInSiblingComponent() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Foo foo();",
            "  Bar bar();",
            "  Child1 child1();",
            "  Child2 child2();",
            "}");
    JavaFileObject child1 =
        JavaFileObjects.forSourceLines(
            "Child1",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child1 {",
            "  Foo foo();",
            "  Baz baz();",
            "}");
    JavaFileObject child2 =
        JavaFileObjects.forSourceLines(
            "Child2",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = BazModule.class)",
            "interface Child2 {",
            "  Foo foo();",
            "  Baz baz();",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "Foo",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    JavaFileObject bar =
        JavaFileObjects.forSourceLines(
            "Bar",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(Baz baz) {}",
            "}");
    JavaFileObject baz = JavaFileObjects.forSourceLines("Baz", "class Baz {}");
    JavaFileObject bazModule = JavaFileObjects.forSourceLines(
        "BazModule",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@Module",
        "final class BazModule {",
        "  @Provides Baz provideBaz() {return new Baz();}",
        "}");

    Compilation compilation =
        daggerCompiler().compile(parent, child1, child2, foo, bar, baz, bazModule);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "\033[1;31m[Dagger/MissingBinding]\033[0m Baz cannot be provided without an "
                    + "@Inject constructor or an @Provides-annotated method.",
                "A binding for Baz exists in Child2:",
                "    Baz is injected at",
                "        [Parent] Bar(baz)",
                "    Bar is requested at",
                "        [Parent] Parent.bar()",
                "The following other entry points also depend on it:",
                "    Parent.foo()",
                "    Child1.foo() [Parent → Child1]",
                "    Child2.foo() [Parent → Child2]",
                "    Child1.baz() [Parent → Child1]"))
        .inFile(parent)
        .onLineContaining("interface Parent");
  }
}
