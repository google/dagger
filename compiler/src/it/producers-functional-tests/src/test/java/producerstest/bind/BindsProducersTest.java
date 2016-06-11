/*
 * Copyright (C) 2016 Google, Inc.
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
package producerstest.bind;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

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
        .isSameAs(component.qualifiedFooOfStrings().get());
  }

  @Test
  public void multibindings() throws Exception {
    assertThat(component.foosOfNumbers().get()).hasSize(2);
    assertThat(component.objects().get()).hasSize(3);
    assertThat(component.charSequences().get()).hasSize(5);
  }
}
