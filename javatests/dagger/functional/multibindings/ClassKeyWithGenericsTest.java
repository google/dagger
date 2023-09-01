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

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

// This is a regression test for https://github.com/google/dagger/issues/4055.
@RunWith(JUnit4.class)
public final class ClassKeyWithGenericsTest {
  @Component(modules = TestModule.class)
  interface TestComponent {
    Map<Class<?>, String> map();
  }

  @Module
  interface TestModule {
    @Provides
    @IntoMap
    @ClassKey(Thing.class)
    static String provideThingValue() {
      return "Thing";
    }

    @Provides
    @IntoMap
    @ClassKey(GenericThing.class)
    static String provideGenericThingValue() {
      return "GenericThing";
    }
  }

  class Thing {}

  class GenericThing<T> {}

  @Test
  public void test() {
    Map<Class<?>, String> map = DaggerClassKeyWithGenericsTest_TestComponent.create().map();
    assertThat(map)
        .containsExactly(
            Thing.class, "Thing",
            GenericThing.class, "GenericThing");
  }
}
