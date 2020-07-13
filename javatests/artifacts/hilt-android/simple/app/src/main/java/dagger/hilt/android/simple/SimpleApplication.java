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

package dagger.hilt.android.simple;

import android.app.Application;
import android.util.Log;
import dagger.hilt.android.HiltAndroidApp;
import javax.inject.Inject;

/**
 * A simple, skeletal application that demonstrates a dependency-injected application using the
 * utilities in {@code Hilt} in Android.
 */
@HiltAndroidApp
public class SimpleApplication extends Application {
  private static final String TAG = SimpleApplication.class.getSimpleName();

  @Inject @Model String model;

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "Injected with model: " + model);
  }
}
