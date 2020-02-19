/*
 * Copyright (C) 2020 The Dagger Authors.
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
public final class ComponentDependenciesTest {
  @Test
  public void dependenciesWithTwoOfSameMethodOnDifferentInterfaces_fail() {
    JavaFileObject interfaceOne = JavaFileObjects.forSourceLines("test.One",
        "package test;",
        "",
        "interface One {",
        "  String getOne();",
        "}");
    JavaFileObject interfaceTwo = JavaFileObjects.forSourceLines("test.Two",
        "package test;",
        "",
        "interface Two {",
        "  String getTwo();",
        "}");
    JavaFileObject mergedInterface = JavaFileObjects.forSourceLines("test.Merged",
        "package test;",
        "",
        "interface Merged extends One, Two {}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = Merged.class)",
        "interface TestComponent {",
        "  String getString();",
        "}");
    Compilation compilation = daggerCompiler().compile(
        interfaceOne, interfaceTwo, mergedInterface, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("DuplicateBindings");
  }

  @Test
  public void dependenciesWithTwoOfSameMethodOnDifferentInterfaces_producers_fail() {
    JavaFileObject interfaceOne = JavaFileObjects.forSourceLines("test.One",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "",
        "interface One {",
        "  ListenableFuture<String> getOne();",
        "}");
    JavaFileObject interfaceTwo = JavaFileObjects.forSourceLines("test.Two",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "",
        "interface Two {",
        "  ListenableFuture<String> getTwo();",
        "}");
    JavaFileObject mergedInterface = JavaFileObjects.forSourceLines("test.Merged",
        "package test;",
        "",
        "interface Merged extends One, Two {}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import com.google.common.util.concurrent.ListenableFuture;",
        "import dagger.producers.ProductionComponent;",
        "",
        "@ProductionComponent(dependencies = Merged.class)",
        "interface TestComponent {",
        "  ListenableFuture<String> getString();",
        "}");
    Compilation compilation = daggerCompiler().compile(
        interfaceOne, interfaceTwo, mergedInterface, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("DuplicateBindings");
  }

  @Test
  public void dependenciesWithTwoOfSameMethodButDifferentNullability_fail() {
    JavaFileObject interfaceOne = JavaFileObjects.forSourceLines("test.One",
        "package test;",
        "",
        "interface One {",
        "  String getString();",
        "}");
    JavaFileObject interfaceTwo = JavaFileObjects.forSourceLines("test.Two",
        "package test;",
        "import javax.annotation.Nullable;",
        "",
        "interface Two {",
        "  @Nullable String getString();",
        "}");
    JavaFileObject mergedInterface = JavaFileObjects.forSourceLines("test.Merged",
        "package test;",
        "",
        "interface Merged extends One, Two {}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = Merged.class)",
        "interface TestComponent {",
        "  String getString();",
        "}");
    Compilation compilation = daggerCompiler().compile(
        interfaceOne, interfaceTwo, mergedInterface, componentFile);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("DuplicateBindings");
  }

}
