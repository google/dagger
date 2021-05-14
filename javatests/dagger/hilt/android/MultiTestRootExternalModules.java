/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Qualifier;

public final class MultiTestRootExternalModules {
  static final long EXTERNAL_LONG_VALUE = 43L;
  static final String EXTERNAL_STR_VALUE = "EXTERNAL_STRING_VALUE";

  @Qualifier
  @interface External {}

  @Module
  @InstallIn(SingletonComponent.class)
  interface PkgPrivateAppModule {
    @Provides
    @External
    static String provideStringValue() {
      return EXTERNAL_STR_VALUE;
    }
  }

  @Module
  @InstallIn(ActivityComponent.class)
  interface PkgPrivateActivityModule {
    @Provides
    @External
    static Long provideLongValue() {
      return EXTERNAL_LONG_VALUE;
    }
  }

  private MultiTestRootExternalModules() {}
}
