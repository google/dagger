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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.Compilers.daggerCompiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MissingAndroidProcessorTest {
  @Test
  public void missingProcessor() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
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
    JavaFileObject contributesAndroidInjectorStub =
        JavaFileObjects.forSourceLines(
            "dagger.android.ContributesAndroidInjector",
            "package dagger.android;",
            "",
            "public @interface ContributesAndroidInjector {}");
    Compilation compilation = daggerCompiler().compile(module, contributesAndroidInjectorStub);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("dagger.android.processor.AndroidProcessor")
        .inFile(module)
        .onLine(9);
  }
}
