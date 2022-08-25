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
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MissingBindingSuggestionsTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public MissingBindingSuggestionsTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static Source injectable(String className, String constructorParams) {
    return CompilerTests.javaSource(
        "test." + className,
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class " + className + " {",
        "  @Inject " + className + "(" + constructorParams + ") {}",
        "}");
  }

  private static Source emptyInterface(String interfaceName) {
    return CompilerTests.javaSource(
        "test." + interfaceName,
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "interface " + interfaceName + " {}");
  }

  @Test public void suggestsBindingInSeparateComponent() {
    Source fooComponent =
        CompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface FooComponent {",
            "  Foo getFoo();",
            "}");
    Source barModule =
        CompilerTests.javaSource(
            "test.BarModule",
            "package test;",
            "",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "final class BarModule {",
            "  @Provides Bar provideBar() {return null;}",
            "}");
    Source barComponent =
        CompilerTests.javaSource(
            "test.BarComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = {BarModule.class})",
            "interface BarComponent {",
            "  Bar getBar();",
            "}");
    Source foo = injectable("Foo", "Bar bar");
    Source bar = emptyInterface("Bar");

    Source topComponent =
        CompilerTests.javaSource(
            "test.TopComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TopComponent {",
            "  FooComponent getFoo();",
            "  BarComponent getBar(BarModule barModule);",
            "}");

    CompilerTests.daggerCompiler(fooComponent, barComponent, topComponent, foo, bar, barModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("A binding for Bar exists in BarComponent:");
            });
  }

  @Test public void suggestsBindingInNestedSubcomponent() {
    Source fooComponent =
        CompilerTests.javaSource(
            "test.FooComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface FooComponent {",
            "  Foo getFoo();",
            "}");
    Source barComponent =
        CompilerTests.javaSource(
            "test.BarComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent()",
            "interface BarComponent {",
            "  BazComponent getBaz();",
            "}");
    Source bazModule =
        CompilerTests.javaSource(
            "test.BazModule",
            "package test;",
            "",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "final class BazModule {",
            "  @Provides Baz provideBaz() {return null;}",
            "}");
    Source bazComponent =
        CompilerTests.javaSource(
            "test.BazComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = {BazModule.class})",
            "interface BazComponent {",
            "  Baz getBaz();",
            "}");
    Source foo = injectable("Foo", "Baz baz");
    Source baz = emptyInterface("Baz");

    Source topComponent =
        CompilerTests.javaSource(
            "test.TopComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TopComponent {",
            "  FooComponent getFoo();",
            "  BarComponent getBar();",
            "}");

    CompilerTests.daggerCompiler(
            fooComponent, barComponent, bazComponent, topComponent, foo, baz, bazModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("A binding for Baz exists in BazComponent:");
            });
  }

  @Test
  public void missingBindingInParentComponent() {
    Source parent =
        CompilerTests.javaSource(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Foo foo();",
            "  Bar bar();",
            "  Child child();",
            "}");
    Source child =
        CompilerTests.javaSource(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules=BazModule.class)",
            "interface Child {",
            "  Foo foo();",
            "  Baz baz();",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "Foo",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "Bar",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(Baz baz) {}",
            "}");
    Source baz = CompilerTests.javaSource("Baz", "class Baz {}");
    Source bazModule =
        CompilerTests.javaSource(
        "BazModule",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@Module",
        "final class BazModule {",
        "  @Provides Baz provideBaz() {return new Baz();}",
        "}");

    CompilerTests.daggerCompiler(parent, child, foo, bar, baz, bazModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "\033[1;31m[Dagger/MissingBinding]\033[0m Baz cannot be provided without an "
                    + "@Inject constructor or an @Provides-annotated method.");
              subject.hasErrorContaining("A binding for Baz exists in Child:");
              subject.hasErrorContaining("    Baz is injected at");
              subject.hasErrorContaining("        [Parent] Bar(baz)");
              subject.hasErrorContaining("    Bar is requested at");
              subject.hasErrorContaining("        [Parent] Parent.bar()");
              subject.hasErrorContaining("The following other entry points also depend on it:");
              subject.hasErrorContaining("    Parent.foo()");
              subject.hasErrorContaining("    Child.foo() [Parent → Child]")
                  .onSource(parent)
                  .onLineContaining("interface Parent");
            });
  }

  @Test
  public void missingBindingInSiblingComponent() {
    Source parent =
        CompilerTests.javaSource(
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
    Source child1 =
        CompilerTests.javaSource(
            "Child1",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Child1 {",
            "  Foo foo();",
            "  Baz baz();",
            "}");
    Source child2 =
        CompilerTests.javaSource(
            "Child2",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = BazModule.class)",
            "interface Child2 {",
            "  Foo foo();",
            "  Baz baz();",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "Foo",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "Bar",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(Baz baz) {}",
            "}");
    Source baz = CompilerTests.javaSource("Baz", "class Baz {}");
    Source bazModule =
        CompilerTests.javaSource(
        "BazModule",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@Module",
        "final class BazModule {",
        "  @Provides Baz provideBaz() {return new Baz();}",
        "}");

    CompilerTests.daggerCompiler(parent, child1, child2, foo, bar, baz, bazModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "\033[1;31m[Dagger/MissingBinding]\033[0m Baz cannot be provided without an "
                      + "@Inject constructor or an @Provides-annotated method.");
              subject.hasErrorContaining("A binding for Baz exists in Child2:");
              subject.hasErrorContaining("    Baz is injected at");
              subject.hasErrorContaining("        [Parent] Bar(baz)");
              subject.hasErrorContaining("    Bar is requested at");
              subject.hasErrorContaining("        [Parent] Parent.bar()");
              subject.hasErrorContaining("The following other entry points also depend on it:");
              subject.hasErrorContaining("    Parent.foo()");
              subject.hasErrorContaining("    Child1.foo() [Parent → Child1]");
              subject.hasErrorContaining("    Child2.foo() [Parent → Child2]");
              subject.hasErrorContaining("    Child1.baz() [Parent → Child1]")
                  .onSource(parent)
                  .onLineContaining("interface Parent");
            });
  }
}
