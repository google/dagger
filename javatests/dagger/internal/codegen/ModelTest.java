/*
 * Copyright (C) 2018 The Dagger Authors.
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
import static com.google.testing.compile.Compiler.javac;
import static dagger.model.testing.BindingGraphSubject.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dagger.model.BindingGraph;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModelTest {

  @Test
  public void cycleTest() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(B b) {}",
            "}");
    JavaFileObject b =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class B {",
            "  @Inject B(Provider<A> a) {}",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  A a();",
            "}");

    BindingGraphCapturer capturer = new BindingGraphCapturer();
    Compilation compilation =
        javac().withProcessors(ComponentProcessor.forTesting(capturer)).compile(a, b, component);
    assertThat(compilation).succeeded();
    BindingGraph bindingGraph = capturer.bindingGraphs().get("test.TestComponent");
    assertThat(bindingGraph).bindingWithKey("test.A").dependsOnBindingWithKey("test.B");
    assertThat(bindingGraph).bindingWithKey("test.B").dependsOnBindingWithKey("test.A");
  }
}
