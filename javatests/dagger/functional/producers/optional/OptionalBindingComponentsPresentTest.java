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

package dagger.functional.producers.optional;

import static com.google.common.truth.Truth.assertThat;
import static dagger.functional.producers.optional.OptionalBindingComponents.Value.QUALIFIED_VALUE;
import static dagger.functional.producers.optional.OptionalBindingComponents.Value.VALUE;

import com.google.common.collect.ImmutableList;
import dagger.functional.producers.optional.OptionalBindingComponents.OptionalBindingComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests for present optional bindings. */
@RunWith(Parameterized.class)
public final class OptionalBindingComponentsPresentTest {

  @Parameters(name = "{0}")
  public static Iterable<Object[]> parameters() {
    return ImmutableList.copyOf(
        new Object[][] {
          {DaggerOptionalBindingComponents_PresentOptionalBindingComponent.create()},
          {DaggerOptionalBindingComponents_AbsentOptionalBindingComponent.create().presentChild()},
          {DaggerOptionalBindingComponents_PresentOptionalProvisionBindingComponent.create()}
        });
  }

  private final OptionalBindingComponent component;

  public OptionalBindingComponentsPresentTest(OptionalBindingComponent component) {
    this.component = component;
  }

  @Test
  public void optional() throws Exception {
    assertThat(component.optionalInstance().get()).hasValue(VALUE);
  }

  @Test
  public void optionalProducer() throws Exception {
    assertThat(component.optionalProducer().get().get().get().get()).isEqualTo(VALUE);
  }

  @Test
  public void optionalProduced() throws Exception {
    assertThat(component.optionalProduced().get().get().get()).isEqualTo(VALUE);
  }

  @Test
  public void qualifiedOptional() throws Exception {
    assertThat(component.qualifiedOptionalInstance().get()).hasValue(QUALIFIED_VALUE);
  }

  @Test
  public void qualifiedOptionalProducer() throws Exception {
    assertThat(component.qualifiedOptionalProducer().get().get().get().get())
        .isEqualTo(QUALIFIED_VALUE);
  }

  @Test
  public void qualifiedOptionalProduced() throws Exception {
    assertThat(component.qualifiedOptionalProduced().get().get().get()).isEqualTo(QUALIFIED_VALUE);
  }

  @Test
  public void optionalNullableProducer() throws Exception {
    assertThat(component.optionalNullableProducer().get().get().get().get()).isNull();
  }

  @Test
  public void optionalNullableProduced() throws Exception {
    assertThat(component.optionalNullableProduced().get().get().get()).isNull();
  }
}
