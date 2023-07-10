/*
 * Copyright (C) 2023 The Dagger Authors.
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

package dagger.functional.nullables;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import org.jspecify.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JspecifyNullableTest {
  @Component(modules = MyModule.class)
  interface MyComponent {
    Integer getInt();
  }

  @Module
  static class MyModule {
    private final Integer value;

    MyModule(Integer value) {
      this.value = value;
    }

    @Provides
    @Nullable
    Integer provideInt() {
      return value;
    }
  }

  @Test
  public void testWithValue() {
    MyComponent component =
        DaggerJspecifyNullableTest_MyComponent.builder().myModule(new MyModule(15)).build();
    assertThat(component.getInt()).isEqualTo(15);
  }

  @Test
  public void testWithNull() {
    MyComponent component =
        DaggerJspecifyNullableTest_MyComponent.builder().myModule(new MyModule(null)).build();
    NullPointerException expectedException =
        assertThrows(NullPointerException.class, component::getInt);
    assertThat(expectedException)
        .hasMessageThat()
        .contains("Cannot return null from a non-@Nullable @Provides method");
  }
}
