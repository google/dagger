/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.android.support;

import android.support.v4.app.Fragment;
import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import javax.inject.Inject;

/**
 * An {@link android.app.Application} that injects its members and can be used to inject {@link
 * android.app.Activity}s, {@linkplain android.app.Fragment framework fragments}, {@linkplain
 * Fragment support fragments}, {@link android.app.Service}s, {@link
 * android.content.BroadcastReceiver}s, and {@link android.content.ContentProvider}s attached to it.
 */
public abstract class DaggerApplication extends dagger.android.DaggerApplication
    implements HasSupportFragmentInjector {

  @Inject DispatchingAndroidInjector<Fragment> supportFragmentInjector;

  @Override
  protected abstract AndroidInjector<? extends DaggerApplication> applicationInjector();

  @Override
  public DispatchingAndroidInjector<Fragment> supportFragmentInjector() {
    return supportFragmentInjector;
  }
}
