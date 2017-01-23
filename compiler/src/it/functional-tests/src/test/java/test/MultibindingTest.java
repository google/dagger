/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableList;
import dagger.multibindings.ClassKey;
import dagger.multibindings.StringKey;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MultibindingTest {

  private static final MultibindingDependency MULTIBINDING_DEPENDENCY =
      new MultibindingDependency() {
        @Override
        public double doubleDependency() {
          return 0.0;
        }
      };

  @Parameters(name = "{0}")
  public static Iterable<Object[]> parameters() {
    return ImmutableList.copyOf(
        new Object[][] {
          {
            DaggerMultibindingComponent.builder()
                .multibindingDependency(MULTIBINDING_DEPENDENCY)
                .build()
          },
          {
            DaggerMultibindingComponentWithMultibindingsInterface.builder()
                .multibindingDependency(MULTIBINDING_DEPENDENCY)
                .build()
          }
        });
  }

  private final MultibindingComponent multibindingComponent;

  public MultibindingTest(MultibindingComponent multibindingComponent) {
    this.multibindingComponent = multibindingComponent;
  }

  @Test public void map() {
    Map<String, String> map = multibindingComponent.map();
    assertThat(map).hasSize(2);
    assertThat(map).containsEntry("foo", "foo value");
    assertThat(map).containsEntry("bar", "bar value");
  }

  @Test public void mapOfArrays() {
    Map<String, String[]> map = multibindingComponent.mapOfArrays();
    assertThat(map).hasSize(2);
    assertThat(map).containsKey("foo");
    assertThat(map.get("foo")).asList().containsExactly("foo1", "foo2").inOrder();
    assertThat(map).containsKey("bar");
    assertThat(map.get("bar")).asList().containsExactly("bar1", "bar2").inOrder();
  }

  @Test public void mapOfProviders() {
    Map<String, Provider<String>> mapOfProviders = multibindingComponent.mapOfProviders();
    assertThat(mapOfProviders).hasSize(2);
    assertThat(mapOfProviders.get("foo").get()).isEqualTo("foo value");
    assertThat(mapOfProviders.get("bar").get()).isEqualTo("bar value");
  }

  @Test public void mapKeysAndValues() {
    assertThat(multibindingComponent.mapKeys())
        .containsExactly("foo", "bar");
    assertThat(multibindingComponent.mapValues())
        .containsExactly("foo value", "bar value");
  }

  @Test public void nestedKeyMap() {
    assertThat(multibindingComponent.nestedKeyMap())
        .containsExactly(
            nestedWrappedKey(Integer.class), "integer", nestedWrappedKey(Long.class), "long");
  }

  @Test
  public void unwrappedAnnotationKeyMap() {
    assertThat(multibindingComponent.unwrappedAnnotationKeyMap())
        .containsExactly(testStringKey("foo\n"), "foo annotation");
  }

  @Test
  public void wrappedAnnotationKeyMap() {
    @SuppressWarnings("unchecked")
    Class<? extends Number>[] classes = new Class[] {Long.class, Integer.class};
    assertThat(multibindingComponent.wrappedAnnotationKeyMap())
        .containsExactly(
            testWrappedAnnotationKey(
                testStringKey("foo"), new int[] {1, 2, 3}, new ClassKey[] {}, classes),
            "wrapped foo annotation");
  }

  @Test
  public void booleanKeyMap() {
    assertThat(multibindingComponent.booleanKeyMap()).containsExactly(true, "true");
  }

  @Test
  public void byteKeyMap() {
    assertThat(multibindingComponent.byteKeyMap()).containsExactly((byte) 100, "100 byte");
  }

  @Test
  public void charKeyMap() {
    assertThat(multibindingComponent.characterKeyMap())
        .containsExactly('a', "a char", '\n', "newline char");
  }

  @Test
  public void classKeyMap() {
    assertThat(multibindingComponent.classKeyMap())
        .containsExactly(Integer.class, "integer", Long.class, "long");
  }

  @Test
  public void numberClassKeyMap() {
    assertThat(multibindingComponent.numberClassKeyMap())
        .containsExactly(BigDecimal.class, "bigdecimal", BigInteger.class, "biginteger");
  }

  @Test
  public void intKeyMap() {
    assertThat(multibindingComponent.integerKeyMap()).containsExactly(100, "100 int");
  }

  @Test
  public void longKeyMap() {
    assertThat(multibindingComponent.longKeyMap()).containsExactly((long) 100, "100 long");
  }

  @Test
  public void shortKeyMap() {
    assertThat(multibindingComponent.shortKeyMap()).containsExactly((short) 100, "100 short");
  }

  @Test public void setBindings() {
    assertThat(multibindingComponent.set())
        .containsExactly(-90, -17, -1, 5, 6, 832, 1742, -101, -102);
  }

  @Test
  public void complexQualifierSet() {
    assertThat(multibindingComponent.complexQualifierStringSet()).containsExactly("foo");
  }

  @Test
  public void emptySet() {
    assertThat(multibindingComponent.emptySet()).isEmpty();
  }

  @Test
  public void emptyQualifiedSet() {
    assertThat(multibindingComponent.emptyQualifiedSet()).isEmpty();
  }

  @Test
  public void emptyMap() {
    assertThat(multibindingComponent.emptyMap()).isEmpty();
  }

  @Test
  public void emptyQualifiedMap() {
    assertThat(multibindingComponent.emptyQualifiedMap()).isEmpty();
  }

  @Test
  public void maybeEmptySet() {
    assertThat(multibindingComponent.maybeEmptySet()).containsExactly("foo");
  }

  @Test
  public void maybeEmptyQualifiedSet() {
    assertThat(multibindingComponent.maybeEmptyQualifiedSet()).containsExactly("qualified foo");
  }

  @Test
  public void maybeEmptyMap() {
    assertThat(multibindingComponent.maybeEmptyMap()).containsEntry("key", "foo value");
  }

  @Test
  public void maybeEmptyQualifiedMap() {
    assertThat(multibindingComponent.maybeEmptyQualifiedMap())
        .containsEntry("key", "qualified foo value");
  }

  @AutoAnnotation
  static StringKey testStringKey(String value) {
    return new AutoAnnotation_MultibindingTest_testStringKey(value);
  }

  @AutoAnnotation
  static NestedAnnotationContainer.NestedWrappedKey nestedWrappedKey(Class<?> value) {
    return new AutoAnnotation_MultibindingTest_nestedWrappedKey(value);
  }

  @AutoAnnotation
  static WrappedAnnotationKey testWrappedAnnotationKey(
      StringKey value, int[] integers, ClassKey[] annotations, Class<? extends Number>[] classes) {
    return new AutoAnnotation_MultibindingTest_testWrappedAnnotationKey(
        value, integers, annotations, classes);
  }
}
