/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.processor.internal.originatingelement;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.hilt.android.testing.compile.HiltCompilerTests.compiler;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OriginatingElementProcessorTest {

  @Test
  public void originatingElementOnInnerClass_fails() {
    JavaFileObject outer1 =
        JavaFileObjects.forSourceLines(
            "test.Outer1",
            "package test;",
            "",
            "class Outer1 {}");
    JavaFileObject outer2 =
        JavaFileObjects.forSourceLines(
            "test.Outer2",
            "package test;",
            "",
            "import dagger.hilt.codegen.OriginatingElement;",
            "",
            "class Outer2 {",
            "  @OriginatingElement(topLevelClass = Outer1.class)",
            "  static class Inner {}",
            "}");
    Compilation compilation = compiler().compile(outer1, outer2);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "@OriginatingElement should only be used to annotate top-level types, but found: "
            + "test.Outer2.Inner");
  }

  @Test
  public void originatingElementValueWithInnerClass_fails() {
    JavaFileObject outer1 =
        JavaFileObjects.forSourceLines(
            "test.Outer1",
            "package test;",
            "",
            "class Outer1 {",
            "  static class Inner {}",
            "}");
    JavaFileObject outer2 =
        JavaFileObjects.forSourceLines(
            "test.Outer2",
            "package test;",
            "",
            "import dagger.hilt.codegen.OriginatingElement;",
            "",
            "@OriginatingElement(topLevelClass = Outer1.Inner.class)",
            "class Outer2 {}");
    Compilation compilation = compiler().compile(outer1, outer2);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "OriginatingElement.topLevelClass value should be a top-level class, but found: "
            + "test.Outer1.Inner");
  }
}
