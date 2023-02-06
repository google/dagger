/*
 * Copyright (C) 2022 The Dagger Authors.
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
import static dagger.internal.codegen.extension.DaggerStreams.toImmutableList;

import androidx.room.compiler.processing.XElementKt;
import androidx.room.compiler.processing.util.Source;
import androidx.room.compiler.processing.util.XTestInvocation;
import com.google.common.collect.ImmutableList;
import dagger.internal.codegen.xprocessing.XElements;
import dagger.testing.compile.CompilerTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KspDescriptorTest {
  private static final Source describeAnnotationSrc =
      Source.Companion.kotlin(
          "Describe.kt",
          String.join(
              "\n",
              "package test",
              "",
              "import kotlin.annotation.Target",
              "import kotlin.annotation.AnnotationTarget.FIELD",
              "import kotlin.annotation.AnnotationTarget.FUNCTION",
              "",
              "@Target(FIELD, FUNCTION)",
              "annotation class Describe"));

  @Test
  public void testFieldDescriptor() {
    Source dummySrc =
        CompilerTests.kotlinSource(
            "DummyClass.kt",
            "package test",
            "",
            "class DummyClass {",
            " @Describe val field1: Int = 0",
            " @Describe val field2: String = \"\"",
            " @Describe val field3: List<String> = listOf()",
            "}");

    CompilerTests.invocationCompiler(dummySrc, describeAnnotationSrc)
        .compile(
            invocation -> {
              if (invocation.isKsp()) {
                assertThat(getFieldDescriptors(invocation))
                    .containsExactly(
                        "field1:I", "field2:Lkotlin/String;", "field3:Lkotlin/collections/List;");
              } else {
                assertThat(getFieldDescriptors(invocation))
                    .containsExactly(
                        "field1:I", "field2:Ljava/lang/String;", "field3:Ljava/util/List;");
              }
            });
  }

  @Test
  public void testFieldDescriptorWithJavaSource() {
    Source dummySrc =
        CompilerTests.javaSource(
            "test.DummyClass",
            "package test;",
            "",
            "import java.util.List;",
            "",
            "class DummyClass {",
            " @Describe int field1;",
            " @Describe String field2;",
            " @Describe List<String> field3;",
            "}");

    CompilerTests.invocationCompiler(dummySrc, describeAnnotationSrc)
        .compile(
            invocation -> {
              if (invocation.isKsp()) {
                assertThat(getFieldDescriptors(invocation))
                    .containsExactly(
                        "field1:I",
                        "field2:Lkotlin/String;",
                        "field3:Lkotlin/collections/MutableList;");
              } else {
                assertThat(getFieldDescriptors(invocation))
                    .containsExactly(
                        "field1:I", "field2:Ljava/lang/String;", "field3:Ljava/util/List;");
              }
            });
  }

  @Test
  public void testMethodDescriptor() {
    Source dummySrc =
        CompilerTests.kotlinSource(
            "DummyClass.kt",
            "package test;",
            "",
            "class DummyClass {",
            " @Describe fun method1() {}",
            " @Describe fun method2(yesOrNo: Boolean, number: Int) {}",
            " @Describe fun method3(letter: Char) {}",
            " @Describe fun method4(realNumber1: Double, realNumber2: Float) {}",
            " @Describe fun method5(bigNumber1: Long, littleNumber: Short) {}",
            " @Describe fun method6(somthing: Any) {}",
            " @Describe fun method7(): Any? = null",
            " @Describe fun method8(): Map<String, Any> = mapOf()",
            "}");

    CompilerTests.invocationCompiler(dummySrc, describeAnnotationSrc)
        .compile(
            invocation -> {
              if (invocation.isKsp()) {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1()V",
                        "method2(ZI)V",
                        "method3(C)V",
                        "method4(DF)V",
                        "method5(JS)V",
                        "method6(Lkotlin/Any;)V",
                        "method7()Lkotlin/Any;",
                        "method8()Lkotlin/collections/Map;");
              } else {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1()V",
                        "method2(ZI)V",
                        "method3(C)V",
                        "method4(DF)V",
                        "method5(JS)V",
                        "method6(Ljava/lang/Object;)V",
                        "method7()Ljava/lang/Object;",
                        "method8()Ljava/util/Map;");
              }
            });
  }

  @Test
  public void methodDescriptorWithJavaSource() {
    Source dummySrc =
        CompilerTests.javaSource(
            "test.DummyClass",
            "package test;",
            "",
            "import java.util.ArrayList;",
            "import java.util.List;",
            "import java.util.Map;",
            "",
            "class DummyClass {",
            " @Describe void method1() {}",
            " @Describe void method2(boolean yesOrNo, int number) {}",
            " @Describe void method3(char letter) {}",
            " @Describe void method4(double realNumber1, float realNumber2) {}",
            " @Describe void method5(long bigNumber1, short littleNumber) {}",
            " @Describe void method6(Object somthing) {}",
            " @Describe Object method7() { return null; }",
            " @Describe List<String> method8(ArrayList<Integer> list) { return null; }",
            " @Describe Map<String, Object> method9() { return null; }",
            "}");

    CompilerTests.invocationCompiler(dummySrc, describeAnnotationSrc)
        .compile(
            invocation -> {
              if (invocation.isKsp()) {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1()V",
                        "method2(ZI)V",
                        "method3(C)V",
                        "method4(DF)V",
                        "method5(JS)V",
                        "method6(Lkotlin/Any;)V",
                        "method7()Lkotlin/Any;",
                        "method8(Ljava/util/ArrayList;)Lkotlin/collections/MutableList;",
                        "method9()Lkotlin/collections/MutableMap;");
              } else {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1()V",
                        "method2(ZI)V",
                        "method3(C)V",
                        "method4(DF)V",
                        "method5(JS)V",
                        "method6(Ljava/lang/Object;)V",
                        "method7()Ljava/lang/Object;",
                        "method8(Ljava/util/ArrayList;)Ljava/util/List;",
                        "method9()Ljava/util/Map;");
              }
            });
  }

  @Test
  public void testArraysMethodDescriptor() {
    Source customClassSrc =
        CompilerTests.kotlinSource("CustomClass.kt", "package test", "class CustomClass {}");

    Source dummySrc =
        CompilerTests.kotlinSource(
            "DummyClass.kt",
            "package test",
            "",
            "class DummyClass {",
            " @Describe fun method1(param: Array<CustomClass>) {}",
            " @Describe fun method2(): Array<CustomClass> = arrayOf()",
            " @Describe fun method3(param: Array<Int>) {}",
            "}");

    CompilerTests.invocationCompiler(customClassSrc, dummySrc, describeAnnotationSrc)
        .compile(
            invocation -> {
              if (invocation.isKsp()) {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1([Ltest/CustomClass;)V",
                        "method2()[Ltest/CustomClass;",
                        "method3([Lkotlin/Int;)V");
              } else {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1([Ltest/CustomClass;)V",
                        "method2()[Ltest/CustomClass;",
                        "method3([Ljava/lang/Integer;)V");
              }
            });
  }

  @Test
  public void testArraysMethodDescriptorJavaSource() {
    Source customClassSrc =
        CompilerTests.kotlinSource("CustomClass.kt", "package test", "class CustomClass {}");

    Source dummySrc =
        CompilerTests.javaSource(
            "test.DummyClass",
            "package test;",
            "",
            "class DummyClass {",
            " @Describe void method1(CustomClass [] param) {}",
            " @Describe CustomClass[] method2() { return null; }",
            " @Describe void method3(int[] array) {}",
            " @Describe void method4(int... array) {}",
            "}");

    CompilerTests.invocationCompiler(customClassSrc, dummySrc, describeAnnotationSrc)
        .compile(
            invocation -> {
              if (invocation.isKsp()) {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1([Ltest/CustomClass;)V",
                        "method2()[Ltest/CustomClass;",
                        "method3([I)V",
                        "method4([I)V");
              } else {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1([Ltest/CustomClass;)V",
                        "method2()[Ltest/CustomClass;",
                        "method3([I)V",
                        "method4([I)V");
              }
            });
  }

  @Test
  public void testInnerClassMethodDescriptorJavaSource() {
    Source customClassSrc =
        CompilerTests.javaSource(
            "test.CustomClass",
            "package test;",
            "",
            "public class CustomClass {",
            " class InnerData {}",
            " static class StaticInnerData {}",
            " enum EnumData { VALUE1, VALUE2 }",
            "}");

    Source dummySrc =
        CompilerTests.javaSource(
            "test.DummyClass",
            "package test;",
            "",
            "class DummyClass {",
            " @Describe void method1(CustomClass.InnerData data) {}",
            " @Describe void method2(CustomClass.StaticInnerData data) {}",
            " @Describe void method3(CustomClass.EnumData data) {}",
            " @Describe CustomClass.StaticInnerData method4() { return null; }",
            "}");

    CompilerTests.invocationCompiler(customClassSrc, dummySrc, describeAnnotationSrc)
        .compile(
            invocation ->
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1(Ltest/CustomClass$InnerData;)V",
                        "method2(Ltest/CustomClass$StaticInnerData;)V",
                        "method3(Ltest/CustomClass$EnumData;)V",
                        "method4()Ltest/CustomClass$StaticInnerData;"));
  }

  @Test
  public void testNestedFieldDescriptor() {
    Source dummySrc =
        CompilerTests.kotlinSource(
            "DummyClass.kt",
            "package test",
            "",
            "class DummyClass {",
            "  class Nested1 {",
            "   class Nested2 {",
            "     @Describe val field1: DummyClass? = null",
            "     @Describe val field2: Nested2? = null",
            "   }",
            " }",
            "}");

    CompilerTests.invocationCompiler(dummySrc, describeAnnotationSrc)
        .compile(
            invocation -> {
              if (invocation.isKsp()) {
                assertThat(getFieldDescriptors(invocation))
                    .containsExactly(
                        "field1:Ltest/DummyClass;", "field2:Ltest/DummyClass$Nested1$Nested2;");
              } else {
                assertThat(getFieldDescriptors(invocation))
                    .containsExactly(
                        "field1:Ltest/DummyClass;", "field2:Ltest/DummyClass$Nested1$Nested2;");
              }
            });
  }

  @Test
  public void testGenericTypeMethodDescriptor() {
    Source dummySrc =
        CompilerTests.kotlinSource(
            "DummyClass.kt",
            "package test",
            "",
            "public class DummyClass<T> {",
            " @Describe fun method1(something: T) {}",
            " @Describe fun method2(): T? = null",
            " @Describe fun <O : String> method3(): List<O> = listOf()",
            " @Describe fun method4(): Map<T, String> = mapOf()",
            " @Describe fun method5(): List<Map<String, T>> = listOf()",
            " @Describe fun <I, O : I> method6(input: I): O? = null",
            " @Describe fun <I, O : String> method7(input: I): O? = null",
            " @Describe fun <P> method8(): P? where P : Collection<String>, P : Comparable<String> "
                + " = null",
            "}");

    CompilerTests.invocationCompiler(dummySrc, describeAnnotationSrc)
        .compile(
            invocation -> {
              if (invocation.isKsp()) {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1(Lkotlin/Any;)V",
                        "method2()Lkotlin/Any;",
                        "method3()Lkotlin/collections/List;",
                        "method4()Lkotlin/collections/Map;",
                        "method5()Lkotlin/collections/List;",
                        "method6(Lkotlin/Any;)Lkotlin/Any;",
                        "method7(Lkotlin/Any;)Lkotlin/String;",
                        "method8()Lkotlin/collections/Collection;");
              } else {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1(Ljava/lang/Object;)V",
                        "method2()Ljava/lang/Object;",
                        "method3()Ljava/util/List;",
                        "method4()Ljava/util/Map;",
                        "method5()Ljava/util/List;",
                        "method6(Ljava/lang/Object;)Ljava/lang/Object;",
                        "method7(Ljava/lang/Object;)Ljava/lang/String;",
                        "method8()Ljava/util/Collection;");
              }
            });
  }

  @Test
  public void testGenericTypeMethodDescriptorWithJavaSource() {
    Source dummySrc =
        CompilerTests.javaSource(
            "test.DummyClass",
            "package test;",
            "",
            "import java.util.ArrayList;",
            "import java.util.Collection;",
            "import java.util.List;",
            "import java.util.Map;",
            "import java.util.Set;",
            "",
            "public class DummyClass<T> {",
            " @Describe void method1(T something) {}",
            " @Describe T method2() { return null; }",
            " @Describe List<? extends String> method3() { return null; }",
            " @Describe Map<T, String> method4() { return null; }",
            " @Describe ArrayList<Map<String, T>> method5() { return null; }",
            " @Describe <I, O extends I> O method6(I input) { return null; }",
            " @Describe static <I, O extends String> O method7(I input) { return null; }",
            " @Describe static <P extends Collection<String> & Comparable<String>> P method8() {",
            "   return null;",
            " }",
            " @Describe static <P extends String & List<Character>> P method9() { return null; }",
            "}");

    CompilerTests.invocationCompiler(dummySrc, describeAnnotationSrc)
        .compile(
            invocation -> {
              if (invocation.isKsp()) {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1(Lkotlin/Any;)V",
                        "method2()Lkotlin/Any;",
                        "method3()Lkotlin/collections/MutableList;",
                        "method4()Lkotlin/collections/MutableMap;",
                        "method5()Ljava/util/ArrayList;",
                        "method6(Lkotlin/Any;)Lkotlin/Any;",
                        "method7(Lkotlin/Any;)Lkotlin/String;",
                        "method8()Lkotlin/collections/MutableCollection;",
                        "method9()Lkotlin/String;");
              } else {
                assertThat(getMethodDescriptors(invocation))
                    .containsExactly(
                        "method1(Ljava/lang/Object;)V",
                        "method2()Ljava/lang/Object;",
                        "method3()Ljava/util/List;",
                        "method4()Ljava/util/Map;",
                        "method5()Ljava/util/ArrayList;",
                        "method6(Ljava/lang/Object;)Ljava/lang/Object;",
                        "method7(Ljava/lang/Object;)Ljava/lang/String;",
                        "method8()Ljava/util/Collection;",
                        "method9()Ljava/lang/String;");
              }
            });
  }

  private static ImmutableList<String> getFieldDescriptors(XTestInvocation invocation) {
    return invocation.getRoundEnv().getElementsAnnotatedWith("test.Describe").stream()
        .filter(XElementKt::isField)
        .map(XElements::asField)
        .map(XElements::getFieldDescriptor)
        .collect(toImmutableList());
  }

  private static ImmutableList<String> getMethodDescriptors(XTestInvocation invocation) {
    return invocation.getRoundEnv().getElementsAnnotatedWith("test.Describe").stream()
        .filter(XElementKt::isMethod)
        .map(XElements::asMethod)
        .map(XElements::getMethodDescriptor)
        .collect(toImmutableList());
  }
}
