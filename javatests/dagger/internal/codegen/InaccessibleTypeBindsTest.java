/*
 * Copyright (C) 2023 The Dagger Authors.
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
import dagger.testing.golden.GoldenFileRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InaccessibleTypeBindsTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public InaccessibleTypeBindsTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  // Interface is accessible, but the impl is not. Use with a scoped binds to make sure type issues
  // are handled from doing an assignment to the Provider<Foo> from DoubleCheck.provider(fooImpl).
  @Test
  public void scopedInaccessibleTypeBound() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "public interface Foo {",
            "}");
    Source fooImpl =
        CompilerTests.javaSource(
            "other.FooImpl",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "import test.Foo;",
            "",
            "final class FooImpl implements Foo {",
            "  @Inject FooImpl() {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "other.TestModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Binds;",
            "import javax.inject.Singleton;",
            "import test.Foo;",
            "",
            "@Module",
            "public interface TestModule {",
            "  @Binds",
            "  @Singleton",
            "  Foo bind(FooImpl impl);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "import javax.inject.Singleton;",
            "import other.TestModule;",
            "",
            "@Singleton",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Foo getFoo();",
            "}");

    CompilerTests.daggerCompiler(foo, fooImpl, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  // Interface is accessible, but the impl is not. Used with a binds in a loop to see if there are
  // type issues from doing an assignment to the delegate factory e.g.
  // DelegateFactory.setDelegate(provider, new SwitchingProvider<FooImpl>(...));
  @Test
  public void inaccessibleTypeBoundInALoop() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "public interface Foo {",
            "}");
    Source fooImpl =
        CompilerTests.javaSource(
            "other.FooImpl",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "import test.Foo;",
            "",
            "final class FooImpl implements Foo {",
            "  @Inject FooImpl(Provider<Foo> fooProvider) {}",
            "}");
    // Use another entry point to make FooImpl be the first requested class, that way FooImpl's
    // provider is the one that is delegated.
    Source otherEntryPoint =
        CompilerTests.javaSource(
            "other.OtherEntryPoint",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public final class OtherEntryPoint {",
            "  @Inject OtherEntryPoint(FooImpl impl) {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "other.TestModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Binds;",
            "import test.Foo;",
            "",
            "@Module",
            "public interface TestModule {",
            "  @Binds",
            "  Foo bind(FooImpl impl);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import other.OtherEntryPoint;",
            "import other.TestModule;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  OtherEntryPoint getOtherEntryPoint();",
            "}");

    CompilerTests.daggerCompiler(foo, fooImpl, otherEntryPoint, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }

  // Same as above but with the binding scoped.
  @Test
  public void inaccessibleTypeBoundInALoopScoped() throws Exception {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "public interface Foo {",
            "}");
    Source fooImpl =
        CompilerTests.javaSource(
            "other.FooImpl",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "import test.Foo;",
            "",
            "final class FooImpl implements Foo {",
            "  @Inject FooImpl(Provider<Foo> fooProvider) {}",
            "}");
    // Use another entry point to make FooImpl be the first requested class, that way FooImpl's
    // provider is the one that is delegated.
    Source otherEntryPoint =
        CompilerTests.javaSource(
            "other.OtherEntryPoint",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public final class OtherEntryPoint {",
            "  @Inject OtherEntryPoint(FooImpl impl) {}",
            "}");
    Source module =
        CompilerTests.javaSource(
            "other.TestModule",
            "package other;",
            "",
            "import dagger.Module;",
            "import dagger.Binds;",
            "import javax.inject.Singleton;",
            "import test.Foo;",
            "",
            "@Module",
            "public interface TestModule {",
            "  @Binds",
            "  @Singleton",
            "  Foo bind(FooImpl impl);",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "import other.OtherEntryPoint;",
            "import other.TestModule;",
            "",
            "@Singleton",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  OtherEntryPoint getOtherEntryPoint();",
            "}");

    CompilerTests.daggerCompiler(foo, fooImpl, otherEntryPoint, module, component)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerTestComponent"));
            });
  }
}
