/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.functional.producers.binds;

import static com.google.common.truth.Truth.assertThat;

import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BindsProducersTest {

  private SimpleBindsProductionComponent component;

  @Before
  public void setUp() {
    component = DaggerSimpleBindsProductionComponent.create();
  }

  @Test
  public void bindDelegates() throws Exception {
    assertThat(component.object().get()).isInstanceOf(FooOfStrings.class);
    assertThat(component.fooOfStrings().get()).isInstanceOf(FooOfStrings.class);
    assertThat(component.fooOfIntegers().get()).isNotNull();
  }

  @Test
  public void bindWithScope() throws Exception {
    assertThat(component.qualifiedFooOfStrings().get())
        .isSameInstanceAs(component.qualifiedFooOfStrings().get());
  }

  @Test
  public void multibindings() throws Exception {
    assertThat(component.foosOfNumbers().get()).hasSize(2);
    assertThat(component.objects().get()).hasSize(3);
    assertThat(component.charSequences().get()).hasSize(5);

    assertThat(component.integerObjectMap().get())
        .containsExactly(
            123, "123-string", 456, "456-string", 789, "789-string", -1, "provision-string");

    Map<Integer, Producer<Object>> integerProducerOfObjectMap =
        component.integerProducerOfObjectMap().get();
    assertThat(integerProducerOfObjectMap).hasSize(4);
    assertThat(integerProducerOfObjectMap.get(123).get().get()).isEqualTo("123-string");
    assertThat(integerProducerOfObjectMap.get(456).get().get()).isEqualTo("456-string");
    assertThat(integerProducerOfObjectMap.get(789).get().get()).isEqualTo("789-string");
    assertThat(integerProducerOfObjectMap.get(-1).get().get()).isEqualTo("provision-string");

    assertThat(component.integerProducedOfObjectMap().get())
        .containsExactly(
            123, Produced.successful("123-string"),
            456, Produced.successful("456-string"),
            789, Produced.successful("789-string"),
            -1, Produced.successful("provision-string"));

    assertThat(component.qualifiedIntegerObjectMap().get()).hasSize(1);
  }
}
