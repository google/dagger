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

import dagger.functional.producers.optional.OptionalBindingComponents.AbsentOptionalBindingComponent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for absent optional bindings. */
@RunWith(JUnit4.class)
public final class OptionalBindingComponentsAbsentTest {
  private AbsentOptionalBindingComponent absent;

  @Before
  public void setUp() {
    absent = DaggerOptionalBindingComponents_AbsentOptionalBindingComponent.create();
  }

  @Test
  public void optional() throws Exception {
    assertThat(absent.optionalInstance().get()).isAbsent();
  }

  @Test
  public void optionalProducer() throws Exception {
    assertThat(absent.optionalProducer().get()).isAbsent();
  }

  @Test
  public void optionalProduced() throws Exception {
    assertThat(absent.optionalProduced().get()).isAbsent();
  }

  @Test
  public void qualifiedOptional() throws Exception {
    assertThat(absent.qualifiedOptionalInstance().get()).isAbsent();
  }

  @Test
  public void qualifiedOptionalProducer() throws Exception {
    assertThat(absent.qualifiedOptionalProducer().get()).isAbsent();
  }

  @Test
  public void qualifiedOptionalProduced() throws Exception {
    assertThat(absent.qualifiedOptionalProduced().get()).isAbsent();
  }
}
