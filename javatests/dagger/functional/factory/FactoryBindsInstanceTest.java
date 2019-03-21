/*
 * Copyright (C) 2019 The Dagger Authors.
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

package dagger.functional.factory;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.fail;

import dagger.BindsInstance;
import dagger.Component;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for component factories with {@link BindsInstance} parameters. */
@RunWith(JUnit4.class)
public final class FactoryBindsInstanceTest {

  @Component
  interface BindsInstanceComponent {
    String string();

    @Component.Factory
    interface Factory {
      BindsInstanceComponent create(@BindsInstance String string);
    }
  }

  @Test
  public void bindsInstance() {
    BindsInstanceComponent component =
        DaggerFactoryBindsInstanceTest_BindsInstanceComponent.factory().create("baz");
    assertThat(component.string()).isEqualTo("baz");
  }

  @Test
  public void nonNullableBindsInstance_failsOnNull() {
    try {
      DaggerFactoryBindsInstanceTest_BindsInstanceComponent.factory().create(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Target({METHOD, PARAMETER})
  @Retention(RUNTIME)
  @interface Nullable {}

  @Component
  interface NullableBindsInstanceComponent {
    @Nullable
    String string();

    @Component.Factory
    interface Factory {
      NullableBindsInstanceComponent create(@BindsInstance @Nullable String string);
    }
  }

  @Test
  public void nullableBindsInstance_doesNotFailOnNull() {
    NullableBindsInstanceComponent component =
        DaggerFactoryBindsInstanceTest_NullableBindsInstanceComponent.factory().create(null);
    assertThat(component.string()).isEqualTo(null);
  }

  @Component
  interface PrimitiveBindsInstanceComponent {
    int getInt();

    @Component.Factory
    interface Factory {
      PrimitiveBindsInstanceComponent create(@BindsInstance int i);
    }
  }

  @Test
  public void primitiveBindsInstance() {
    PrimitiveBindsInstanceComponent component =
        DaggerFactoryBindsInstanceTest_PrimitiveBindsInstanceComponent.factory().create(1);
    assertThat(component.getInt()).isEqualTo(1);
  }
}
