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

import androidx.room.compiler.processing.util.Source;
import dagger.testing.compile.CompilerTests;
import dagger.testing.compile.CompilerTests.DaggerCompiler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ContributesAndroidInjectorTest {
  private static final Source TEST_ACTIVITY =
      CompilerTests.javaSource(
          "test.TestActivity",
          "package test;",
          "",
          "import android.app.Activity;",
          "",
          "class TestActivity extends Activity {}");

  @Test
  public void notAbstract() {
    Source module =
        CompilerTests.javaSource(
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

    compile(module, TEST_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorContaining("must be abstract").onLineContaining("test()");
            });
  }

  @Test
  public void hasParameters() {
    Source otherActivity =
        CompilerTests.javaSource(
            "test.OtherActivity",
            "package test;",
            "",
            "import android.app.Activity;",
            "",
            "class OtherActivity extends Activity {}");
    Source module =
        CompilerTests.javaSource(
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

    compile(module, TEST_ACTIVITY, otherActivity)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorContaining("cannot have parameters").onLineContaining("oneParam(");
              subject.hasErrorContaining("cannot have parameters").onLineContaining("manyParams(");
            });
  }

  @Test
  public void notInAModule() {
    Source randomFile =
        CompilerTests.javaSource(
            "test.RandomFile",
            "package test;",
            "",
            "import dagger.android.ContributesAndroidInjector;",
            "",
            "abstract class RandomFile {",
            "  @ContributesAndroidInjector",
            "  abstract TestActivity test() {}",
            "}");

    compile(randomFile, TEST_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject.hasErrorContaining("must be in a @Module").onLineContaining("test()");
            });
  }

  @Test
  public void parameterizedReturnType() {
    Source parameterizedActivity =
        CompilerTests.javaSource(
            "test.ParameterizedActivity",
            "package test;",
            "",
            "import android.app.Activity;",
            "",
            "class ParameterizedActivity<T> extends Activity {}");
    Source module =
        CompilerTests.javaSource(
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

    compile(module, TEST_ACTIVITY, parameterizedActivity)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject
                  .hasErrorContaining("cannot return parameterized types")
                  .onLineContaining("test()");
            });
  }

  @Test
  public void moduleIsntModule() {
    Source module =
        CompilerTests.javaSource(
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

    compile(module, TEST_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject
                  .hasErrorContaining("Intent is not a @Module")
                  .onLineContaining("modules = android.content.Intent.class");
            });
  }

  @Test
  public void hasQualifier() {
    Source module =
        CompilerTests.javaSource(
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

    compile(module, TEST_ACTIVITY)
        .compile(
            subject -> {
              subject.compilationDidFail();
              subject
                  .hasErrorContaining("@ContributesAndroidInjector methods cannot have qualifiers")
                  .onLineContaining("@AndroidQualifier");
            });
  }

  private static DaggerCompiler compile(Source... sources) {
    return CompilerTests.daggerCompiler(sources)
        .withAdditionalJavacProcessors(new AndroidProcessor())
        .withAdditionalKspProcessors(new KspAndroidProcessor.Provider());
  }
}
