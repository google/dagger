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

package test.optional;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import test.optional.OptionalBindingComponents.AbsentOptionalBindingComponent;

/** Tests for absent optional bindings. */
@RunWith(JUnit4.class)
public final class OptionalBindingComponentsAbsentTest {
  private AbsentOptionalBindingComponent absent;

  @Before
  public void setUp() {
    absent = DaggerOptionalBindingComponents_AbsentOptionalBindingComponent.create();
  }

  @Test
  public void optional() {
    assertThat(absent.optionalInstance()).isAbsent();
  }

  @Test
  public void optionalProvider() {
    assertThat(absent.optionalProvider()).isAbsent();
  }

  @Test
  public void optionalLazy() {
    assertThat(absent.optionalLazy()).isAbsent();
  }

  @Test
  public void optionalLazyProvider() {
    assertThat(absent.optionalLazyProvider()).isAbsent();
  }

  @Test
  public void qualifiedOptional() {
    assertThat(absent.qualifiedOptionalInstance()).isAbsent();
  }

  @Test
  public void qualifiedOptionalProvider() {
    assertThat(absent.qualifiedOptionalProvider()).isAbsent();
  }

  @Test
  public void qualifiedOptionalLazy() {
    assertThat(absent.qualifiedOptionalLazy()).isAbsent();
  }

  @Test
  public void qualifiedOptionalLazyProvider() {
    assertThat(absent.qualifiedOptionalLazyProvider()).isAbsent();
  }
}
