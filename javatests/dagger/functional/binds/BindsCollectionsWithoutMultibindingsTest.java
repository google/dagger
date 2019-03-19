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

package dagger.functional.binds;

import static com.google.common.truth.Truth.assertThat;

import dagger.Binds;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BindsCollectionsWithoutMultibindingsTest {
  @Module
  abstract static class M {
    @Provides
    static HashSet<String> provideHashSet() {
      HashSet<String> set = new HashSet<>();
      set.add("binds");
      set.add("set");
      return set;
    }

    @Binds
    abstract Set<String> bindStringSet(HashSet<String> set);

    @Provides
    static HashMap<String, String> provideHashMap() {
      HashMap<String, String> map = new HashMap<>();
      map.put("binds", "map");
      map.put("without", "multibindings");
      return map;
    }

    @Binds
    abstract Map<String, String> bindStringMap(HashMap<String, String> map);
  }

  @Component(modules = M.class)
  interface C {
    Set<String> set();

    Map<String, String> map();
  }

  @Test
  public void works() {
    C component = DaggerBindsCollectionsWithoutMultibindingsTest_C.create();

    assertThat(component.set()).containsExactly("binds", "set");
    assertThat(component.map())
        .containsExactly(
            "binds", "map",
            "without", "multibindings");
  }
}
