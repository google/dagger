/*
 * Copyright (C) 2026 The Dagger Authors.
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
import static dagger.functional.jdk8.TypeUseNullabilityClasses.EXPECTED_INTEGER;
import static dagger.functional.jdk8.TypeUseNullabilityClasses.EXPECTED_STRING;
import static org.junit.Assert.assertThrows;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.functional.jdk8.TypeUseNullabilityClasses.GenericBar;
import dagger.functional.jdk8.TypeUseNullabilityClasses.GenericFoo;
import dagger.functional.jdk8.TypeUseNullabilityClasses.NullFoo;
import dagger.functional.jdk8.TypeUseNullabilityClasses.TypeUse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Similar to {@link dagger.functional.nullables.NullabilityTest}, but for TYPE_USE annotated
 * nullable annotations.
 */
@RunWith(JUnit4.class)
public final class TypeUseNullabilityTest {

  @Component(modules = NullModule.class)
  interface NullComponent {
    NullFoo nullFoo();

    String nonNullableString();

    @TypeUse.Nullable
    Integer nullableInteger();

    // TODO determine validity of this
    GenericFoo<@TypeUse.Nullable Object> genericFoo();

    void injectBarString(GenericBar<String> stringBar);

    void injectBarInteger(GenericBar<Integer> stringBar);
  }

  @Component(dependencies = NullComponent.class)
  interface NullComponentWithDependency {
    NullFoo nullFoo();

    String nonNullableString();

    @TypeUse.Nullable
    Integer nullableInteger();
  }

  @Module
  static class NullModule {
    private final String string;
    private final Integer integer;

    NullModule(String string, Integer integer) {
      this.string = string;
      this.integer = integer;
    }

    @Provides
    String provideNonNullableString() {
      return string;
    }

    @Provides
    @TypeUse.Nullable
    Integer provideNullableType() {
      return integer;
    }

    @Provides
    GenericFoo<@TypeUse.Nullable Object> provideGenericFoo() {
      return new GenericFoo<>(new Object());
    }
  }

  /**
   * Baseline test case demonstrating that all requested bindings would actually work with non-null
   * bindings.
   */
  @Test
  public void nonNull() {
    NullModule nonNullModule = new NullModule(EXPECTED_STRING, EXPECTED_INTEGER);
    NullComponent component =
        DaggerTypeUseNullabilityTest_NullComponent.builder().nullModule(nonNullModule).build();
    NullFoo nullFoo = component.nullFoo();

    assertThat(component.nonNullableString()).isEqualTo(EXPECTED_STRING);
    assertThat(component.nullableInteger()).isEqualTo(EXPECTED_INTEGER);

    assertThat(nullFoo.nullableInteger).isEqualTo(EXPECTED_INTEGER);
    assertThat(nullFoo.nullableIntegerField).isEqualTo(EXPECTED_INTEGER);
    assertThat(nullFoo.nullableMethodInjectedField).isEqualTo(EXPECTED_INTEGER);
  }

  @Test
  public void testNullability_moduleProvides() {
    NullModule nullModule = new NullModule(/* string= */ null, /* integer= */ null);
    NullComponent component =
        DaggerTypeUseNullabilityTest_NullComponent.builder().nullModule(nullModule).build();

    NullPointerException expected =
        assertThrows(NullPointerException.class, component::nonNullableString);
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("Cannot return null from a non-@Nullable @Provides method");

    assertThat(component.nullableInteger()).isNull();
  }

  @Test
  public void testNullability_typeInjection() {
    NullModule nullModule = new NullModule(/* string= */ null, /* integer= */ null);
    NullComponent component =
        DaggerTypeUseNullabilityTest_NullComponent.builder().nullModule(nullModule).build();
    NullFoo nullFoo = component.nullFoo();

    assertThat(nullFoo.nullableInteger).isNull();
    assertThat(nullFoo.nullableIntegerField).isNull();
    assertThat(nullFoo.nullableMethodInjectedField).isNull();
  }

  @Test
  public void testNullability_componentOverride() {
    NullComponent nullComponent =
        new NullComponent() {
          @Override
          public NullFoo nullFoo() {
            return null;
          }

          @Override
          public String nonNullableString() {
            return null;
          }

          @Override
          public Integer nullableInteger() {
            return null;
          }

          @Override
          public GenericFoo<Object> genericFoo() {
            return new GenericFoo<>(null);
          }

          @Override
          public void injectBarString(GenericBar<String> stringBar) {
            stringBar.t = null;
          }

          @Override
          public void injectBarInteger(GenericBar<@TypeUse.Nullable Integer> intBar) {
            intBar.t = null;
          }
        };
    NullComponentWithDependency component =
        DaggerTypeUseNullabilityTest_NullComponentWithDependency.builder()
            .nullComponent(nullComponent)
            .build();

    NullPointerException expected =
        assertThrows(NullPointerException.class, component::nonNullableString);
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("Cannot return null from a non-@Nullable component method");

    assertThat(component.nullableInteger()).isNull();
  }
}
