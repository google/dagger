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

package dagger.hilt.android.internal.modules;

import android.app.Activity;
import android.content.Context;
import androidx.fragment.app.FragmentActivity;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.Reusable;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.qualifiers.ActivityContext;

/** Provides convenience bindings for activities. */
@Module
@InstallIn(ActivityComponent.class)
abstract class ActivityModule {
  @Binds
  @ActivityContext
  abstract Context provideContext(Activity activity);

  @Provides
  @Reusable
  static FragmentActivity provideFragmentActivity(Activity activity) {
    try {
      return (FragmentActivity) activity;
    } catch (ClassCastException e) {
      throw new IllegalStateException("Expected activity to be a FragmentActivity: " + activity, e);
    }
  }

  private ActivityModule() {}
}
