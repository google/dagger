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

package dagger.functional;

import dagger.Module;
import dagger.Provides;

@Module
final class NullableModule {
  /**
   * A {@code Nullable} that isn't {@link javax.annotation.Nullable}, to ensure that Dagger can be
   * built without depending on JSR-305.
   */
  @interface Nullable {}

  @Provides
  @Nullable
  static Object nullObject() {
    return null;
  }
}
