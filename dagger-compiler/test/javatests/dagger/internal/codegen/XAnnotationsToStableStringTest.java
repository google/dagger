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

import androidx.room.compiler.processing.XAnnotation;
import androidx.room.compiler.processing.XProcessingEnv;
import androidx.room.compiler.processing.XTypeElement;
import com.google.testing.compile.CompilationRule;
import com.squareup.javapoet.ClassName;
import dagger.Component;
import dagger.internal.codegen.javac.JavacPluginModule;
import dagger.internal.codegen.xprocessing.XAnnotations;
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
public class XAnnotationsToStableStringTest {
  static final class Foo {}

  enum MyEnum {
    V1,
    V2
  }

  @interface SingleValueWithCustomNameAnnotation {
    Class<?> classValue();
  }

  @interface SingleValueWithDefaultAnnotation {
    Class<?> value() default Object.class;
  }

  @interface SingleValueAnnotation {
    Class<?> value();
  }

  @interface MultiValueAnnotation {
    MyEnum enumValue();

    Class<?> classValue();

    boolean booleanValue();

    byte byteValue();

    char charValue();

    int intValue();

    long longValue();

    short shortValue();

    float floatValue();

    double doubleValue();

    String stringValue();

    String stringWithDefaultValue() default "Default value";

    String[] stringWithMultipleArrayValue();

    String[] stringWithSingleArrayValue();

    SingleValueAnnotation[] singleValueAnnotations();
  }

  @SingleValueAnnotation(value = Foo.class)
  @SingleValueWithDefaultAnnotation
  @SingleValueWithCustomNameAnnotation(classValue = Foo.class)
  @MultiValueAnnotation(
      // Note: the value orders are purposely randomized for this test
      doubleValue = 13.2,
      longValue = 7L,
      stringWithSingleArrayValue = {"STRING_VALUE1"},
      byteValue = 3,
      enumValue = MyEnum.V1,
      charValue = 'c',
      stringWithMultipleArrayValue = {"STRING_VALUE1", "STRING_VALUE2"},
      shortValue = 9,
      classValue = Foo.class,
      stringValue = "STRING_VALUE1",
      booleanValue = false,
      floatValue = 11.1f,
      singleValueAnnotations = {
        @SingleValueAnnotation(Object.class),
        @SingleValueAnnotation(Foo.class),
        @SingleValueAnnotation(String.class)
      },
      intValue = 5)
  interface Usage {}

  @Rule public CompilationRule compilationRule = new CompilationRule();

  @Inject XProcessingEnv processingEnv;

  private final String testName = getClass().getCanonicalName();

  @Before
  public void setUp() {
    TestComponent.create(compilationRule.getElements(), compilationRule.getTypes()).inject(this);
  }

  @Test
  public void multiValueAnnotationTest() {
    XTypeElement typeElement = processingEnv.requireTypeElement(Usage.class.getCanonicalName());
    XAnnotation annotation = typeElement.getAnnotation(ClassName.get(MultiValueAnnotation.class));
    assertThat(XAnnotations.toStableString(annotation))
        .isEqualTo(
            String.format(
                "@%1$s.MultiValueAnnotation("
                    + "enumValue=V1, "
                    + "classValue=%1$s.Foo, "
                    + "booleanValue=false, "
                    + "byteValue=3, "
                    + "charValue='c', "
                    + "intValue=5, "
                    + "longValue=7, "
                    + "shortValue=9, "
                    + "floatValue=11.1, "
                    + "doubleValue=13.2, "
                    + "stringValue=\"STRING_VALUE1\", "
                    + "stringWithDefaultValue=\"Default value\", "
                    + "stringWithMultipleArrayValue={\"STRING_VALUE1\", \"STRING_VALUE2\"}, "
                    + "stringWithSingleArrayValue={\"STRING_VALUE1\"}, "
                    + "singleValueAnnotations={"
                    + "@%1$s.SingleValueAnnotation(java.lang.Object), "
                    + "@%1$s.SingleValueAnnotation(%1$s.Foo), "
                    + "@%1$s.SingleValueAnnotation(java.lang.String)"
                    + "})",
                testName));
  }

  @Test
  public void singleValueAnnotationTest() {
    XTypeElement typeElement = processingEnv.requireTypeElement(Usage.class.getCanonicalName());
    XAnnotation annotation = typeElement.getAnnotation(ClassName.get(SingleValueAnnotation.class));
    assertThat(XAnnotations.toStableString(annotation))
        .isEqualTo(String.format("@%1$s.SingleValueAnnotation(%1$s.Foo)", testName));
  }

  @Test
  public void singleValueWithDefaultAnnotationTest() {
    XTypeElement typeElement = processingEnv.requireTypeElement(Usage.class.getCanonicalName());
    XAnnotation annotation =
        typeElement.getAnnotation(ClassName.get(SingleValueWithDefaultAnnotation.class));
    assertThat(XAnnotations.toStableString(annotation))
        .isEqualTo(
            String.format("@%1$s.SingleValueWithDefaultAnnotation(java.lang.Object)", testName));
  }

  @Test
  public void singleValueWithCustomNameAnnotationTest() {
    XTypeElement typeElement = processingEnv.requireTypeElement(Usage.class.getCanonicalName());
    XAnnotation annotation =
        typeElement.getAnnotation(ClassName.get(SingleValueWithCustomNameAnnotation.class));
    assertThat(XAnnotations.toStableString(annotation))
        .isEqualTo(
            String.format(
                "@%1$s.SingleValueWithCustomNameAnnotation(classValue=%1$s.Foo)", testName));
  }

  @Singleton
  @Component(modules = JavacPluginModule.class)
  interface TestComponent {
    static TestComponent create(Elements elements, Types types) {
      return DaggerXAnnotationsToStableStringTest_TestComponent.builder()
          .javacPluginModule(new JavacPluginModule(elements, types))
          .build();
    }

    void inject(XAnnotationsToStableStringTest test);
  }
}
