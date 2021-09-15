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

package dagger.internal.codegen;

import static com.google.common.truth.Truth.assertThat;
import static dagger.testing.compile.CompilerTests.kotlinCompiler;

import com.google.common.collect.ImmutableList;
import com.tschuchort.compiletesting.KotlinCompilation;
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode;
import com.tschuchort.compiletesting.SourceFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ComponentValidationKtTest {
  @Test
  public void creatorMethodNameIsJavaKeyword_compilationError() {
    SourceFile componentSrc =
        SourceFile.Companion.kotlin(
            "FooComponent.kt",
            String.join(
                "\n",
                "package test",
                "",
                "import dagger.BindsInstance",
                "import dagger.Component",
                "",
                "@Component",
                "interface FooComponent {",
                "  @Component.Builder",
                "  interface Builder {",
                "    @BindsInstance public fun int(str: Int): Builder",
                "    public fun build(): FooComponent",
                "  }",
                "}"),
            false);
    KotlinCompilation compilation = kotlinCompiler();
    compilation.setSources(ImmutableList.of(componentSrc));

    KotlinCompilation.Result result = compilation.compile();

    // TODO(b/192396673): Add error count when the feature request is fulfilled.
    assertThat(result.getExitCode()).isEqualTo(ExitCode.COMPILATION_ERROR);
    assertThat(result.getMessages())
        .contains("Can not use a Java keyword as method name: int(I)Ltest/FooComponent$Builder");
  }

  @Test
  public void componentMethodNameIsJavaKeyword_compilationError() {
    SourceFile componentSrc =
        SourceFile.Companion.kotlin(
            "FooComponent.kt",
            String.join(
                "\n",
                "package test",
                "",
                "import dagger.BindsInstance",
                "import dagger.Component",
                "",
                "@Component(modules = [TestModule::class])",
                "interface FooComponent {",
                "  fun int(str: Int): String",
                "}"),
            false);
    SourceFile moduleSrc =
        SourceFile.Companion.kotlin(
            "TestModule.kt",
            String.join(
                "\n",
                "package test",
                "",
                "import dagger.Module",
                "",
                "@Module",
                "interface TestModule {",
                "  fun providesString(): String {",
                "    return \"test\"",
                "  }",
                "}"),
            false);
    KotlinCompilation compilation = kotlinCompiler();
    compilation.setSources(ImmutableList.of(componentSrc, moduleSrc));

    KotlinCompilation.Result result = compilation.compile();

    assertThat(result.getExitCode()).isEqualTo(ExitCode.COMPILATION_ERROR);
    assertThat(result.getMessages())
        .contains("Can not use a Java keyword as method name: int(I)Ljava/lang/String");
  }
}
