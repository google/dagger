/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.hilt.processor.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.compile.CompilationRule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ElementDescriptorsTest {

  @Rule public CompilationRule compilation = new CompilationRule();

  static class TestClassA<T> {
    int field1;
    String field2;
    T field3;
    List<String> field4;
  }

  @Test
  public void fieldDescriptor() {
    assertThat(getFieldDescriptors(TestClassA.class.getCanonicalName()))
        .containsExactly(
            "field1:I",
            "field2:Ljava/lang/String;",
            "field3:Ljava/lang/Object;",
            "field4:Ljava/util/List;");
  }

  static class TestClassB<T> {
    void method1(boolean yesOrNo, int number) {}

    byte method2(char letter) {
      return 0;
    }

    void method3(double realNumber1, float realNummber2) {}

    void method4(long bigNumber, short littlerNumber) {}
  }

  @Test
  public void methodDescriptor_primitives() {
    assertThat(getMethodDescriptors(TestClassB.class.getCanonicalName()))
        .containsExactly("method1(ZI)V", "method2(C)B", "method3(DF)V", "method4(JS)V");
  }

  static class TestClassC<T> {
    void method1(Object something) {}

    Object method2() {
      return null;
    }

    List<String> method3(ArrayList<Integer> list) {
      return null;
    }

    Map<String, Object> method4() {
      return null;
    }
  }

  @Test
  public void methodDescriptor_javaTypes() {
    assertThat(getMethodDescriptors(TestClassC.class.getCanonicalName()))
        .containsExactly(
            "method1(Ljava/lang/Object;)V",
            "method2()Ljava/lang/Object;",
            "method3(Ljava/util/ArrayList;)Ljava/util/List;",
            "method4()Ljava/util/Map;");
  }

  static class TestClassD<T> {
    void method1(TestDataClass data) {}

    TestDataClass method2() {
      return null;
    }
  }

  @Test
  public void methodDescriptor_testTypes() {
    assertThat(getMethodDescriptors(TestClassD.class.getCanonicalName()))
        .containsExactly(
            "method1(Ldagger/hilt/processor/internal/TestDataClass;)V",
            "method2()Ldagger/hilt/processor/internal/TestDataClass;");
  }

  static class TestClassE<T> {
    void method1(TestDataClass[] data) {}

    TestDataClass[] method2() {
      return null;
    }

    void method3(int[] array) {}

    void method4(int... array) {}
  }

  @Test
  public void methodDescriptor_arrays() {
    assertThat(getMethodDescriptors(TestClassE.class.getCanonicalName()))
        .containsExactly(
            "method1([Ldagger/hilt/processor/internal/TestDataClass;)V",
            "method2()[Ldagger/hilt/processor/internal/TestDataClass;",
            "method3([I)V",
            "method4([I)V");
  }

  static class TestClassF<T> {
    void method1(TestDataClass.MemberInnerData data) {}

    void method2(TestDataClass.StaticInnerData data) {}

    void method3(TestDataClass.EnumData enumData) {}

    TestDataClass.StaticInnerData method4() {
      return null;
    }
  }

  @Test
  public void methodDescriptor_innerTestType() {
    assertThat(getMethodDescriptors(TestClassF.class.getCanonicalName()))
        .containsExactly(
            "method1(Ldagger/hilt/processor/internal/TestDataClass$MemberInnerData;)V",
            "method2(Ldagger/hilt/processor/internal/TestDataClass$StaticInnerData;)V",
            "method3(Ldagger/hilt/processor/internal/TestDataClass$EnumData;)V",
            "method4()Ldagger/hilt/processor/internal/TestDataClass$StaticInnerData;");
  }

  @SuppressWarnings("TypeParameterUnusedInFormals")
  static class TestClassG<T> {
    void method1(T something) {}

    T method2() {
      return null;
    }

    List<? extends String> method3() {
      return null;
    }

    Map<T, String> method4() {
      return null;
    }

    ArrayList<Map<T, String>> method5() {
      return null;
    }

    static <I, O extends I> O method6(I input) {
      return null;
    }

    static <I, O extends String> O method7(I input) {
      return null;
    }

    static <P extends Collection<String> & Comparable<String>> P method8() {
      return null;
    }

    static <P extends String & List<Character>> P method9() {
      return null;
    }
  }

  @Test
  public void methodDescriptor_erasure() {
    assertThat(getMethodDescriptors(TestClassG.class.getCanonicalName()))
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

  private Set<String> getFieldDescriptors(String className) {
    TypeElement testElement = compilation.getElements().getTypeElement(className);
    return ElementFilter.fieldsIn(testElement.getEnclosedElements()).stream()
        .map(ElementDescriptors::getFieldDescriptor)
        .collect(Collectors.toSet());
  }

  private Set<String> getMethodDescriptors(String className) {
    TypeElement testElement = compilation.getElements().getTypeElement(className);
    return ElementFilter.methodsIn(testElement.getEnclosedElements()).stream()
        .map(ElementDescriptors::getMethodDescriptor)
        .collect(Collectors.toSet());
  }
}

@SuppressWarnings("ClassCanBeStatic")
class TestDataClass {
  class MemberInnerData {}

  static class StaticInnerData {}

  enum EnumData {
    VALUE1,
    VALUE2
  }
}
