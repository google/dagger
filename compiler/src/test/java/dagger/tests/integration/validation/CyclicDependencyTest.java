/**
 * Copyright (c) 2013 Google, Inc.
 * Copyright (c) 2013 Square, Inc.
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
package dagger.tests.integration.validation;

import com.google.common.base.Joiner;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static dagger.tests.integration.ProcessorTestUtils.daggerProcessors;
import static org.truth0.Truth.ASSERT;

@RunWith(JUnit4.class)
public class CyclicDependencyTest {

  @Test public void cyclicDepsWithInjectables() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("CyclicDeps", Joiner.on("\n").join(
        "import dagger.Module;",
        "import javax.inject.Inject;",
        "class CyclicDeps {",
        "  static class Foo {",
        "    @Inject Foo(Bar b) { }",
        "  }",
        "  static class Bar {",
        "    @Inject Bar(Blah b) { }",
        "  }",
        "  static class Blah {",
        "    @Inject Blah(Foo f) { }",
        "  }",
        "  static class EntryPoint {",
        "    @Inject Foo f;",
        "  }",
        "  @Module(injects = EntryPoint.class)",
        "  static class TestModule { }",
        "}"));

    ASSERT.about(javaSource()).that(sourceFile).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining("0. CyclicDeps$Foo bound by").in(sourceFile).onLine(17).and()
        .withErrorContaining("1. CyclicDeps$Bar bound by").in(sourceFile).onLine(17).and()
        .withErrorContaining("2. CyclicDeps$Blah bound by").in(sourceFile).onLine(17);
  }

  @Test public void cyclicDepsWithProvidesMethods() {
    JavaFileObject sourceFile = JavaFileObjects.forSourceString("CyclicDeps", Joiner.on("\n").join(
        "import dagger.Module;",
        "import dagger.Provides;",
        "class CyclicDeps {",
        "  static class A { }",
        "  static class B { }",
        "  static class C { }",
        "  static class D { }",
        "  @Module(injects = D.class)",
        "  static class CyclicModule {",
        "    @Provides A a(D d) { return null; }",
        "    @Provides B b(A a) { return null; }",
        "    @Provides C c(B b) { return null; }",
        "    @Provides D d(C c) { return null; }",
        "  }",
        "}"));

    ASSERT.about(javaSource()).that(sourceFile).processedWith(daggerProcessors()).failsToCompile()
        .withErrorContaining("0. CyclicDeps$A bound by Provider").in(sourceFile).onLine(9).and()
        .withErrorContaining("1. CyclicDeps$D bound by Provider").in(sourceFile).onLine(9).and()
        .withErrorContaining("2. CyclicDeps$C bound by Provider").in(sourceFile).onLine(9).and()
        .withErrorContaining("3. CyclicDeps$B bound by Provider").in(sourceFile).onLine(9);
  }

}
