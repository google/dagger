/*
 * Copyright (C) 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package test;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import javax.inject.Provider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class MultibindingTest {
  private MultibindingComponent multibindingComponent;

  @Before public void setUp() {
    multibindingComponent = DaggerMultibindingComponent.builder()
        .multibindingDependency(new MultibindingDependency() {
          @Override public double doubleDependency() {
            return 0.0;
          }
        })
        .build();
  }

  @Test public void map() {
    Map<String, String> map = multibindingComponent.map();
    assertThat(map).hasSize(2);
    assertThat(map).containsEntry("foo", "foo value");
    assertThat(map).containsEntry("bar", "bar value");
  }

  @Test public void mapOfProviders() {
    Map<String, Provider<String>> mapOfProviders = multibindingComponent.mapOfProviders();
    assertThat(mapOfProviders).hasSize(2);
    assertThat(mapOfProviders.get("foo").get()).isEqualTo("foo value");
    assertThat(mapOfProviders.get("bar").get()).isEqualTo("bar value");
  }

  @Test public void mapKeysAndValues() {
    assertThat(multibindingComponent.mapKeys()).containsExactly("foo", "bar");
    assertThat(multibindingComponent.mapValues()).containsExactly("foo value", "bar value");
  }

  @Test public void nestedKeyMap() {
    assertThat(multibindingComponent.nestedKeyMap()).isEqualTo(
        ImmutableMap.of(
            nestedWrappedKey(Integer.class), "integer",
            nestedWrappedKey(Long.class), "long"));
  }

  @Test
  public void unwrappedAnnotationKeyMap() {
    assertThat(multibindingComponent.unwrappedAnnotationKeyMap())
        .isEqualTo(ImmutableMap.of(testStringKey("foo\n"), "foo annotation"));
  }

  @Test
  public void wrappedAnnotationKeyMap() {
    @SuppressWarnings("unchecked")
    Class<? extends Number>[] classes = new Class[] {Long.class, Integer.class};
    assertThat(multibindingComponent.wrappedAnnotationKeyMap())
        .isEqualTo(
            ImmutableMap.of(
                testWrappedAnnotationKey(
                    testStringKey("foo"), new int[] {1, 2, 3}, new TestClassKey[] {}, classes),
                "wrapped foo annotation"));
  }

  @Test
  public void booleanKeyMap() {
    assertThat(multibindingComponent.booleanKeyMap()).isEqualTo(ImmutableMap.of(true, "true"));
  }

  @Test
  public void byteKeyMap() {
    assertThat(multibindingComponent.byteKeyMap())
        .isEqualTo(ImmutableMap.of((byte) 100, "100 byte"));
  }

  @Test
  public void charKeyMap() {
    assertThat(multibindingComponent.characterKeyMap())
        .isEqualTo(ImmutableMap.of('a', "a char", '\n', "newline char"));
  }

  @Test
  public void classKeyMap() {
    assertThat(multibindingComponent.classKeyMap())
        .isEqualTo(
            ImmutableMap.of(
                Integer.class, "integer",
                Long.class, "long"));
  }

  @Test
  public void numberClassKeyMap() {
    assertThat(multibindingComponent.numberClassKeyMap())
        .isEqualTo(
            ImmutableMap.of(
                BigDecimal.class, "bigdecimal",
                BigInteger.class, "biginteger"));
  }

  @Test
  public void intKeyMap() {
    assertThat(multibindingComponent.integerKeyMap()).isEqualTo(ImmutableMap.of(100, "100 int"));
  }

  @Test
  public void longKeyMap() {
    assertThat(multibindingComponent.longKeyMap())
        .isEqualTo(ImmutableMap.of((long) 100, "100 long"));
  }

  @Test
  public void shortKeyMap() {
    assertThat(multibindingComponent.shortKeyMap())
        .isEqualTo(ImmutableMap.of((short) 100, "100 short"));
  }

  @Test public void setBindings() {
    assertThat(multibindingComponent.set()).containsExactly(-90, -17, -1, 5, 6, 832, 1742);
  }

  @AutoAnnotation
  static TestStringKey testStringKey(String value) {
    return new AutoAnnotation_MultibindingTest_testStringKey(value);
  }

  @AutoAnnotation
  static TestStringKey.NestedWrappedKey nestedWrappedKey(Class<?> value) {
    return new AutoAnnotation_MultibindingTest_nestedWrappedKey(value);
  }

  @AutoAnnotation
  static TestWrappedAnnotationKey testWrappedAnnotationKey(
      TestStringKey value,
      int[] integers,
      TestClassKey[] annotations,
      Class<? extends Number>[] classes) {
    return new AutoAnnotation_MultibindingTest_testWrappedAnnotationKey(
        value, integers, annotations, classes);
  }
}
