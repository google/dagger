/*
 * Copyright (C) 2021 The Dagger Authors.
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

import android.app.Application;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.migration.CustomInject;
import dagger.hilt.android.migration.CustomInjection;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Inject;

/**
 * Classes for CustomInjectTest. This is in a separate build target because otherwise robolectric
 * does not recognize the application class as extending application due to order of class
 * generation.
 */
public final class CustomInjectClasses {

  @Module
  @InstallIn(SingletonComponent.class)
  static final class TestModule {
    @Provides
    static Integer provideInt() {
      return 9;
    }
  }

  @CustomInject
  @HiltAndroidApp(Application.class)
  public static final class TestApplication extends Hilt_CustomInjectClasses_TestApplication {

    @Inject Integer intValue;

    void inject() {
      CustomInjection.inject(this);
    }
  }
}
