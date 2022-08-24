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

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.CompilerMode;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SetMultibindingValidationTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public SetMultibindingValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final Source FOO =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "public interface Foo {}");

  private static final Source FOO_IMPL =
        CompilerTests.javaSource(
            "test.FooImpl",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "public final class FooImpl implements Foo {",
            "  @Inject FooImpl() {}",
            "}");

  @Test public void testMultipleSetBindingsToSameFoo() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
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
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Set<Foo> setOfFoo();",
            "}");
    CompilerTests.daggerCompiler(FOO, FOO_IMPL, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Multiple set contributions into Set<Foo> for the same contribution key: "
                      + "FooImpl");
            });
  }

  @Test public void testMultipleSetBindingsToSameFooThroughMultipleBinds() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
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
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Set<Object> setOfObject();",
            "}");
    CompilerTests.daggerCompiler(FOO, FOO_IMPL, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Multiple set contributions into Set<Object> for the same contribution key: "
                      + "FooImpl");
            });
  }

  @Test public void testMultipleSetBindingsViaElementsIntoSet() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
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
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Set<Foo> setOfFoo();",
            "}");
    CompilerTests.daggerCompiler(FOO, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Multiple set contributions into Set<Foo> for the same contribution key: "
                      + "@TestModule.Internal Set<Foo>");
            });
  }

  @Test public void testMultipleSetBindingsToSameFooSubcomponents() {
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
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
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
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
    Source parentComponent =
        CompilerTests.javaSource(
            "test.ParentComponent",
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
    Source childComponent =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {",
            "  Set<Foo> setOfFoo();",
            "}");
    CompilerTests.daggerCompiler(
            FOO, FOO_IMPL, parentModule, childModule, parentComponent, childComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Multiple set contributions into Set<Foo> for the same contribution key: "
                      + "FooImpl");
              subject.hasErrorContaining("ParentComponent → ChildComponent");
            });
  }

  @Test public void testMultipleSetBindingsToSameKeyButDifferentBindings() {
    // Use an impl with local multibindings to create different bindings. We still want this to fail
    // even though there are separate bindings because it is likely an unintentional error anyway.
    Source fooImplWithMult =
        CompilerTests.javaSource(
            "test.FooImplWithMult",
            "package test;",
            "",
            "import java.util.Set;",
            "import javax.inject.Inject;",
            "",
            "public final class FooImplWithMult implements Foo {",
            "  @Inject FooImplWithMult(Set<Long> longSet) {}",
            "}");
    // Scoping the @Binds is necessary to ensure it goes to different bindings
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
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
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
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
    Source parentComponent =
        CompilerTests.javaSource(
            "test.ParentComponent",
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
    Source childComponent =
        CompilerTests.javaSource(
            "test.ChildComponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Set;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface ChildComponent {",
            "  Set<Foo> setOfFoo();",
            "}");
    CompilerTests.daggerCompiler(
            FOO, fooImplWithMult, parentModule, childModule, parentComponent, childComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "Multiple set contributions into Set<Foo> for the same contribution key: "
                      + "FooImplWithMult");
              subject.hasErrorContaining("ParentComponent → ChildComponent");
            });
  }
}
