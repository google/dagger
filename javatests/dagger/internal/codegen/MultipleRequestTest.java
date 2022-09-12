/*
 * Copyright (C) 2014 The Dagger Authors.
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

import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MultipleRequestTest {
  @Test public void multipleRequests_constructor() {
    CompilerTests.daggerCompiler(
            CompilerTests.javaSource(
                "test.Dep",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "class Dep {",
                "  @Inject Dep() {}",
                "}"),
            CompilerTests.javaSource(
                "test.ConstructorInjectsMultiple",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "class ConstructorInjectsMultiple {",
                "  @Inject ConstructorInjectsMultiple(Dep d1, Dep d2) {}",
                "}"),
            CompilerTests.javaSource(
                "test.SimpleComponent",
                "package test;",
                "",
                "import dagger.Component;",
                "",
                "@Component",
                "interface SimpleComponent {",
                "  ConstructorInjectsMultiple get();",
                "}"))
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test public void multipleRequests_field() {
    CompilerTests.daggerCompiler(
            CompilerTests.javaSource(
                "test.Dep",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "class Dep {",
                "  @Inject Dep() {}",
                "}"),
            CompilerTests.javaSource(
                "test.FieldInjectsMultiple",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "class FieldInjectsMultiple {",
                "  @Inject Dep d1;",
                "  @Inject Dep d2;",
                "  @Inject FieldInjectsMultiple() {}",
                "}"),
            CompilerTests.javaSource(
                "test.SimpleComponent",
                "package test;",
                "",
                "import dagger.Component;",
                "",
                "@Component",
                "interface SimpleComponent {",
                "  FieldInjectsMultiple get();",
                "}"))
        .compile(subject -> subject.hasErrorCount(0));
  }

  @Test public void multipleRequests_providesMethod() {
    CompilerTests.daggerCompiler(
            CompilerTests.javaSource(
                "test.Dep",
                "package test;",
                "",
                "import javax.inject.Inject;",
                "",
                "class Dep {",
                "  @Inject Dep() {}",
                "}"),
            CompilerTests.javaSource(
                "test.SimpleModule",
                "package test;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "",
                "@Module",
                "class SimpleModule {",
                "  @Provides Object provide(Dep d1, Dep d2) {",
                "    return null;",
                "  }",
                "}"),
            CompilerTests.javaSource(
                "test.SimpleComponent",
                "package test;",
                "",
                "import dagger.Component;",
                "",
                "@Component(modules = SimpleModule.class)",
                "interface SimpleComponent {",
                "  Object get();",
                "}"))
        .compile(subject -> subject.hasErrorCount(0));
  }
}
