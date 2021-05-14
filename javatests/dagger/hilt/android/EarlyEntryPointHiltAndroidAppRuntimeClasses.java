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
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Classes for {@link EarlyEntryPointHiltAndroidAppRuntimeTest}. */
public final class EarlyEntryPointHiltAndroidAppRuntimeClasses {
  private EarlyEntryPointHiltAndroidAppRuntimeClasses() {}

  // @HiltAndroidApp cannot be nested in tests because @Config.application won't accept it.
  @HiltAndroidApp(Application.class)
  public static class TestApplication
      extends Hilt_EarlyEntryPointHiltAndroidAppRuntimeClasses_TestApplication {}

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  interface FooEntryPoint {
    Foo foo();
  }

  // @EarlyEntryPoint cannot be nested in tests, so we've separated it out into this class.
  @EarlyEntryPoint
  @InstallIn(SingletonComponent.class)
  interface EarlyFooEntryPoint {
    Foo foo();
  }

  @Singleton
  static class Foo {
    @Inject
    Foo() {}
  }
}
