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

package test.multipackage.a;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

@Module
public final class AModule {
  @Provides
  @IntoSet
  static String provideString() {
    return "a";
  }

  @Provides
  @IntoSet
  static Inaccessible provideInaccessible(Inaccessible inaccessible) {
    return inaccessible;
  }

  @Provides
  @ElementsIntoSet
  static Set<Inaccessible> provideSetOfInaccessibles() {
    return new HashSet<>();
  }

  @Provides
  @IntoMap
  @StringKey("inaccessible")
  static Inaccessible provideInaccessibleToMap(Inaccessible inaccessible) {
    return inaccessible;
  }

  static class Inaccessible {
    @Inject Inaccessible() {}
  }

}
