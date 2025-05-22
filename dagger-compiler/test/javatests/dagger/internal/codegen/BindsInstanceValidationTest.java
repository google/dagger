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
import com.google.common.collect.ImmutableList;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BindsInstanceValidationTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public BindsInstanceValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void bindsInstanceInModule() {
    Source testModule =
        CompilerTests.javaSource(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @BindsInstance abstract void str(String string);",
            "}");
    CompilerTests.daggerCompiler(testModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@BindsInstance methods should not be included in @Modules. Did you mean @Binds");
            });
  }

  @Test
  public void bindsInstanceInComponent() {
    Source testComponent =
        CompilerTests.javaSource(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  @BindsInstance String s(String s);",
            "}");
    CompilerTests.daggerCompiler(testComponent)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                  "@BindsInstance methods should not be included in @Components. "
                      + "Did you mean to put it in a @Component.Builder?");
            });
  }

  @Test
  public void bindsInstanceNotAbstract() {
    Source notAbstract =
        CompilerTests.javaSource(
            "test.BindsInstanceNotAbstract",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "class BindsInstanceNotAbstract {",
            "  @BindsInstance BindsInstanceNotAbstract bind(int unused) { return this; }",
            "}");
    CompilerTests.daggerCompiler(notAbstract)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("@BindsInstance methods must be abstract")
                  .onSource(notAbstract)
                  .onLine(7);
            });
  }

  @Test
  public void bindsInstanceNoParameters() {
    Source notAbstract =
        CompilerTests.javaSource(
            "test.BindsInstanceNoParameters",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "",
            "interface BindsInstanceNoParameters {",
            "  @BindsInstance void noParams();",
            "}");
    CompilerTests.daggerCompiler(notAbstract)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@BindsInstance methods should have exactly one parameter for the bound type")
                  .onSource(notAbstract)
                  .onLine(6);
            });
  }

  @Test
  public void bindsInstanceManyParameters() {
    Source notAbstract =
        CompilerTests.javaSource(
            "test.BindsInstanceNoParameter",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "",
            "interface BindsInstanceManyParameters {",
            "  @BindsInstance void manyParams(int i, long l);",
            "}");
    CompilerTests.daggerCompiler(notAbstract)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining(
                      "@BindsInstance methods should have exactly one parameter for the bound type")
                  .onSource(notAbstract)
                  .onLine(6);
            });
  }

  @Test
  public void bindsInstanceFrameworkType() {
    Source bindsFrameworkType =
        CompilerTests.javaSource(
            "test.BindsInstanceFrameworkType",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.producers.Producer;",
            "import javax.inject.Provider;",
            "",
            "interface BindsInstanceFrameworkType {",
            "  @BindsInstance void bindsProvider(Provider<Object> objectProvider);",
            "  @BindsInstance void bindsProducer(Producer<Object> objectProducer);",
            "}");
    CompilerTests.daggerCompiler(bindsFrameworkType)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject.hasErrorContaining("@BindsInstance parameters must not be framework types")
                  .onSource(bindsFrameworkType)
                  .onLine(8);
              subject.hasErrorContaining("@BindsInstance parameters must not be framework types")
                  .onSource(bindsFrameworkType)
                  .onLine(9);
            });
  }

  @Test
  public void bindsInstanceDaggerProvider() {
    Source bindsDaggerProvider =
        CompilerTests.javaSource(
            "test.BindsInstanceFrameworkType",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.internal.Provider;",
            "import dagger.producers.Producer;",
            "",
            "interface BindsInstanceFrameworkType {",
            "  @BindsInstance void bindsProvider(Provider<Object> objectProvider);",
            "}");
    CompilerTests.daggerCompiler(bindsDaggerProvider)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("@BindsInstance parameters must not be disallowed types")
                  .onSource(bindsDaggerProvider)
                  .onLine(8);
            });
  }
}
