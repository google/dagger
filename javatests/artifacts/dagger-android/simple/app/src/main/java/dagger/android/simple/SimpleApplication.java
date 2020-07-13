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

package dagger.android.simple;

import android.util.Log;
import dagger.Component;
import dagger.android.AndroidInjectionModule;
import dagger.android.AndroidInjector;
import dagger.android.DaggerApplication;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A simple, skeletal application that demonstrates a dependency-injected application using the
 * utilities in {@code dagger.android}.
 */
public class SimpleApplication extends DaggerApplication {
  private static final String TAG = SimpleApplication.class.getSimpleName();

  @Singleton
  @Component(
      modules = {
        AndroidInjectionModule.class,
        SimpleActivity.InjectorModule.class,
        ModelModule.class
      }
  )
  interface SimpleComponent extends AndroidInjector<SimpleApplication> {
    @Component.Factory
    interface Factory extends AndroidInjector.Factory<SimpleApplication> {}
  }

  @Inject @Model String model;

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "Injected with model: " + model);
  }

  @Override
  protected AndroidInjector<SimpleApplication> applicationInjector() {
    return DaggerSimpleApplication_SimpleComponent.factory().create(this);
  }
}
