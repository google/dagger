/*
 * Copyright (C) 2014 Google, Inc.
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
package dagger.internal.codegen.writer;

import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class JavaWriterTest {
  @Test public void referencedAndDeclaredSimpleName() {
    JavaWriter javaWriter = JavaWriter.inPackage("test");
    ClassWriter topClass = javaWriter.addClass("Top");
    topClass.addNestedClass("Middle").addNestedClass("Bottom");
    topClass.addField(ClassName.create("some.other.pkg", "Bottom"), "field");
    assertThat(topClass.toString()).doesNotContain("import some.other.pkg.Bottom;");
  }

  @Test public void zeroImportsSingleNewline() {
    JavaWriter javaWriter = JavaWriter.inPackage("test");
    javaWriter.addClass("Top");

    String expected = ""
        + "package test;\n"
        + "\n"
        + "class Top {";

    assertThat(javaWriter.toString()).startsWith(expected);
  }

  @Test public void newlineBetweenImports() {
    JavaWriter javaWriter = JavaWriter.inPackage("test");
    ClassWriter topClass = javaWriter.addClass("Top");
    topClass.addField(Executor.class, "executor");

    String expected = ""
        + "package test;\n"
        + "\n"
        + "import java.util.concurrent.Executor;\n"
        + "\n"
        + "class Top {";

    assertThat(javaWriter.toString()).startsWith(expected);
  }

  @Test public void newlinesBetweenTypes() {
    JavaWriter javaWriter = JavaWriter.inPackage("test");
    javaWriter.addClass("Top");
    javaWriter.addClass("Middle");
    javaWriter.addClass("Bottom");

    String expected = ""
        + "package test;\n"
        + "\n"
        + "class Top {}\n"
        + "\n"
        + "class Middle {}\n"
        + "\n"
        + "class Bottom {}\n";

    assertThat(javaWriter.toString()).isEqualTo(expected);
  }
}
