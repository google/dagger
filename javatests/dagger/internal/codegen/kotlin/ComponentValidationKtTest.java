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

package dagger.internal.codegen.kotlin;

import static com.google.common.truth.Truth.assertThat;
import static dagger.testing.compile.CompilerTests.compileWithKapt;

import androidx.room3.compiler.processing.util.DiagnosticMessage;
import androidx.room3.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.tools.Diagnostic.Kind;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ComponentValidationKtTest {

  @Rule
  public TemporaryFolder tempFolderRule = new TemporaryFolder();

  @Test
  public void creatorMethodNameIsJavaKeyword_compilationError() {
    Source componentSrc =
        Source.Companion.kotlin(
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
                "}"));

    compileWithKapt(
        ImmutableList.of(componentSrc),
        tempFolderRule,
        result -> {
          // TODO(b/192396673): Add error count when the feature request is fulfilled.
          assertThat(result.getSuccess()).isFalse();
          List<DiagnosticMessage> errors = result.getDiagnostics().get(Kind.ERROR);
          assertThat(errors).hasSize(1);
          assertThat(errors.get(0).getMsg())
              .contains("The name 'int' cannot be used because it is a Java keyword");
        });
  }

  @Test
  public void componentMethodNameIsJavaKeyword_compilationError() {
    Source componentSrc =
        Source.Companion.kotlin(
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
                "}"));
    Source moduleSrc =
        Source.Companion.kotlin(
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
                "}"));

    compileWithKapt(
        ImmutableList.of(componentSrc, moduleSrc),
        tempFolderRule,
        result -> {
          assertThat(result.getSuccess()).isFalse();
          List<DiagnosticMessage> errors = result.getDiagnostics().get(Kind.ERROR);
          assertThat(errors).hasSize(1);
          assertThat(errors.get(0).getMsg())
              .contains("The name 'int' cannot be used because it is a Java keyword");
        });
  }
}
