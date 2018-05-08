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
    assertThat(compilation)
        .hadErrorContaining("A binding with matching key exists in component: test.BarComponent");
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
    assertThat(compilation)
        .hadErrorContaining("A binding with matching key exists in component: test.BazComponent");
  }
}
