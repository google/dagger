/*
 * Copyright (C) 2016 Google, Inc.
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
package producerstest.multibindings;

import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import java.util.Set;

@Module
final class MultibindingModule {
  @Provides
  @IntoSet
  static String providedStr() {
    return "providedStr";
  }

  @Provides
  @ElementsIntoSet
  static Set<String> providedStrs() {
    return ImmutableSet.of("providedStr1", "providedStr2");
  }

  @Provides
  @IntoMap
  @IntKey(3)
  static String providedValueFor3() {
    return "provided three";
  }
}
