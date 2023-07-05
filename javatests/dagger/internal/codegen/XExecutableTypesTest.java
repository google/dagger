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

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XTypeElement;
import androidx.room.compiler.processing.util.Source;
import dagger.internal.codegen.xprocessing.XExecutableTypes;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XExecutableTypesTest {

  @Test
  public void subsignatureMethodNamesAreIgnored() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  void p(String s) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar {",
            "  void q(String s) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isTrue();
              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isTrue();
            });
  }

  @Test
  public void subsignatureReturnTypesAreIgnored() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  List m(Collection c) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar {",
            "  Set m(Collection c) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isTrue();
              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isTrue();
            });
  }

  @Test
  public void subsignatureStaticIsIgnored() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  void m(Collection i) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar {",
            "  static void m(Collection i) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isTrue();
              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isTrue();
            });
  }

  @Test
  public void subsignatureWithAndWithoutTypeArguments() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  <T> void m(Collection<T> i) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar {",
            "  void m(Collection i) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isTrue();
              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isFalse();
            });
  }

  @Test
  public void subsignatureDifferentNumberOfTypeArguments() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  <T, Q> void m(Collection i) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar {",
            "  <T> void m(Collection i) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isFalse();
              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isFalse();
            });
  }

  @Test
  public void subsignatureDifferentTypeArgumentBounds() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  <T extends Foo> void m(Collection<T> i) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar {",
            "  <T> void m(Collection<T> i) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isFalse();
              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isFalse();
            });
  }

  @Test
  public void subsignatureWithGenericClasses() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  void m(Collection i) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar {",
            "  void m(Collection<String> i) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isFalse();
              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isTrue();
            });
  }

  @Test
  public void subsignatureSameSignature() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  <T> List<T> toList(Collection<T> c) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar extends Foo {",
            "  <T> List<T> toList(Collection<T> c) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isTrue();
              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isTrue();
            });
  }

  @Test
  public void subsignatureSameSignatureUnrelatedClasses() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  <T> List<T> toList(Collection<T> c) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar {",
            "  <T> List<T> toList(Collection<T> c) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isTrue();
              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isTrue();
            });
  }

  @Test
  public void subsignatureWildcards() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  void toList(Collection<Object> c) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar {",
            "  void toList(Collection<? extends Foo> c) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isFalse();
              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isFalse();
            });
  }

  @Test
  public void subsignatureBounded() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  void toList(Collection<Foo> c) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar {",
            "  <T extends Foo> void toList(Collection<T> c) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isFalse();
              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isFalse();
            });
  }

  @Test
  public void subsignatureGenericMethodAndWildcard() {
    Source foo =
        CompilerTests.javaSource(
            "test.Foo",
            "package test;",
            "import java.util.*;",
            "class Foo {",
            "  <T> List<T> toList(Collection<T> c) { throw new RuntimeException(); }",
            "}");
    Source bar =
        CompilerTests.javaSource(
            "test.Bar",
            "package test;",
            "import java.util.*;",
            "class Bar {",
            "  <T extends Foo> List<T> toList(Collection<T> c) { throw new RuntimeException(); }",
            "}");
    CompilerTests.invocationCompiler(foo, bar)
        .compile(
            invocation -> {
              XTypeElement fooType = invocation.getProcessingEnv().requireTypeElement("test.Foo");
              XMethodElement m1 = fooType.getDeclaredMethods().get(0);

              XTypeElement barType = invocation.getProcessingEnv().requireTypeElement("test.Bar");
              XMethodElement m2 = barType.getDeclaredMethods().get(0);

              assertThat(XExecutableTypes.isSubsignature(m2, m1)).isFalse();
              assertThat(XExecutableTypes.isSubsignature(m1, m2)).isFalse();
            });
  }
}
