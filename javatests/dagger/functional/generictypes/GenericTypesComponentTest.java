/*
 * Copyright (C) 2022 The Dagger Authors.
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

package dagger.functional.generictypes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class GenericTypesComponentTest {
  private static final String STRING_VALUE = "someString";
  private static final int INT_VALUE = 3;

  @Component(modules = GenericTypesModule.class)
  interface GenericTypesComponent extends GenericTypesInterface<Integer, String> {}

  interface GenericTypesInterface<T1, T2> {
    Set<T2> genericSet();

    Map<T1, T2> genericMap();
  }

  @Module
  interface GenericTypesModule {
    @Provides
    static Map<Integer, String> provideMap(String str) {
      return ImmutableMap.of(INT_VALUE, str);
    }

    @Provides
    static Set<String> provideSet(String str) {
      return ImmutableSet.of(str);
    }

    @Provides
    static String provideString() {
      return STRING_VALUE;
    }
  }

  @Test
  public void testComponent() {
    GenericTypesComponent component =
        DaggerGenericTypesComponentTest_GenericTypesComponent.create();
    assertThat(component.genericSet()).containsExactly(STRING_VALUE);
    assertThat(component.genericMap()).containsExactly(INT_VALUE, STRING_VALUE);
  }
}
