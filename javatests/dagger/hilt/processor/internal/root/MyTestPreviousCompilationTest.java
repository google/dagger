/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.processor.internal.root;

import static com.google.testing.compile.CompilationSubject.assertThat;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import dagger.hilt.android.testing.compile.HiltCompilerTests;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class MyTestPreviousCompilationTest {

  @Parameters(name = "{0}")
  public static ImmutableCollection<Object[]> parameters() {
    return ImmutableList.copyOf(new Object[][] {{true}, {false}});
  }

  private final boolean disableCrossCompilationRootValidation;

  public MyTestPreviousCompilationTest(boolean disableCrossCompilationRootValidation) {
    this.disableCrossCompilationRootValidation = disableCrossCompilationRootValidation;
  }

  private Compiler compiler() {
    return HiltCompilerTests.compiler()
        .withOptions(
            String.format(
                "-Adagger.hilt.disableCrossCompilationRootValidation=%s",
                disableCrossCompilationRootValidation));
  }

  @Test
  public void testRootTest() {
    JavaFileObject testRoot =
        JavaFileObjects.forSourceLines(
            "test.TestRoot",
            "package test;",
            "",
            "import dagger.hilt.android.testing.HiltAndroidTest;",
            "",
            "@HiltAndroidTest",
            "public class TestRoot {}");

    Compilation compilation = compiler().compile(testRoot);
    if (disableCrossCompilationRootValidation) {
      assertThat(compilation).succeeded();
    } else {
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining(
              "Cannot process new roots when there are test roots from a previous compilation unit:"
                  + "\n    Test roots from previous compilation unit: "
                  + "dagger.hilt.processor.internal.root.MyTestPreviousCompilation.MyTest"
                  + "\n    All roots from this compilation unit: test.TestRoot");
    }
  }

  @Test
  public void appRootTest() {
    JavaFileObject appRoot =
        JavaFileObjects.forSourceLines(
            "test.AppRoot",
            "package test;",
            "",
            "import android.app.Application;",
            "import dagger.hilt.android.HiltAndroidApp;",
            "",
            "@HiltAndroidApp(Application.class)",
            "public class AppRoot extends Hilt_AppRoot {}");

    Compilation compilation = compiler().compile(appRoot);
    if (disableCrossCompilationRootValidation) {
      assertThat(compilation).succeeded();
    } else {
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining(
              "Cannot process new roots when there are test roots from a previous compilation unit:"
                  + "\n    Test roots from previous compilation unit: "
                  + "dagger.hilt.processor.internal.root.MyTestPreviousCompilation.MyTest"
                  + "\n    All roots from this compilation unit: test.AppRoot");
    }
  }
}
