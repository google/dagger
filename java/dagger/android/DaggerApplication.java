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

import android.app.Application;
import android.content.ContentProvider;
import com.google.errorprone.annotations.ForOverride;
import dagger.internal.Beta;
import javax.inject.Inject;

/**
 * An {@link Application} that injects its members and can be used to inject objects that the
 * Android framework instantiates, such as Activitys, Fragments, or Services. Injection is performed
 * in {@link #onCreate()} or the first call to {@link AndroidInjection#inject(ContentProvider)},
 * whichever happens first.
 */
@Beta
public abstract class DaggerApplication extends Application implements HasAndroidInjector {
  @Inject volatile DispatchingAndroidInjector<Object> androidInjector;

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
   * need injected members on the application. Injection is not performed in the constructor, as
   * that may result in members-injection methods being called before the constructor has completed,
   * allowing for a partially-constructed instance to escape.
   */
  private void injectIfNecessary() {
    if (androidInjector == null) {
      synchronized (this) {
        if (androidInjector == null) {
          @SuppressWarnings("unchecked")
          AndroidInjector<DaggerApplication> applicationInjector =
              (AndroidInjector<DaggerApplication>) applicationInjector();
          applicationInjector.inject(this);
          if (androidInjector == null) {
            throw new IllegalStateException(
                "The AndroidInjector returned from applicationInjector() did not inject the "
                    + "DaggerApplication");
          }
        }
      }
    }
  }

  @Override
  public AndroidInjector<Object> androidInjector() {
    // injectIfNecessary should already be called unless we are about to inject a ContentProvider,
    // which can happen before Application.onCreate()
    injectIfNecessary();

    return androidInjector;
  }
}
