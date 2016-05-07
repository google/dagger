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
package test.bind;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class BindTest {
  @Test
  public void bindDelegates() {
    TestComponent component = DaggerTestComponent.create();
    assertThat(component.object()).isInstanceOf(FooOfStrings.class);
    assertThat(component.fooOfStrings()).isInstanceOf(FooOfStrings.class);
    assertThat(component.fooOfObjects()).isInstanceOf(FooOfObjects.class);
    assertThat(component.fooOfIntegers()).isNotNull();
  }

  @Test
  public void bindWithScope() {
    TestComponent component = DaggerTestComponent.create();
    assertThat(component.qualifiedFooOfStrings())
        .isSameAs(component.qualifiedFooOfStrings());
  }
}
