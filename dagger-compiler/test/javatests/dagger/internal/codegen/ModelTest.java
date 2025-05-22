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

import static dagger.spi.model.testing.BindingGraphSubject.assertThat;

import androidx.room.compiler.processing.util.Source;
import dagger.spi.model.BindingGraph;
import dagger.spi.model.BindingGraphPlugin;
import dagger.spi.model.DiagnosticReporter;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ModelTest {

  @Test
  public void cycleTest() {
    Source a =
        CompilerTests.javaSource(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(B b) {}",
            "}");
    Source b =
        CompilerTests.javaSource(
            "test.B",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class B {",
            "  @Inject B(Provider<A> a) {}",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  A a();",
            "}");

    CompilerTests.daggerCompiler(a, b, component)
        .withBindingGraphPlugins(
            () -> new BindingGraphPlugin() {
              @Override
              public void visitGraph(BindingGraph graph, DiagnosticReporter reporter) {
                assertThat(graph).bindingWithKey("test.A").dependsOnBindingWithKey("test.B");
                assertThat(graph).bindingWithKey("test.B").dependsOnBindingWithKey("test.A");
              }
            })
        .compile(subject -> subject.hasErrorCount(0));
  }
}
