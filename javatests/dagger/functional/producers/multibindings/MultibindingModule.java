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

package dagger.functional.producers.multibindings;

import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Provides;
import dagger.functional.producers.multibindings.Qualifiers.EmptyButDeclaredInModule;
import dagger.functional.producers.multibindings.Qualifiers.EmptyButDeclaredInModuleAndProducerModule;
import dagger.functional.producers.multibindings.Qualifiers.OnlyProvisionMultibindings;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;
import dagger.multibindings.StringKey;
import java.util.Map;
import java.util.Set;

@Module
abstract class MultibindingModule {
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
  
  @Multibinds
  @EmptyButDeclaredInModuleAndProducerModule
  abstract Map<String, Object> emptyButDeclaredInModuleAndProducerModule();

  @Multibinds
  @EmptyButDeclaredInModule
  abstract Map<String, Object> emptyButDeclaredInModule();

  @Provides
  @IntoMap
  @StringKey("a")
  @OnlyProvisionMultibindings
  static Object onlyProvisionMultibindings() {
    return "only multibinding";
  }
}
