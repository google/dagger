/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.functional.multipackage.a;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.StringKey;
import java.util.HashSet;
import java.util.Set;

@Module
public abstract class AModule {
  @Provides
  @IntoSet
  static String provideString() {
    return "a";
  }

  @Binds
  @IntoSet
  abstract Inaccessible provideInaccessible(Inaccessible inaccessible);

  @Provides
  @ElementsIntoSet
  static Set<Inaccessible> provideSetOfInaccessibles() {
    return new HashSet<>();
  }

  @Binds
  @IntoMap
  @StringKey("inaccessible")
  abstract Inaccessible provideInaccessibleToMap(Inaccessible inaccessible);
}
