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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BindsInstanceValidationTest {
  @Test
  public void bindsInstanceInModule() {
    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation = daggerCompiler().compile(testModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@BindsInstance methods should not be included in @Modules. Did you mean @Binds");
  }

  @Test
  public void bindsInstanceInComponent() {
    JavaFileObject testComponent =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation = daggerCompiler().compile(testComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@BindsInstance methods should not be included in @Components. "
                + "Did you mean to put it in a @Component.Builder?");
  }

  @Test
  public void bindsInstanceNotAbstract() {
    JavaFileObject notAbstract =
        JavaFileObjects.forSourceLines(
            "test.BindsInstanceNotAbstract",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "import dagger.Component;",
            "",
            "class BindsInstanceNotAbstract {",
            "  @BindsInstance BindsInstanceNotAbstract bind(int unused) { return this; }",
            "}");
    Compilation compilation = daggerCompiler().compile(notAbstract);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@BindsInstance methods must be abstract")
        .inFile(notAbstract)
        .onLine(7);
  }

  @Test
  public void bindsInstanceNoParameters() {
    JavaFileObject notAbstract =
        JavaFileObjects.forSourceLines(
            "test.BindsInstanceNoParameters",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "",
            "interface BindsInstanceNoParameters {",
            "  @BindsInstance void noParams();",
            "}");
    Compilation compilation = daggerCompiler().compile(notAbstract);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@BindsInstance methods should have exactly one parameter for the bound type")
        .inFile(notAbstract)
        .onLine(6);
  }

  @Test
  public void bindsInstanceManyParameters() {
    JavaFileObject notAbstract =
        JavaFileObjects.forSourceLines(
            "test.BindsInstanceNoParameter",
            "package test;",
            "",
            "import dagger.BindsInstance;",
            "",
            "interface BindsInstanceManyParameters {",
            "  @BindsInstance void manyParams(int i, long l);",
            "}");
    Compilation compilation = daggerCompiler().compile(notAbstract);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@BindsInstance methods should have exactly one parameter for the bound type")
        .inFile(notAbstract)
        .onLine(6);
  }

  @Test
  public void bindsInstanceFrameworkType() {
    JavaFileObject bindsFrameworkType =
        JavaFileObjects.forSourceLines(
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
    Compilation compilation = daggerCompiler().compile(bindsFrameworkType);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("@BindsInstance parameters must not be framework types")
        .inFile(bindsFrameworkType)
        .onLine(8);

    assertThat(compilation)
        .hadErrorContaining("@BindsInstance parameters must not be framework types")
        .inFile(bindsFrameworkType)
        .onLine(9);
  }

}
