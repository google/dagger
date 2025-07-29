/*
 * Copyright (C) 2024 The Dagger Authors.
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

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.LazyClassKey;
import java.util.Map;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LazyClassKeyWithGenericsTest {
  @Component(modules = TestModule.class)
  @Singleton
  interface TestComponent {
    Map<Class<?>, String> map();

    Provider<Map<Class<?>, Provider<Integer>>> intMap();
  }

  @Module
  interface TestModule {
    @Provides
    @IntoMap
    @LazyClassKey(Thing.class)
    static String provideThingValue() {
      return "Thing";
    }

    @Provides
    @IntoMap
    @LazyClassKey(GenericThing.class)
    static String provideGenericThingValue() {
      return "GenericThing";
    }

    @Provides
    @IntoMap
    @LazyClassKey(Thing.class)
    static Integer provideThingIntValue() {
      return 2;
    }

    @Provides
    @IntoMap
    @LazyClassKey(GenericThing.class)
    static Integer provideGenericThingIntValue() {
      return 1;
    }
  }

  class Thing {}

  class GenericThing<T> {}

  @Test
  public void test() {
    TestComponent testComponent = DaggerLazyClassKeyWithGenericsTest_TestComponent.create();
    Map<Class<?>, String> map = testComponent.map();
    Map<Class<?>, Provider<Integer>> intMap = testComponent.intMap().get();
    // MapSubject#containsExactly uses entrySet, which is banned from DaggerClassKey map.
    assertThat(map.get(Thing.class)).isEqualTo("Thing");
    assertThat(map.get(GenericThing.class)).isEqualTo("GenericThing");
    assertThat(intMap.get(Thing.class).get()).isEqualTo(2);
    assertThat(intMap.get(GenericThing.class).get()).isEqualTo(1);
  }
}
