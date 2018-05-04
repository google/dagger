/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.functional.multibindings;

import static com.google.common.truth.Truth.assertThat;

import com.google.auto.value.AutoAnnotation;
import dagger.Component;
import dagger.MapKey;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ComplexMapKeysInDifferentOrderTest {
  @MapKey(unwrapValue = false)
  @interface ComplexMapKey {
    int i();
    int j();
  }

  @Module
  interface TestModule {
    @Provides
    @IntoMap
    @ComplexMapKey(i = 1, j = 2)
    static int inOrder() {
      return 3;
    }

    @Provides
    @IntoMap
    @ComplexMapKey(j = 4, i = 5)
    static int backwardsOrder() {
      return 6;
    }
  }

  @Component(modules = TestModule.class)
  interface TestComponent {
    Map<ComplexMapKey, Integer> map();
  }

  @Test
  public void test() {
    Map<ComplexMapKey, Integer> map =
        DaggerComplexMapKeysInDifferentOrderTest_TestComponent.create().map();
    assertThat(map.get(mapKey(1, 2))).isEqualTo(3);
    assertThat(map.get(mapKey(5, 4))).isEqualTo(6);
  }

  @AutoAnnotation
  static ComplexMapKey mapKey(int i, int j) {
    return new AutoAnnotation_ComplexMapKeysInDifferentOrderTest_mapKey(i, j);
  }
}
