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

package test.builderbinds;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BuilderBindsTest {

  @Test
  public void builderBinds() {
    TestComponent component =
        DaggerTestComponent.builder()
            .count(5)
            .input("foo")
            .nullableInput("bar")
            .listOfString(Arrays.asList("x", "y", "z"))
            .build();
    assertThat(component.count()).isEqualTo(5);
    assertThat(component.input()).isEqualTo("foo");
    assertThat(component.nullableInput()).isEqualTo("bar");
    assertThat(component.listOfString()).containsExactly("x", "y", "z").inOrder();
  }

  @Test
  public void builderBindsNullableWithNull() {
    TestComponent component =
        DaggerTestComponent.builder()
            .count(5)
            .input("foo")
            .nullableInput(null)
            .listOfString(Collections.<String>emptyList())
            .build();
    assertThat(component.count()).isEqualTo(5);
    assertThat(component.input()).isEqualTo("foo");
    assertThat(component.nullableInput()).isNull();
    assertThat(component.listOfString()).isEmpty();
  }

  @Test
  public void builderBindsNonNullableWithNull() {
    try {
      DaggerTestComponent.builder().count(5).input(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void builderBindsPrimitiveNotSet() {
    try {
      DaggerTestComponent.builder()
          .input("foo")
          .nullableInput("bar")
          .listOfString(Collections.<String>emptyList())
          .build();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void builderBindsNonNullableNotSet() {
    try {
      DaggerTestComponent.builder()
          .count(5)
          .nullableInput("foo")
          .listOfString(Collections.<String>emptyList())
          .build();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void builderBindsNullableNotSet() {
    TestComponent component =
        DaggerTestComponent.builder()
            .count(5)
            .input("foo")
            .listOfString(Collections.<String>emptyList())
            .build();
    assertThat(component.count()).isEqualTo(5);
    assertThat(component.input()).isEqualTo("foo");
    assertThat(component.nullableInput()).isNull();
    assertThat(component.listOfString()).isEmpty();
  }
}
