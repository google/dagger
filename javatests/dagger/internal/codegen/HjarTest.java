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

import androidx.room3.compiler.processing.util.Source;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import dagger.testing.compile.CompilerTests;
import dagger.testing.compile.CompilerTests.DaggerCompiler;
import dagger.testing.golden.GoldenFileRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests compilation with the {@code experimental_turbine_hjar} flag enabled. */
@RunWith(JUnit4.class)
public class HjarTest {
  private static final Joiner NEWLINES = Joiner.on('\n');

  @Rule public GoldenFileRule goldenFileRule = new GoldenFileRule();

  /** Returns a {@link DaggerCompiler} with hjar generation enabled. */
  private static DaggerCompiler daggerCompiler(Source... sources) {
    return CompilerTests.daggerCompiler(sources)
        .withProcessingOptions(ImmutableMap.of("experimental_turbine_hjar", ""));
  }

  /**
   * Returns a {@link DaggerCompiler} with both hjar generation and {@code
   * generatedClassExtendsComponent} enabled.
   */
  private static DaggerCompiler daggerCompilerExtendingComponent(Source... sources) {
    return CompilerTests.daggerCompiler(sources)
        .withProcessingOptions(
            ImmutableMap.of(
                "experimental_turbine_hjar", "",
                "dagger.generatedClassExtendsComponent", "ENABLED"));
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
    daggerCompiler(component)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerMyComponent"));
            });
  }

  @Test
  public void componentWithModuleTest() {
    Source module =
        CompilerTests.javaSource(
            "test.MyModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "public final class MyModule {",
            "  private final String value;",
            "  public MyModule(String value) { this.value = value; }",
            "  @Provides String provideString() { return value; }",
            "}");
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = MyModule.class)",
            "public interface MyComponent {",
            "  String getString();",
            "}");
    daggerCompiler(module, component)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerMyComponent"));
            });
  }

  @Test
  public void componentExtendsInterface_allReturnTypesTest() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface MyComponent {",
            "  boolean getBoolean();",
            // `char` is tested separately in componentExtendsInterface_charReturnTypeTest.
            "  byte getByte();",
            "  short getShort();",
            "  int getInt();",
            "  long getLong();",
            "  float getFloat();",
            "  double getDouble();",
            "  String getString();",
            "  Void getBoxedVoid();",
            "  int[] getIntArray();",
            "  void inject(String str);",
            "}");
    daggerCompilerExtendingComponent(component)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerMyComponent"));
            });
  }

  @Test
  public void componentExtendsInterface_charReturnTypeTest() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface MyComponent {",
            "  char getChar();",
            "}");
    daggerCompilerExtendingComponent(component)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              // Use a targeted assertion instead of a golden file because HjarGenerator emits a
              // literal NUL byte (U+0000) for char's default value, which causes diff/golden
              // tooling to treat a golden file as binary or replace it with U+FFFD.
              subject
                  .generatedSourceFileWithPath("test/DaggerMyComponent.java")
                  .contains(
                      NEWLINES.join(
                          "  @Override",
                          "  public char getChar() {",
                          "    return '\0';",
                          "  }"));
            });
  }

  @Test
  public void componentExtendsAbstractClass_requiresSuperCallTest() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "abstract class MyComponent {",
            "  private MyComponent() {}",
            "  protected MyComponent(String s, int i, boolean b) {}",
            "  abstract String getString();",
            "}");
    daggerCompilerExtendingComponent(component)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerMyComponent"));
            });
  }

  @Test
  public void componentExtendsAbstractClass_noArgConstructorOmitsSuperCallTest() {
    Source component =
        CompilerTests.javaSource(
            "test.MyComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "abstract class MyComponent {",
            "  MyComponent() {}",
            "  MyComponent(String s) {}",
            "  abstract String getString();",
            "}");
    daggerCompilerExtendingComponent(component)
        .compile(
            subject -> {
              subject.hasErrorCount(0);
              subject.generatedSource(goldenFileRule.goldenSource("test/DaggerMyComponent"));
            });
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
