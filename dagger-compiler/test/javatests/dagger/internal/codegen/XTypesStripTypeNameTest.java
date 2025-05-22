/*
 * Copyright (C) 2023 The Dagger Authors.
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
import static dagger.internal.codegen.extension.DaggerCollectors.onlyElement;
import static dagger.internal.codegen.xprocessing.XTypes.stripVariances;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.util.Source;
import com.squareup.javapoet.TypeName;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XTypesStripTypeNameTest {
  @Test
  public void fooExtendsBar() {
    assertStrippedWildcardTypeNameEquals(
        /* source = */
        CompilerTests.javaSource(
            "Subject",
            "interface Subject {",
            "  Foo<? extends Bar<? extends Baz>> method();",
            "",
            "  interface Foo<T> {}",
            "  interface Bar<T> {}",
            "  interface Baz {}",
            "}"),
        /* strippedTypeName = */ "Foo<Bar<Baz>>");
  }

  @Test
  public void fooSuperBar() {
    assertStrippedWildcardTypeNameEquals(
        /* source = */
        CompilerTests.javaSource(
            "Subject",
            "interface Subject {",
            "  Foo<? super Bar<? super Baz>> method();",
            "",
            "  interface Foo<T> {}",
            "  interface Bar<T> {}",
            "  interface Baz {}",
            "}"),
        /* strippedTypeName = */ "Foo<Bar<Baz>>");
  }

  @Test
  public void multipleParameters() {
    assertStrippedWildcardTypeNameEquals(
        /* source = */
        CompilerTests.javaSource(
            "Subject",
            "interface Subject {",
            "  Foo<Bar<? extends Baz>, Bar<? super Baz>> method();",
            "",
            "  interface Foo<T1, T2> {}",
            "  interface Bar<T> {}",
            "  interface Baz {}",
            "}"),
        /* strippedTypeName = */ "Foo<Bar<Baz>, Bar<Baz>>");
  }

  @Test
  public void multipleParametersSameArgument() {
    assertStrippedWildcardTypeNameEquals(
        /* source = */
        CompilerTests.javaSource(
            "Subject",
            "interface Subject {",
            "  Foo<Bar<? extends Baz>, Bar<? extends Baz>> method();",
            "",
            "  interface Foo<T1, T2> {}",
            "  interface Bar<T> {}",
            "  interface Baz {}",
            "}"),
        /* strippedTypeName = */ "Foo<Bar<Baz>, Bar<Baz>>");
  }

  @Test
  public void multipleParametersCrossReferencing() {
    assertStrippedWildcardTypeNameEquals(
        /* source = */
        CompilerTests.javaSource(
            "Subject",
            "interface Subject {",
            "  Foo<Bar<? extends Baz>, Bar<? extends Bar<? extends Baz>>> method();",
            "",
            "  interface Foo<T1, T2 extends Bar<? extends T1>> {}",
            "  interface Bar<T> {}",
            "  interface Baz {}",
            "}"),
        /* strippedTypeName = */ "Foo<Bar<Baz>, Bar<Bar<Baz>>>");
  }

  @Test
  public void selfReferencing() {
    assertStrippedWildcardTypeNameEquals(
        /* source = */
        CompilerTests.javaSource(
            "Subject",
            "interface Subject {",
            "  <T extends Foo<T>> Foo<T> method();",
            "",
            "  interface Foo<T extends Foo<T>> {}",
            "}"),
        /* strippedTypeName = */ "Foo<T>");
  }

  @Test
  public void arrayType() {
    assertStrippedWildcardTypeNameEquals(
        /* source = */
        CompilerTests.javaSource(
            "Subject",
            "interface Subject {",
            "  Foo<? extends Bar<? extends Baz>>[] method();",
            "",
            "  interface Foo<T> {}",
            "  interface Bar<T> {}",
            "  interface Baz {}",
            "}"),
        /* strippedTypeName = */ "Foo<Bar<Baz>>[]");
  }

  @Test
  public void typeVariableSameVariableName() {
    CompilerTests.invocationCompiler(
        CompilerTests.javaSource(
              "Subject",
              "interface Subject {",
              "  <T extends Bar> Foo<T> method1();",
              "  <T extends Baz> Foo<T> method2();",
              "",
              "  interface Foo<T> {}",
              "  interface Bar {}",
              "  interface Baz {}",
              "}"))
        .compile(
            invocation -> {
              XTypeElement subject = invocation.getProcessingEnv().requireTypeElement("Subject");
              TypeName method1ReturnTypeName =
                  getDeclaredMethod(subject, "method1").getReturnType().getTypeName();
              TypeName method2ReturnTypeName =
                  getDeclaredMethod(subject, "method2").getReturnType().getTypeName();
              assertThat(method1ReturnTypeName).isEqualTo(method2ReturnTypeName);
            });
  }

  private static void assertStrippedWildcardTypeNameEquals(Source source, String strippedTypeName) {
    CompilerTests.invocationCompiler(source)
        .compile(
            invocation -> {
              XTypeElement subject = invocation.getProcessingEnv().requireTypeElement("Subject");
              TypeName returnTypeName =
                  getDeclaredMethod(subject, "method").getReturnType().getTypeName();
              assertThat(stripVariances(returnTypeName).toString().replace("Subject.", ""))
                  .isEqualTo(strippedTypeName);
            }
        );
  }

  private static XMethodElement getDeclaredMethod(XTypeElement typeElement, String jvmName) {
    return typeElement.getDeclaredMethods().stream()
        .filter(method -> method.getJvmName().contentEquals(jvmName))
        .collect(onlyElement());
  }
}
