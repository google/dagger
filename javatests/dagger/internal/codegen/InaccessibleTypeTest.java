/*
 * Copyright (C) 2015 The Dagger Authors.
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
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

import com.google.common.collect.FluentIterable;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InaccessibleTypeTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public InaccessibleTypeTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test public void basicInjectedType() {
    JavaFileObject noDepClassFile = JavaFileObjects.forSourceLines("foreign.NoDepClass",
        "package foreign;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class NoDepClass {",
        "  @Inject NoDepClass() {}",
        "}");
    JavaFileObject publicClassFile = JavaFileObjects.forSourceLines("foreign.PublicClass",
        "package foreign;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class PublicClass {",
        "  @Inject PublicClass(NonPublicClass1 dep1, NonPublicClass2 dep2, NoDepClass dep3) {}",
        "}");
    JavaFileObject nonPublicClass1File = JavaFileObjects.forSourceLines("foreign.NonPublicClass1",
        "package foreign;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class NonPublicClass1 {",
        "  @Inject NonPublicClass1(NoDepClass dep) {}",
        "}");
    JavaFileObject nonPublicClass2File = JavaFileObjects.forSourceLines("foreign.NonPublicClass2",
        "package foreign;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class NonPublicClass2 {",
        "  @Inject NonPublicClass2(NoDepClass dep) {}",
        "}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import foreign.PublicClass;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface TestComponent {",
        "  PublicClass publicClass();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import foreign.NoDepClass_Factory;",
            "import foreign.NonPublicClass1_Factory;",
            "import foreign.NonPublicClass2_Factory;",
            "import foreign.PublicClass;",
            "import foreign.PublicClass_Factory;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private Object getNonPublicClass1() {",
            "    return NonPublicClass1_Factory.newNonPublicClass1(",
            "        NoDepClass_Factory.newNoDepClass());",
            "  }",
            "",
            "  private Object getNonPublicClass2() {",
            "    return NonPublicClass2_Factory.newNonPublicClass2(",
            "        NoDepClass_Factory.newNoDepClass());",
            "  }",
            "",
            "  @Override",
            "  public PublicClass publicClass() {",
            "    return PublicClass_Factory.newPublicClass(",
            "        getNonPublicClass1(), ",
            "        getNonPublicClass2(), ",
            "        NoDepClass_Factory.newNoDepClass());",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(
                FluentIterable.from(compilerMode.javacopts())
                    .append(
                        "-Xlint:-processing",
                        "-Xlint:rawtypes",
                        "-Xlint:unchecked",
                        "-Xlint:-classfile"))
            .compile(
                noDepClassFile,
                publicClassFile,
                nonPublicClass1File,
                nonPublicClass2File,
                componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }

  @Test public void memberInjectedType() {
    JavaFileObject noDepClassFile = JavaFileObjects.forSourceLines("test.NoDepClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class NoDepClass {",
        "  @Inject NoDepClass() {}",
        "}");
    JavaFileObject aClassFile = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import foreign.B;",
        "import javax.inject.Inject;",
        "",
        "final class A extends B {",
        "  @Inject NoDepClass dep;",
        "}");
    JavaFileObject bClassFile = JavaFileObjects.forSourceLines("foreign.B",
        "package foreign;",
        "",
        "import test.NoDepClass;",
        "import javax.inject.Inject;",
        "",
        "public class B extends C {",
        "  @Inject NoDepClass dep;",
        "}");
    JavaFileObject cClassFile = JavaFileObjects.forSourceLines("foreign.C",
        "package foreign;",
        "",
        "import test.D;",
        "import test.NoDepClass;",
        "import javax.inject.Inject;",
        "",
        "class C extends D {",
        "  @Inject NoDepClass dep;",
        "}");
    JavaFileObject dClassFile = JavaFileObjects.forSourceLines("test.D",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "public class D {",
        "  @Inject NoDepClass dep;",
        "}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import javax.inject.Provider;",
        "",
        "@Component",
        "interface TestComponent {",
        "  void injectA(A a);",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects.forSourceLines(
            "test.DaggerTestComponent",
            "package test;",
            "",
            "import foreign.B_MembersInjector;",
            "import foreign.C_MembersInjector;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  @Override",
            "  public void injectA(A a) {",
            "    injectA2(a);",
            "  }",
            "",
            "  @CanIgnoreReturnValue",
            "  private A injectA2(A instance) {",
            "    D_MembersInjector.injectDep(instance, new NoDepClass());",
            "    C_MembersInjector.injectDep(instance, new NoDepClass());",
            "    B_MembersInjector.injectDep(instance, new NoDepClass());",
            "    A_MembersInjector.injectDep(instance, new NoDepClass());",
            "    return instance;",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .withOptions(
                FluentIterable.from(compilerMode.javacopts())
                    .append(
                        "-Xlint:-processing",
                        "-Xlint:rawtypes",
                        "-Xlint:unchecked",
                        "-Xlint:-classfile"))
            .compile(
                noDepClassFile,
                aClassFile, bClassFile, cClassFile, dClassFile, componentFile);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(generatedComponent);
  }
}
