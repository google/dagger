/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.android.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ContributesAndroidInjectorTest {
  private static final JavaFileObject TEST_ACTIVITY =
      JavaFileObjects.forSourceLines(
          "test.TestActivity",
          "package test;",
          "",
          "import android.app.Activity;",
          "",
          "class TestActivity extends Activity {}");

  @Test
  public void notAbstract() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.android.ContributesAndroidInjector;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @ContributesAndroidInjector",
            "  static TestActivity test() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation = compile(module, TEST_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("must be abstract")
        .inFile(module)
        .onLineContaining("test()");
  }

  @Test
  public void hasParameters() {
    JavaFileObject otherActivity =
        JavaFileObjects.forSourceLines(
            "test.OtherActivity",
            "package test;",
            "",
            "import android.app.Activity;",
            "",
            "class OtherActivity extends Activity {}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.android.ContributesAndroidInjector;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @ContributesAndroidInjector",
            "  abstract TestActivity oneParam(TestActivity one);",
            "",
            "  @ContributesAndroidInjector",
            "  abstract OtherActivity manyParams(OtherActivity two, Object o);",
            "}");

    Compilation compilation = compile(module, TEST_ACTIVITY, otherActivity);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("cannot have parameters")
        .inFile(module)
        .onLineContaining("oneParam(");
    assertThat(compilation)
        .hadErrorContaining("cannot have parameters")
        .inFile(module)
        .onLineContaining("manyParams(");
  }

  @Test
  public void notInAModule() {
    JavaFileObject randomFile =
        JavaFileObjects.forSourceLines(
            "test.RandomFile",
            "package test;",
            "",
            "import dagger.android.ContributesAndroidInjector;",
            "",
            "abstract class RandomFile {",
            "  @ContributesAndroidInjector",
            "  abstract TestActivity test() {}",
            "}");

    Compilation compilation = compile(randomFile, TEST_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("must be in a @Module")
        .inFile(randomFile)
        .onLineContaining("test()");
  }

  @Test
  public void parameterizedReturnType() {
    JavaFileObject parameterizedActivity =
        JavaFileObjects.forSourceLines(
            "test.ParameterizedActivity",
            "package test;",
            "",
            "import android.app.Activity;",
            "",
            "class ParameterizedActivity<T> extends Activity {}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.android.ContributesAndroidInjector;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @ContributesAndroidInjector",
            "  abstract <T> ParameterizedActivity<T> test();",
            "}");

    Compilation compilation = compile(module, TEST_ACTIVITY, parameterizedActivity);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("cannot return parameterized types")
        .inFile(module)
        .onLineContaining("test()");
  }

  @Test
  public void moduleIsntModule() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.android.ContributesAndroidInjector;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @ContributesAndroidInjector(modules = android.content.Intent.class)",
            "  abstract TestActivity test();",
            "}");

    Compilation compilation = compile(module, TEST_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Intent is not a @Module")
        .inFile(module)
        .onLineContaining("modules = android.content.Intent.class");
  }

  @Test
  public void hasQualifier() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.android.ContributesAndroidInjector;",
            "import javax.inject.Qualifier;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Qualifier @interface AndroidQualifier {}",
            "",
            "  @AndroidQualifier",
            "  @ContributesAndroidInjector",
            "  abstract TestActivity test();",
            "}");

    Compilation compilation = compile(module, TEST_ACTIVITY);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@ContributesAndroidInjector methods cannot have qualifiers")
        .inFile(module)
        .onLineContaining("@AndroidQualifier");
  }

  private static Compilation compile(JavaFileObject... javaFileObjects) {
    return javac().withProcessors(new AndroidProcessor()).compile(javaFileObjects);
  }
}
