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

package dagger.android;

import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import com.google.errorprone.annotations.ForOverride;
import dagger.internal.Beta;
import javax.inject.Inject;

/**
 * An {@link Application} that injects its members and can be used to inject {@link Activity}s,
 * {@link Fragment}s, {@link Service}s, {@link BroadcastReceiver}s and {@link ContentProvider}s
 * attached to it. Injection is performed in {@link #onCreate()} or the first call to {@link
 * AndroidInjection#inject(ContentProvider)}, whichever happens first.
 */
@Beta
public abstract class DaggerApplication extends Application
    implements HasActivityInjector,
        HasFragmentInjector,
        HasServiceInjector,
        HasBroadcastReceiverInjector,
        HasContentProviderInjector {

  @Inject DispatchingAndroidInjector<Activity> activityInjector;
  @Inject DispatchingAndroidInjector<BroadcastReceiver> broadcastReceiverInjector;
  @Inject DispatchingAndroidInjector<Fragment> fragmentInjector;
  @Inject DispatchingAndroidInjector<Service> serviceInjector;
  @Inject DispatchingAndroidInjector<ContentProvider> contentProviderInjector;
  private volatile boolean needToInject = true;

  @Override
  public void onCreate() {
    super.onCreate();
    injectIfNecessary();
  }

  /**
   * Implementations should return an {@link AndroidInjector} for the concrete {@link
   * DaggerApplication}. Typically, that injector is a {@link dagger.Component}.
   */
  @ForOverride
  protected abstract AndroidInjector<? extends DaggerApplication> applicationInjector();

  /**
   * Lazily injects the {@link DaggerApplication}'s members. Injection cannot be performed in {@link
   * Application#onCreate()} since {@link android.content.ContentProvider}s' {@link
   * android.content.ContentProvider#onCreate() onCreate()} method will be called first and might
   * need injected members on the application. Injection is not performed in the the constructor, as
   * that may result in members-injection methods being called before the constructor has completed,
   * allowing for a partially-constructed instance to escape.
   */
  private void injectIfNecessary() {
    if (needToInject) {
      synchronized (this) {
        if (needToInject) {
          @SuppressWarnings("unchecked")
          AndroidInjector<DaggerApplication> applicationInjector =
              (AndroidInjector<DaggerApplication>) applicationInjector();
          applicationInjector.inject(this);
          if (needToInject) {
            throw new IllegalStateException(
                "The AndroidInjector returned from applicationInjector() did not inject the "
                    + "DaggerApplication");
          }
        }
      }
    }
  }

  @Inject
  void setInjected() {
    needToInject = false;
  }

  @Override
  public DispatchingAndroidInjector<Activity> activityInjector() {
    return activityInjector;
  }

  @Override
  public DispatchingAndroidInjector<Fragment> fragmentInjector() {
    return fragmentInjector;
  }

  @Override
  public DispatchingAndroidInjector<BroadcastReceiver> broadcastReceiverInjector() {
    return broadcastReceiverInjector;
  }

  @Override
  public DispatchingAndroidInjector<Service> serviceInjector() {
    return serviceInjector;
  }

  // injectIfNecessary is called here but not on the other *Injector() methods because it is the
  // only one that should be called (in AndroidInjection.inject(ContentProvider)) before
  // Application.onCreate()
  @Override
  public AndroidInjector<ContentProvider> contentProviderInjector() {
    injectIfNecessary();
    return contentProviderInjector;
  }
}
