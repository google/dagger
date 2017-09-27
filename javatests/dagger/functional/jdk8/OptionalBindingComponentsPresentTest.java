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

package dagger.functional.jdk8;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static dagger.functional.jdk8.OptionalBindingComponents.Value.QUALIFIED_VALUE;
import static dagger.functional.jdk8.OptionalBindingComponents.Value.VALUE;

import com.google.common.collect.ImmutableList;
import dagger.functional.jdk8.OptionalBindingComponents.OptionalBindingComponent;
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
          {DaggerOptionalBindingComponents_EmptyOptionalBindingComponent.create().presentChild()},
        });
  }

  private final OptionalBindingComponent component;

  public OptionalBindingComponentsPresentTest(OptionalBindingComponent component) {
    this.component = component;
  }

  @Test
  public void optionalProvider() {
    assertThat(component.values().optionalProvider().get().get()).isEqualTo(VALUE);
  }

  @Test
  public void optionalLazy() {
    assertThat(component.values().optionalLazy().get().get()).isEqualTo(VALUE);
  }

  @Test
  public void optionalLazyProvider() {
    assertThat(component.values().optionalLazyProvider().get().get().get()).isEqualTo(VALUE);
  }

  @Test
  public void qualifiedOptional() {
    assertThat(component.qualifiedValues().optionalInstance()).hasValue(QUALIFIED_VALUE);
  }

  @Test
  public void qualifiedOptionalProvider() {
    assertThat(component.qualifiedValues().optionalProvider().get().get())
        .isEqualTo(QUALIFIED_VALUE);
  }

  @Test
  public void qualifiedOptionalLazy() {
    assertThat(component.qualifiedValues().optionalLazy().get().get()).isEqualTo(QUALIFIED_VALUE);
  }

  @Test
  public void qualifiedOptionalLazyProvider() {
    assertThat(component.qualifiedValues().optionalLazyProvider().get().get().get())
        .isEqualTo(QUALIFIED_VALUE);
  }

  @Test
  public void optionalNullableProvider() {
    assertThat(component.optionalNullableProvider().get().get()).isNull();
  }

  @Test
  public void optionalNullableLazy() {
    assertThat(component.optionalNullableLazy().get().get()).isNull();
  }

  @Test
  public void optionalNullableLazyProvider() {
    assertThat(component.optionalNullableLazyProvider().get().get().get()).isNull();
  }
}
