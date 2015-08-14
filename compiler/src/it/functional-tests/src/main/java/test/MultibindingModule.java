/*
* Copyright (C) 2015 Google, Inc.
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
package test;

import dagger2.Module;
import dagger2.Provides;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;

import static dagger2.Provides.Type.MAP;
import static dagger2.Provides.Type.SET;

@Module
class MultibindingModule {
  @Provides(type = MAP) @TestKey("foo") String provideFooKey(double doubleDependency) {
    return "foo value";
  }

  @Provides(type = MAP) @TestKey("bar") String provideBarKey() {
    return "bar value";
  }

  @Provides(type = SET) int provideFiveToSet() {
    return 5;
  }

  @Provides(type = SET) int provideSixToSet() {
    return 6;
  }

  @Provides Set<String> provideMapKeys(Map<String, Provider<String>> map) {
    return map.keySet();
  }

  @Provides Collection<String> provideMapValues(Map<String, String> map) {
    return map.values();
  }

  @Provides(type = MAP) @TestKey.NestedWrappedKey(Integer.class) String valueForInteger() {
    return "integer";
  }

  @Provides(type = MAP) @TestKey.NestedWrappedKey(Long.class) String valueForLong() {
    return "long";
  }
}
