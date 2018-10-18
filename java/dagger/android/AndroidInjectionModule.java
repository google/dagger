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

package dagger.android;

import android.app.Activity;
import android.app.Fragment;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import dagger.Module;
import dagger.internal.Beta;
import dagger.multibindings.Multibinds;
import java.util.Map;

/**
 * Contains bindings to ensure the usability of {@code dagger.android} framework classes. This
 * module should be installed in the component that is used to inject the {@link
 * android.app.Application} class.
 */
@Beta
@Module
public abstract class AndroidInjectionModule {
  @Multibinds
  abstract Map<Class<? extends Activity>, AndroidInjector.Factory<? extends Activity>>
      activityInjectorFactories();

  @Multibinds
  abstract Map<String, AndroidInjector.Factory<? extends Activity>>
      activityInjectorFactoriesWithStringKeys();

  @Multibinds
  abstract Map<Class<? extends Fragment>, AndroidInjector.Factory<? extends Fragment>>
      fragmentInjectorFactories();

  @Multibinds
  abstract Map<String, AndroidInjector.Factory<? extends Fragment>>
      fragmentInjectorFactoriesWithStringKeys();

  @Multibinds
  abstract Map<Class<? extends Service>, AndroidInjector.Factory<? extends Service>>
      serviceInjectorFactories();

  @Multibinds
  abstract Map<String, AndroidInjector.Factory<? extends Service>>
      serviceInjectorFactoriesWithStringKeys();

  @Multibinds
  abstract Map<
          Class<? extends BroadcastReceiver>, AndroidInjector.Factory<? extends BroadcastReceiver>>
      broadcastReceiverInjectorFactories();

  @Multibinds
  abstract Map<String, AndroidInjector.Factory<? extends BroadcastReceiver>>
      broadcastReceiverInjectorFactoriesWithStringKeys();

  @Multibinds
  abstract Map<Class<? extends ContentProvider>, AndroidInjector.Factory<? extends ContentProvider>>
      contentProviderInjectorFactories();

  @Multibinds
  abstract Map<String, AndroidInjector.Factory<? extends ContentProvider>>
      contentProviderInjectorFactoriesWithStringKeys();

  private AndroidInjectionModule() {}
}
