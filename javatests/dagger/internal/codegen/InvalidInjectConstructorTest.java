/*
 * Copyright (C) 2022 The Dagger Authors.
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

// Tests an invalid inject constructor that avoids validation in its own library by using
// a dependency on jsr330 rather than Dagger gets validated when used in a component.
@RunWith(JUnit4.class)
public final class InvalidInjectConstructorTest {

  @Test
  public void usedInvalidConstructorFails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.InvalidInjectConstructor;",
            "",
            "@Component",
            "interface TestComponent {",
            "  InvalidInjectConstructor invalidInjectConstructor();",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(2);
    assertThat(compilation)
        .hadErrorContaining(
            "Type dagger.internal.codegen.InvalidInjectConstructor may only contain one injected "
                + "constructor. Found: ["
                + "InvalidInjectConstructor(), "
                + "InvalidInjectConstructor(java.lang.String)"
                + "]");
    // TODO(b/215620949): Avoid reporting missing bindings on a type that has errors.
    assertThat(compilation)
        .hadErrorContaining(
            "InvalidInjectConstructor cannot be provided without an @Inject constructor or an "
                + "@Provides-annotated method.");
  }

  @Test
  public void unusedInvalidConstructorFails() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.internal.codegen.InvalidInjectConstructor;",
            "",
            "@Component",
            "interface TestComponent {",
            // Here we're only using the members injection, but we're testing that we still validate
            // the constructors
            "  void inject(InvalidInjectConstructor instance);",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(2);
    assertThat(compilation)
        .hadErrorContaining(
            "Type dagger.internal.codegen.InvalidInjectConstructor may only contain one injected "
                + "constructor. Found: ["
                + "InvalidInjectConstructor(), "
                + "InvalidInjectConstructor(java.lang.String)"
                + "]");
    // TODO(b/215620949): Avoid reporting missing bindings on a type that has errors.
    assertThat(compilation)
        .hadErrorContaining(
            "InvalidInjectConstructor cannot be provided without an @Inject constructor or an "
                + "@Provides-annotated method.");
  }
}
