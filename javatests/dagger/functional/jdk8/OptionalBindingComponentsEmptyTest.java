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

import static com.google.common.truth.Truth8.assertThat;

import dagger.functional.jdk8.OptionalBindingComponents.EmptyOptionalBindingComponent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for absent optional bindings. */
@RunWith(JUnit4.class)
public final class OptionalBindingComponentsEmptyTest {
  private EmptyOptionalBindingComponent component;

  @Before
  public void setUp() {
    component = DaggerOptionalBindingComponents_EmptyOptionalBindingComponent.create();
  }

  @Test
  public void optional() {
    assertThat(component.values().optionalInstance()).isEmpty();
  }

  @Test
  public void optionalProvider() {
    assertThat(component.values().optionalProvider()).isEmpty();
  }

  @Test
  public void optionalLazy() {
    assertThat(component.values().optionalLazy()).isEmpty();
  }

  @Test
  public void optionalLazyProvider() {
    assertThat(component.values().optionalLazyProvider()).isEmpty();
  }

  @Test
  public void qualifiedOptional() {
    assertThat(component.qualifiedValues().optionalInstance()).isEmpty();
  }

  @Test
  public void qualifiedOptionalProvider() {
    assertThat(component.qualifiedValues().optionalProvider()).isEmpty();
  }

  @Test
  public void qualifiedOptionalLazy() {
    assertThat(component.qualifiedValues().optionalLazy()).isEmpty();
  }

  @Test
  public void qualifiedOptionalLazyProvider() {
    assertThat(component.qualifiedValues().optionalLazyProvider()).isEmpty();
  }
}
