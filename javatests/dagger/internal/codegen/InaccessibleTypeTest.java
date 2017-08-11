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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static dagger.internal.codegen.GeneratedLines.GENERATED_ANNOTATION;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class InaccessibleTypeTest {
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
            "import javax.annotation.Generated;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private DaggerTestComponent(Builder builder) {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
            "  @Override",
            "  public PublicClass publicClass() {",
            "    return PublicClass_Factory.newPublicClass(",
            "        NonPublicClass1_Factory.newNonPublicClass1(",
            "            NoDepClass_Factory.newNoDepClass()),",
            "        NonPublicClass2_Factory.newNonPublicClass2(",
            "            NoDepClass_Factory.newNoDepClass()),",
            "        NoDepClass_Factory.newNoDepClass());",
            "  }",
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(
            ImmutableList.of(
                noDepClassFile,
                publicClassFile,
                nonPublicClass1File,
                nonPublicClass2File,
                componentFile))
        .withCompilerOptions(
            "-Xlint:-processing",
            "-Xlint:rawtypes",
            "-Xlint:unchecked",
            "-Werror")
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
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
            "import com.google.errorprone.annotations.CanIgnoreReturnValue;",
            "import foreign.B_MembersInjector;",
            "import foreign.C_MembersInjector;",
            "import javax.annotation.Generated;",
            "",
            GENERATED_ANNOTATION,
            "public final class DaggerTestComponent implements TestComponent {",
            "  private DaggerTestComponent(Builder builder) {}",
            "",
            "  public static Builder builder() {",
            "    return new Builder();",
            "  }",
            "",
            "  public static TestComponent create() {",
            "    return new Builder().build();",
            "  }",
            "",
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
            "",
            "  public static final class Builder {",
            "    private Builder() {",
            "    }",
            "",
            "    public TestComponent build() {",
            "      return new DaggerTestComponent(this);",
            "    }",
            "  }",
            "}");
    assertAbout(javaSources())
        .that(
            ImmutableList.of(
                noDepClassFile, aClassFile, bClassFile, cClassFile, dClassFile, componentFile))
        .withCompilerOptions(
            "-Xlint:-processing",
            "-Xlint:rawtypes",
            "-Xlint:unchecked",
            "-Werror")
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
  }
}
