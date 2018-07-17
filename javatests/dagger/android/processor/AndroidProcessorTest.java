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

import static com.google.common.truth.Truth8.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AndroidProcessorTest {
  @Test
  public void generatedProguardFile() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.android.AndroidInjectionKey;",
            "import dagger.Module;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class TestModule {",
            "  @Provides",
            "  @IntoMap",
            "  @AndroidInjectionKey(\"test.TestActivity\")",
            "  static int i() { ",
            "    return 1;",
            "  }",
            "}");
    Compilation enabled =
        javac()
            .withProcessors(new AndroidProcessor())
            .withOptions("-Adagger.android.experimentalUseStringKeys=true")
            .compile(module);
    assertThat(enabled).succeeded();
    assertThat(enabled)
        .generatedFile(CLASS_OUTPUT, "META-INF/proguard/dagger.android.AndroidInjectionKeys");

    Compilation disabled =
        javac()
            .withProcessors(new AndroidProcessor())
            .withOptions("-Adagger.android.experimentalUseStringKeys=false")
            .compile(module);
    assertThat(disabled).succeeded();
    assertThat(
            disabled.generatedFile(
                CLASS_OUTPUT, "META-INF/proguard/dagger.android.AndroidInjectionKeys"))
        .isEmpty();

    Compilation noFlag = javac().withProcessors(new AndroidProcessor()).compile(module);
    assertThat(noFlag).succeeded();
    assertThat(
            noFlag.generatedFile(
                CLASS_OUTPUT, "META-INF/proguard/dagger.android.AndroidInjectionKeys"))
        .isEmpty();
  }
}
