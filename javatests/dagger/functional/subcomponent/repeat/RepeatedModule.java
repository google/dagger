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

package dagger.functional.subcomponent.repeat;

import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
final class RepeatedModule {
  private final Object state = new Object();

  @Provides
  Object state() {
    return state;
  }

  @Provides
  static String provideString() {
    return "a string";
  }

  @Provides
  @IntoSet
  static String contributeString() {
    return "a string in a set";
  }

  @Provides
  static OnlyUsedInParent provideOnlyUsedInParent() {
    return new OnlyUsedInParent() {};
  }

  @Provides
  static OnlyUsedInChild provideOnlyUsedInChild() {
    return new OnlyUsedInChild() {};
  }
}
