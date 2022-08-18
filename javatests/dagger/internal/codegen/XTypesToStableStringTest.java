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

package dagger.internal.codegen;

import static com.google.common.truth.Truth.assertThat;
import static dagger.internal.codegen.extension.DaggerCollectors.onlyElement;
import static dagger.internal.codegen.xprocessing.XElements.getSimpleName;

import androidx.room.compiler.processing.XMethodElement;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.testing.compile.CompilationRule;
import dagger.Component;
import dagger.internal.codegen.javac.JavacPluginModule;
import dagger.internal.codegen.xprocessing.XTypes;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class XTypesToStableStringTest {
  static class Foo {}

  static class ParameterizedType<T> {}

  static class MultiParameterizedType<T1, T2 extends ParameterizedType<T1>> {}

  interface Usage {
    Foo foo();

    ParameterizedType<Foo> parameterizedType();

    ParameterizedType<? extends Foo> parameterizedTypeWithWildcard();

    <T extends Foo> ParameterizedType<T> parameterizedTypeWithTypeVariable();

    <T extends ParameterizedType<T>> ParameterizedType<T> parameterizedTypeWithSelfReference();

    MultiParameterizedType<Foo, ParameterizedType<Foo>> multiParameterizedType();
  }

  @Rule public CompilationRule compilationRule = new CompilationRule();

  private final String testName = getClass().getCanonicalName();

  @Inject XProcessingEnv processingEnv;

  @Before
  public void setUp() {
    TestComponent.create(compilationRule.getElements(), compilationRule.getTypes()).inject(this);
  }

  @Test
  public void simpleTypeTest() {
    XTypeElement typeElement = processingEnv.requireTypeElement(Usage.class.getCanonicalName());
    XMethodElement method = getDeclaredMethod(typeElement, "foo");
    assertThat(XTypes.toStableString(method.getReturnType()))
        .isEqualTo(String.format("%1$s.Foo", testName));
  }

  @Test
  public void parameterizedTypeTest() {
    XTypeElement typeElement = processingEnv.requireTypeElement(Usage.class.getCanonicalName());
    XMethodElement method = getDeclaredMethod(typeElement, "parameterizedType");
    assertThat(XTypes.toStableString(method.getReturnType()))
        .isEqualTo(String.format("%1$s.ParameterizedType<%1$s.Foo>", testName));
  }

  @Test
  public void parameterizedTypeWithWildcardTest() {
    XTypeElement typeElement = processingEnv.requireTypeElement(Usage.class.getCanonicalName());
    XMethodElement method = getDeclaredMethod(typeElement, "parameterizedTypeWithWildcard");
    assertThat(XTypes.toStableString(method.getReturnType()))
        .isEqualTo(String.format("%1$s.ParameterizedType<? extends %1$s.Foo>", testName));
  }

  @Test
  public void parameterizedTypeWithTypeVariableTest() {
    XTypeElement typeElement = processingEnv.requireTypeElement(Usage.class.getCanonicalName());
    XMethodElement method = getDeclaredMethod(typeElement, "parameterizedTypeWithTypeVariable");
    assertThat(XTypes.toStableString(method.getReturnType()))
        .isEqualTo(String.format("%1$s.ParameterizedType<T>", testName));
  }

  @Test
  public void parameterizedTypeWithSelfReferenceTest() {
    XTypeElement typeElement = processingEnv.requireTypeElement(Usage.class.getCanonicalName());
    XMethodElement method = getDeclaredMethod(typeElement, "parameterizedTypeWithSelfReference");
    assertThat(XTypes.toStableString(method.getReturnType()))
        .isEqualTo(String.format("%1$s.ParameterizedType<T>", testName));
  }

  @Test
  public void multiParameterizedTypeTest() {
    XTypeElement typeElement = processingEnv.requireTypeElement(Usage.class.getCanonicalName());
    XMethodElement method = getDeclaredMethod(typeElement, "multiParameterizedType");
    assertThat(XTypes.toStableString(method.getReturnType()))
        .isEqualTo(
            String.format(
                "%1$s.MultiParameterizedType<%1$s.Foo,%1$s.ParameterizedType<%1$s.Foo>>",
                testName));
  }

  private XMethodElement getDeclaredMethod(XTypeElement typeElement, String methodName) {
    return typeElement.getDeclaredMethods().stream()
        .filter(method -> getSimpleName(method).contentEquals(methodName))
        .collect(onlyElement());
  }

  @Singleton
  @Component(modules = JavacPluginModule.class)
  interface TestComponent {
    static TestComponent create(Elements elements, Types types) {
      return DaggerXTypesToStableStringTest_TestComponent.builder()
          .javacPluginModule(new JavacPluginModule(elements, types))
          .build();
    }

    void inject(XTypesToStableStringTest test);
  }
}
