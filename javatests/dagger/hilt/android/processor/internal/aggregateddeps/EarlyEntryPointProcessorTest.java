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

package dagger.hilt.android.processor.internal.aggregateddeps;

import androidx.room.compiler.processing.util.Source;
import dagger.hilt.android.testing.compile.HiltCompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EarlyEntryPointProcessorTest {

  @Test
  public void testUsedWithEntryPoint_fails() {
    Source entryPoint =
        HiltCompilerTests.javaSource(
            "test.UsedWithEntryPoint",
            "package test;",
            "",
            "import dagger.hilt.android.EarlyEntryPoint;",
            "import dagger.hilt.EntryPoint;",
            "import dagger.hilt.InstallIn;",
            "import dagger.hilt.components.SingletonComponent;",
            "",
            "@EarlyEntryPoint",
            "@EntryPoint",
            "@InstallIn(SingletonComponent.class)",
            "public interface UsedWithEntryPoint {}");
    HiltCompilerTests.hiltCompiler(entryPoint)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      "Only one of the following annotations can be used on"
                          + " test.UsedWithEntryPoint: [dagger.hilt.EntryPoint,"
                          + " dagger.hilt.android.EarlyEntryPoint]")
                  .onSource(entryPoint)
                  .onLine(11);
            });
  }

  @Test
  public void testNotSingletonComponent_fails() {
    Source entryPoint =
        HiltCompilerTests.javaSource(
            "test.NotSingletonComponent",
            "package test;",
            "",
            "import dagger.hilt.android.EarlyEntryPoint;",
            "import dagger.hilt.android.components.ActivityComponent;",
            "import dagger.hilt.EntryPoint;",
            "import dagger.hilt.InstallIn;",
            "",
            "@EarlyEntryPoint",
            "@InstallIn(ActivityComponent.class)",
            "public interface NotSingletonComponent {}");

    HiltCompilerTests.hiltCompiler(entryPoint)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      "@EarlyEntryPoint can only be installed into the SingletonComponent. "
                          + "Found: [dagger.hilt.android.components.ActivityComponent]")
                  .onSource(entryPoint)
                  .onLine(10);
            });
  }

  @Test
  public void testThatTestInstallInCannotOriginateFromTest() {
    Source test =
        HiltCompilerTests.javaSource(
            "test.MyTest",
            "package test;",
            "",
            "import dagger.hilt.EntryPoint;",
            "import dagger.hilt.InstallIn;",
            "import dagger.hilt.android.EarlyEntryPoint;",
            "import dagger.hilt.android.testing.HiltAndroidTest;",
            "import dagger.hilt.components.SingletonComponent;",
            "",
            "@HiltAndroidTest",
            "public class MyTest {",
            "  @EarlyEntryPoint",
            "  @InstallIn(SingletonComponent.class)",
            "  interface NestedEarlyEntryPoint {}",
            "}");
    HiltCompilerTests.hiltCompiler(test)
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      "@EarlyEntryPoint-annotated entry point, test.MyTest.NestedEarlyEntryPoint,"
                          + " cannot be nested in (or originate from) a @HiltAndroidTest-annotated"
                          + " class, test.MyTest.")
                  .onSource(test)
                  .onLine(13);
            });
  }
}
