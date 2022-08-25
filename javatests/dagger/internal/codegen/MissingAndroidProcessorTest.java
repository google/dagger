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

package dagger.internal.codegen;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MissingAndroidProcessorTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public MissingAndroidProcessorTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void missingProcessor() {
    Source module =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.android.ContributesAndroidInjector;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface TestModule {",
            "  @ContributesAndroidInjector",
            "  Object o();",
            "}");
    Source contributesAndroidInjectorStub =
        CompilerTests.javaSource(
            "dagger.android.ContributesAndroidInjector",
            "package dagger.android;",
            "",
            "public @interface ContributesAndroidInjector {}");

    CompilerTests.daggerCompiler(module, contributesAndroidInjectorStub)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("dagger.android.processor.AndroidProcessor")
                  .onSource(module)
                  .onLine(9);
            });
  }
}
