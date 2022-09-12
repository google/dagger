/*
 * Copyright (C) 2016 The Dagger Authors.
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
public class OptionalBindingTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public OptionalBindingTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  @Test
  public void provideExplicitOptionalInParent_AndBindsOptionalOfInChild() {
    Source parent =
        CompilerTests.javaSource(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Optional;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Optional<String> optional();",
            "  Child child();",
            "}");
    Source parentModule =
        CompilerTests.javaSource(
            "test.ParentModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import java.util.Optional;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides",
            "  Optional<String> optional() {",
            "    return Optional.of(new String());",
            "  }",
            "}");

    Source child =
        CompilerTests.javaSource(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "import java.util.Optional;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Optional<String> optional();",
            "}");
    Source childModule =
        CompilerTests.javaSource(
            "test.ChildModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface ChildModule {",
            "  @BindsOptionalOf",
            "  String optionalString();",
            "}");

    CompilerTests.daggerCompiler(parent, parentModule, child, childModule)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject.hasErrorContaining("Optional<String> is bound multiple times")
                  .onSource(parent)
                  .onLineContaining("interface Parent");
            });
  }
}
