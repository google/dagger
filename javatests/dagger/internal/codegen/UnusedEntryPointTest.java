/*
 * Copyright (C) 2024 The Dagger Authors.
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

import androidx.room3.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class UnusedEntryPointTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public UnusedEntryPointTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void entryPointUsed_hasMissingBinding_failsValidation() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.pruning.OnlyUsedBy;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface MyComponent {",
            "  @OnlyUsedBy(FooIdentifier.class)",
            "  void injectBar(Bar bar);",
            "  Foo getFoo();",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.pruning.BindingIdentifier;",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @BindingIdentifier(FooIdentifier.class) @Inject Foo() {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar { @Inject String str; }");
    Source fooIdentifier =
        CompilerTests.javaSource(
            "test.FooIdentifier", "package test;", "", "interface FooIdentifier {}");
    CompilerTests.daggerCompiler(component, foo, bar, fooIdentifier)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "String cannot be provided without an @Inject constructor or an"
                      + " @Provides-annotated method.");
            });
  }

  @Test
  public void unusedEntryPoint_missingBindingRequestedElseWhere_failsValidation() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.pruning.OnlyUsedBy;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface MyComponent {",
            "  @OnlyUsedBy(FooIdentifier.class)",
            "  void injectBar(Bar bar);",
            "  Foo getFoo();",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import dagger.pruning.BindingIdentifier;",
            "import javax.inject.Inject;",
            "",
            "class Foo {",
            "  @Inject Foo(Bar bar) {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "   @Inject Bar(String str) {}",
            "}");
    Source fooIdentifier =
        CompilerTests.javaSource(
            "test.FooIdentifier", "package test;", "", "interface FooIdentifier {}");
    CompilerTests.daggerCompiler(component, foo, bar, fooIdentifier)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "String cannot be provided without an @Inject constructor or an"
                      + " @Provides-annotated method.");
            });
  }

  @Test
  public void unusedEntryPoint_hasMissingBinding_succeeds() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.pruning.OnlyUsedBy;",
            "",
            "@Component",
            "interface MyComponent {",
            "  @OnlyUsedBy(UnusedCaller.class)",
            "  MissingBinding getMissingBinding();",
            "}");
    Source unusedCaller =
        CompilerTests.javaSource(
            "test.UnusedCaller",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class UnusedCaller {",
            "  @Inject UnusedCaller() {}",
            "}");
    Source missingBinding =
        CompilerTests.javaSource(
            "test.MissingBinding", "package test;", "", "interface MissingBinding {}");
    CompilerTests.daggerCompiler(component, unusedCaller, missingBinding)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void entryPointUsedByBindsBindingIdentifier_hasMissingBinding_failsValidation() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.pruning.OnlyUsedBy;",
            "",
            "@Component(modules = FooModule.class)",
            "interface MyComponent {",
            "  @OnlyUsedBy(FooIdentifier.class)",
            "  MissingBinding getMissingBinding();",
            "  Foo getFoo();",
            "}");
    Source foo = CompilerTests.javaSource("test.Foo", "package test;", "", "interface Foo {}");
    Source fooImpl =
        CompilerTests.javaSource(
            "test.FooImpl",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class FooImpl implements Foo {",
            "  @Inject FooImpl() {}",
            "}");
    Source fooModule =
        CompilerTests.javaSource(
            "test.FooModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.pruning.BindingIdentifier;",
            "",
            "@Module",
            "interface FooModule {",
            "  @Binds",
            "  @BindingIdentifier(FooIdentifier.class)",
            "  Foo bindFoo(FooImpl impl);",
            "}");
    Source fooIdentifier =
        CompilerTests.javaSource(
            "test.FooIdentifier", "package test;", "", "interface FooIdentifier {}");
    Source missingBinding =
        CompilerTests.javaSource(
            "test.MissingBinding", "package test;", "", "interface MissingBinding {}");
    CompilerTests.daggerCompiler(component, foo, fooImpl, fooModule, fooIdentifier, missingBinding)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "MissingBinding cannot be provided without an @Provides-annotated method.");
            });
  }
}
