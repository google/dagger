/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static dagger.internal.codegen.base.ComponentCreatorAnnotation.SUBCOMPONENT_BUILDER;
import static dagger.internal.codegen.binding.ErrorMessages.creatorMessagesFor;

import androidx.room.compiler.processing.util.Source;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.binding.ErrorMessages;
import dagger.testing.compile.CompilerTests;
import dagger.testing.golden.GoldenFileRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for {@link dagger.Subcomponent.Builder} validation. */
@RunWith(Parameterized.class)
public class SubcomponentBuilderValidationTest {
  @Parameters(name = "{0}")
  public static ImmutableList<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  private final CompilerMode compilerMode;

  public SubcomponentBuilderValidationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final ErrorMessages.ComponentCreatorMessages MSGS =
      creatorMessagesFor(SUBCOMPONENT_BUILDER);

  @Test
  public void testMoreThanOneArgFails() {
    Source childComponentFile = CompilerTests.javaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    Builder set(String s, Integer i);",
        "    Builder set(Number n, Double d);",
        "  }",
        "}");

    CompilerTests.daggerCompiler(childComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(2);
              subject
                  .hasErrorContaining(MSGS.setterMethodsMustTakeOneArg())
                  .onSource(childComponentFile)
                  .onLine(10);
              subject
                  .hasErrorContaining(MSGS.setterMethodsMustTakeOneArg())
                  .onSource(childComponentFile)
                  .onLine(11);
            });
  }

  @Test
  public void testInheritedMoreThanOneArgFails() {
     Source childComponentFile = CompilerTests.javaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    Builder set1(String s, Integer i);",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");

    String expectedErrorMsg =
        String.format(
            MSGS.inheritedSetterMethodsMustTakeOneArg(),
            "test.ChildComponent.Builder test.ChildComponent.Parent.set1(String, Integer)");
    CompilerTests.daggerCompiler(childComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(expectedErrorMsg)
                  .onSource(childComponentFile)
                  .onLine(13);
            });
  }

  @Test
  public void testSetterReturningNonVoidOrBuilderFails() {
     Source childComponentFile = CompilerTests.javaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    String set(Integer i);",
        "  }",
        "}");

    CompilerTests.daggerCompiler(childComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(MSGS.setterMethodsMustReturnVoidOrBuilder())
                  .onSource(childComponentFile)
                  .onLine(10);
            });
  }

  @Test
  public void testInheritedSetterReturningNonVoidOrBuilderFails() {
     Source childComponentFile = CompilerTests.javaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    String set(Integer i);",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");

    CompilerTests.daggerCompiler(childComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      String.format(
                          MSGS.inheritedSetterMethodsMustReturnVoidOrBuilder(),
                          "String test.ChildComponent.Parent.set(Integer)"))
                  .onSource(childComponentFile)
                  .onLine(13);
            });
  }

  @Test
  public void testGenericsOnSetterMethodFails() {
     Source childComponentFile = CompilerTests.javaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  @Subcomponent.Builder",
        "  interface Builder {",
        "    ChildComponent build();",
        "    <T> Builder set(T t);",
        "  }",
        "}");

    CompilerTests.daggerCompiler(childComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(MSGS.methodsMayNotHaveTypeParameters())
                  .onSource(childComponentFile)
                  .onLine(10);
            });
  }

  @Test
  public void testGenericsOnInheritedSetterMethodFails() {
     Source childComponentFile = CompilerTests.javaSource("test.ChildComponent",
        "package test;",
        "",
        "import dagger.Subcomponent;",
        "",
        "@Subcomponent",
        "abstract class ChildComponent {",
        "  interface Parent {",
        "    ChildComponent build();",
        "    <T> Builder set(T t);",
        "  }",
        "",
        "  @Subcomponent.Builder",
        "  interface Builder extends Parent {}",
        "}");

    CompilerTests.daggerCompiler(childComponentFile)
        .withProcessingOptions(compilerMode.processorOptions())
        .compile(
            subject -> {
              subject.hasErrorCount(1);
              subject
                  .hasErrorContaining(
                      String.format(
                          MSGS.inheritedMethodsMayNotHaveTypeParameters(),
                          "test.ChildComponent.Builder test.ChildComponent.Parent.set(T)"))
                  .onSource(childComponentFile)
                  .onLine(13);
            });
  }
}
