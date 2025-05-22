/*
 * Copyright (C) 2014 The Dagger Authors.
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
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import dagger.testing.compile.CompilerTests.DaggerCompiler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests compilation with the {@code experimental_turbine_hjar} flag enabled. */
@RunWith(JUnit4.class)
public class HjarTest {
  /** Returns a {@link DaggerCompiler} with hjar generation enabled. */
  private static DaggerCompiler daggerCompiler(Source... sources) {
    return CompilerTests.daggerCompiler(sources)
        .withProcessingOptions(ImmutableMap.of("experimental_turbine_hjar", ""));
  }

  @Test
  public void componentTest() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface MyComponent {",
            "  String getString();",
            "  int getInt();",
            "  void inject(String str);",
            "}");
    daggerCompiler(component).compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void moduleTest() {
    Source module =
        CompilerTests.javaSource(
            "test.MyModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface MyModule {",
            "  @Provides static int provideInt() { return 0; }",
            "  @Provides static String provideString() { return null; }",
            "  @Provides static String[] provideStringArray() { return null; }",
            "  @Provides static int[] provideIntArray() { return null; }",
            "  @Provides static boolean provideBoolean() { return false; }",
            "}");
    daggerCompiler(module).compile(subject -> subject.hasErrorCount(0));
  }

  @Test
  public void producerModuleTest() {
    Source module =
        CompilerTests.javaSource(
            "test.MyModule",
            "package test;",
            "",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import dagger.producers.ProducerModule;",
            "import dagger.producers.Produces;",
            "",
            "@ProducerModule",
            "interface MyModule {",
            "  @Produces static ListenableFuture<String> produceString() { return null; }",
            "}");
    daggerCompiler(module).compile(subject -> subject.hasErrorCount(0));
  }
}
