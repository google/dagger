/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.internal.codegen.bindinggraphvalidation;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SetMultibindingValidationTest {
  private static final JavaFileObject FOO =
      JavaFileObjects.forSourceLines(
          "test.Foo",
          "package test;",
          "",
          "public interface Foo {}");

  private static final JavaFileObject FOO_IMPL =
      JavaFileObjects.forSourceLines(
          "test.FooImpl",
          "package test;",
          "",
          "import javax.inject.Inject;",
          "",
          "public final class FooImpl implements Foo {",
          "  @Inject FooImpl() {}",
          "}");

  @Test public void testMultipleSetBindingsToSameFoo() {
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Binds;",
        "import dagger.multibindings.IntoSet;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "interface TestModule {",
        "  @Binds @IntoSet Foo bindFoo(FooImpl impl);",
        "",
        "  @Binds @IntoSet Foo bindFooAgain(FooImpl impl);",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Set;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  Set<Foo> setOfFoo();",
        "}");
    Compilation compilation = daggerCompiler().compile(FOO, FOO_IMPL, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Multiple set contributions into Set<Foo> for the same contribution key: FooImpl");
  }

  @Test public void testMultipleSetBindingsToSameFooThroughMultipleBinds() {
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Binds;",
        "import dagger.multibindings.IntoSet;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "interface TestModule {",
        "  @Binds @IntoSet Object bindObject(FooImpl impl);",
        "",
        "  @Binds @IntoSet Object bindObjectAgain(Foo impl);",
        "",
        "  @Binds Foo bindFoo(FooImpl impl);",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Set;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  Set<Object> setOfObject();",
        "}");
    Compilation compilation = daggerCompiler().compile(FOO, FOO_IMPL, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Multiple set contributions into Set<Object> for the same contribution key: FooImpl");
  }

  @Test public void testMultipleSetBindingsViaElementsIntoSet() {
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Binds;",
        "import dagger.Provides;",
        "import dagger.multibindings.ElementsIntoSet;",
        "import java.util.HashSet;",
        "import java.util.Set;",
        "import javax.inject.Inject;",
        "import javax.inject.Qualifier;",
        "",
        "@dagger.Module",
        "interface TestModule {",
        "",
        "  @Qualifier",
        "  @interface Internal {}",
        "",
        "  @Provides @Internal static Set<Foo> provideSet() { return new HashSet<>(); }",
        "",
        "  @Binds @ElementsIntoSet Set<Foo> bindSet(@Internal Set<Foo> fooSet);",
        "",
        "  @Binds @ElementsIntoSet Set<Foo> bindSetAgain(@Internal Set<Foo> fooSet);",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Set;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  Set<Foo> setOfFoo();",
        "}");
    Compilation compilation = daggerCompiler().compile(FOO, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Multiple set contributions into Set<Foo> for the same contribution key: "
            + "@TestModule.Internal Set<Foo>");
  }

  @Test public void testMultipleSetBindingsToSameFooSubcomponents() {
    JavaFileObject parentModule = JavaFileObjects.forSourceLines("test.ParentModule",
        "package test;",
        "",
        "import dagger.Binds;",
        "import dagger.multibindings.IntoSet;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "interface ParentModule {",
        "  @Binds @IntoSet Foo bindFoo(FooImpl impl);",
        "}");
    JavaFileObject childModule = JavaFileObjects.forSourceLines("test.ChildModule",
        "package test;",
        "",
        "import dagger.Binds;",
        "import dagger.multibindings.IntoSet;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "interface ChildModule {",
        "  @Binds @IntoSet Foo bindFoo(FooImpl impl);",
        "}");
    JavaFileObject parentComponent = JavaFileObjects.forSourceLines("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Set;",
        "",
        "@Component(modules = ParentModule.class)",
        "interface ParentComponent {",
        "  Set<Foo> setOfFoo();",
        "  ChildComponent child();",
        "}");
    JavaFileObject childComponent = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "import java.util.Set;",
        "",
        "@Subcomponent(modules = ChildModule.class)",
        "interface ChildComponent {",
        "  Set<Foo> setOfFoo();",
        "}");
    Compilation compilation = daggerCompiler().compile(
        FOO, FOO_IMPL, parentModule, childModule, parentComponent, childComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Multiple set contributions into Set<Foo> for the same contribution key: FooImpl");
    assertThat(compilation)
        .hadErrorContaining(
            "ParentComponent → ChildComponent");
  }

  @Test public void testMultipleSetBindingsToSameKeyButDifferentBindings() {
    // Use an impl with local multibindings to create different bindings. We still want this to fail
    // even though there are separate bindings because it is likely an unintentional error anyway.
    JavaFileObject fooImplWithMult = JavaFileObjects.forSourceLines("test.FooImplWithMult",
        "package test;",
        "",
        "import java.util.Set;",
        "import javax.inject.Inject;",
        "",
        "public final class FooImplWithMult implements Foo {",
        "  @Inject FooImplWithMult(Set<Long> longSet) {}",
        "}");
    // Scoping the @Binds is necessary to ensure it goes to different bindings
    JavaFileObject parentModule = JavaFileObjects.forSourceLines("test.ParentModule",
        "package test;",
        "",
        "import dagger.Binds;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoSet;",
        "import javax.inject.Inject;",
        "import javax.inject.Singleton;",
        "",
        "@dagger.Module",
        "interface ParentModule {",
        "  @Singleton",
        "  @Binds @IntoSet Foo bindFoo(FooImplWithMult impl);",
        "",
        "  @Provides @IntoSet static Long provideLong() {",
        "    return 0L;",
        "  }",
        "}");
    JavaFileObject childModule = JavaFileObjects.forSourceLines("test.ChildModule",
        "package test;",
        "",
        "import dagger.Binds;",
        "import dagger.Provides;",
        "import dagger.multibindings.IntoSet;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "interface ChildModule {",
        "  @Binds @IntoSet Foo bindFoo(FooImplWithMult impl);",
        "",
        "  @Provides @IntoSet static Long provideLong() {",
        "    return 1L;",
        "  }",
        "}");
    JavaFileObject parentComponent = JavaFileObjects.forSourceLines("test.ParentComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Set;",
        "import javax.inject.Singleton;",
        "",
        "@Singleton",
        "@Component(modules = ParentModule.class)",
        "interface ParentComponent {",
        "  Set<Foo> setOfFoo();",
        "  ChildComponent child();",
        "}");
    JavaFileObject childComponent = JavaFileObjects.forSourceLines("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "import java.util.Set;",
        "",
        "@Subcomponent(modules = ChildModule.class)",
        "interface ChildComponent {",
        "  Set<Foo> setOfFoo();",
        "}");
    Compilation compilation = daggerCompiler().compile(
        FOO, fooImplWithMult, parentModule, childModule, parentComponent, childComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "Multiple set contributions into Set<Foo> for the same contribution key: "
            + "FooImplWithMult");
    assertThat(compilation)
        .hadErrorContaining(
            "ParentComponent → ChildComponent");
  }
}
