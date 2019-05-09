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

package dagger.internal.codegen;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

import com.google.auto.common.AnnotationMirrors;
import com.google.testing.compile.CompilationRule;
import dagger.internal.codegen.langmodel.DaggerElements;
import dagger.internal.codegen.langmodel.DaggerTypes;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.lang.model.element.AnnotationMirror;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link TypeProtoConverter}. */
@RunWith(JUnit4.class)
public class AnnotationProtoConverterTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  private DaggerElements elements;
  private DaggerTypes types;
  private AnnotationProtoConverter annotationProtoConverter;

  @Before
  public void setUp() {
    this.elements = new DaggerElements(compilationRule.getElements(), compilationRule.getTypes());
    this.types = new DaggerTypes(compilationRule.getTypes(), elements);
    this.annotationProtoConverter =
        new AnnotationProtoConverter(new TypeProtoConverter(types, elements));
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface TestAnnotation {
    byte b();
    boolean bool();
    short s();
    char c();
    int i();
    long l();
    double d();
    float f();

    String string();
    RetentionPolicy enumValue();
    Class<?> classValue();
    HasDefaults[] nestedAnnotations();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface HasDefaults {
    int value() default 2;
  }

  @TestAnnotation(
      b = 1,
      bool = true,
      s = 2,
      c = 'c',
      i = 4,
      l = 5,
      d = 6.0d,
      f = 7.0f,
      string = "hello, world",
      enumValue = RetentionPolicy.CLASS,
      classValue = AnnotationProtoConverter.class,
      nestedAnnotations = {@HasDefaults, @HasDefaults(8)})
  static class TestSubject {}

  @Test
  public void conversion() {
    AnnotationMirror actual =
        getOnlyElement(elements.getTypeElement(TestSubject.class).getAnnotationMirrors());
    AnnotationMirror translated =
        annotationProtoConverter.fromProto(AnnotationProtoConverter.toProto(actual));
    assertThat(AnnotationMirrors.equivalence().equivalent(actual, translated)).isTrue();
  }
}
