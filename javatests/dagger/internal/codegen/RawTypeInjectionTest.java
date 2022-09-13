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

package dagger.internal.codegen;

import androidx.room.compiler.processing.util.Source;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RawTypeInjectionTest {
  @Test
  public void rawEntryPointTest() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Foo foo();",  // Fail: requesting raw type
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo<T> {",
            "  @Inject Foo() {}",
            "}");

    CompilerTests.daggerCompiler(component, foo)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Foo cannot be provided without an @Provides-annotated method.")
                  .onSource(component)
                  .onLine(6);
            });
  }

  @Test
  public void rawProvidesRequestTest() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  int integer();",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo<T> {",
            "  @Inject Foo() {}",
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
            "  @Provides",
            "  int provideFoo(Foo foo) {", // Fail: requesting raw type
            "    return 0;",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component, foo, module)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Foo cannot be provided without an @Provides-annotated method.")
                  .onSource(component)
                  .onLine(6);
            });
  }

  @Test
  public void rawInjectConstructorRequestTest() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Foo foo();",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo<T> {",
            "  @Inject Foo() {}",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Bar {",
            "  @Inject Bar(Foo foo) {}", // Fail: requesting raw type
            "}");

    CompilerTests.daggerCompiler(component, foo, bar)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "Foo cannot be provided without an @Provides-annotated method.")
                  .onSource(component)
                  .onLine(6);
            });
  }

  @Test
  public void rawProvidesReturnTest() {
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            // Test that we can request the raw type if it's provided by a module.
            "  Foo foo();",
            "}");
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class Foo<T> {",
            "  @Inject Foo() {}",
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
            // Test that Foo<T> can still be requested and is independent of Foo (otherwise we'd
            // get a cyclic dependency error).
            "  @Provides",
            "  Foo provideFoo(Foo<Integer> fooInteger) {",
            "    return fooInteger;",
            "  }",
            "",
            "  @Provides",
            "  int provideInt() {",
            "    return 0;",
            "  }",
            "}");

    CompilerTests.daggerCompiler(component, foo, module)
        .compile(subject -> subject.hasErrorCount(0));
  }
}
