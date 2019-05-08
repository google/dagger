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

import static android.util.Log.DEBUG;
import static dagger.internal.Preconditions.checkNotNull;

import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Context;
import android.util.Log;
import dagger.internal.Beta;

/** Injects core Android types. */
@Beta
public final class AndroidInjection {
  private static final String TAG = "dagger.android";

  /**
   * Injects {@code activity} if an associated {@link AndroidInjector} implementation can be found,
   * otherwise throws an {@link IllegalArgumentException}.
   *
   * @throws RuntimeException if the {@link Application} doesn't implement {@link
   *     HasAndroidInjector} or {@link HasActivityInjector}.
   */
  public static void inject(Activity activity) {
    checkNotNull(activity, "activity");
    Application application = activity.getApplication();
    AndroidInjector<? super Activity> injector;
    if (application instanceof HasAndroidInjector) {
      injector = ((HasAndroidInjector) application).androidInjector();
      checkNotNull(injector, "%s.androidInjector() returned null", application.getClass());
    } else if (application instanceof HasActivityInjector) {
      injector = ((HasActivityInjector) application).activityInjector();
      checkNotNull(injector, "%s.activityInjector() returned null", application.getClass());
    } else {
      throw new RuntimeException(
          String.format(
              "%s does not implement %s or %s",
              application.getClass().getCanonicalName(),
              HasAndroidInjector.class.getCanonicalName(),
              HasActivityInjector.class.getCanonicalName()));
    }

    injector.inject(activity);
  }

  /**
   * Injects {@code fragment} if an associated {@link AndroidInjector} implementation can be found,
   * otherwise throws an {@link IllegalArgumentException}.
   *
   * <p>Uses the following algorithm to find the appropriate {@link AndroidInjector} to use to
   * inject {@code fragment}:
   *
   * <ol>
   *   <li>Walks the parent-fragment hierarchy to find a fragment that implements {@link
   *       HasAndroidInjector} or {@link HasFragmentInjector}, and if none do
   *   <li>Uses the {@code fragment}'s {@link Fragment#getActivity() activity} if it implements
   *       {@link HasAndroidInjector} or {@link HasFragmentInjector}, and if not
   *   <li>Uses the {@link android.app.Application} if it implements {@link HasAndroidInjector}
   *       {@link HasFragmentInjector}.
   * </ol>
   *
   * If none of them implement {@link HasAndroidInjector} or {@link HasFragmentInjector}, a {@link
   * IllegalArgumentException} is thrown.
   *
   * @throws IllegalArgumentException if no parent fragment, activity, or application implements
   *     {@link HasAndroidInjector} or {@link HasFragmentInjector}.
   */
  public static void inject(Fragment fragment) {
    checkNotNull(fragment, "fragment");

    Object hasInjector = findHasFragmentInjector(fragment);
    AndroidInjector<? super Fragment> injector;
    if (hasInjector instanceof HasAndroidInjector) {
      injector = ((HasAndroidInjector) hasInjector).androidInjector();
      checkNotNull(injector, "%s.androidInjector() returned null", hasInjector.getClass());
    } else if (hasInjector instanceof HasFragmentInjector) {
      injector = ((HasFragmentInjector) hasInjector).fragmentInjector();
      checkNotNull(injector, "%s.fragmentInjector() returned null", hasInjector.getClass());
    } else {
      throw new RuntimeException(
          String.format(
              "%s does not implement %s or %s",
              hasInjector.getClass().getCanonicalName(),
              HasAndroidInjector.class.getCanonicalName(),
              HasFragmentInjector.class.getCanonicalName()));
    }

    if (Log.isLoggable(TAG, DEBUG)) {
      Log.d(
          TAG,
          String.format(
              "An injector for %s was found in %s",
              fragment.getClass().getCanonicalName(),
              hasInjector.getClass().getCanonicalName()));
    }

    injector.inject(fragment);
  }

  private static Object findHasFragmentInjector(Fragment fragment) {
    Fragment parentFragment = fragment;
    while ((parentFragment = parentFragment.getParentFragment()) != null) {
      if (parentFragment instanceof HasAndroidInjector
          || parentFragment instanceof HasFragmentInjector) {
        return parentFragment;
      }
    }
    Activity activity = fragment.getActivity();
    if (activity instanceof HasAndroidInjector || activity instanceof HasFragmentInjector) {
      return activity;
    }
    Application application = activity.getApplication();
    if (application instanceof HasAndroidInjector || application instanceof HasFragmentInjector) {
      return application;
    }
    throw new IllegalArgumentException(
        String.format("No injector was found for %s", fragment.getClass().getCanonicalName()));
  }

  /**
   * Injects {@code service} if an associated {@link AndroidInjector} implementation can be found,
   * otherwise throws an {@link IllegalArgumentException}.
   *
   * @throws RuntimeException if the {@link Application} doesn't implement {@link
   *     HasAndroidInjector} or {@link HasServiceInjector}.
   */
  public static void inject(Service service) {
    checkNotNull(service, "service");
    Application application = service.getApplication();
    AndroidInjector<? super Service> injector;
    if (application instanceof HasAndroidInjector) {
      injector = ((HasAndroidInjector) application).androidInjector();
      checkNotNull(injector, "%s.androidInjector() returned null", application.getClass());
    } else if (application instanceof HasServiceInjector) {
      injector = ((HasServiceInjector) application).serviceInjector();
      checkNotNull(injector, "%s.serviceInjector() returned null", application.getClass());
    } else {
      throw new RuntimeException(
          String.format(
              "%s does not implement %s or %s",
              application.getClass().getCanonicalName(),
              HasAndroidInjector.class.getCanonicalName(),
              HasServiceInjector.class.getCanonicalName()));
    }

    injector.inject(service);
  }

  /**
   * Injects {@code broadcastReceiver} if an associated {@link AndroidInjector} implementation can
   * be found, otherwise throws an {@link IllegalArgumentException}.
   *
   * @throws RuntimeException if the {@link Application} from {@link
   *     Context#getApplicationContext()} doesn't implement {@link HasAndroidInjector} or {@link
   *     HasBroadcastReceiverInjector}.
   */
  public static void inject(BroadcastReceiver broadcastReceiver, Context context) {
    checkNotNull(broadcastReceiver, "broadcastReceiver");
    checkNotNull(context, "context");

    Application application = (Application) context.getApplicationContext();
    AndroidInjector<? super BroadcastReceiver> injector;
    if (application instanceof HasAndroidInjector) {
      injector = ((HasAndroidInjector) application).androidInjector();
      checkNotNull(injector, "%s.androidInjector() returned null", application.getClass());
    } else if (application instanceof HasBroadcastReceiverInjector) {
      injector = ((HasBroadcastReceiverInjector) application).broadcastReceiverInjector();
      checkNotNull(
          injector, "%s.broadcastReceiverInjector() returned null", application.getClass());
    } else {
      throw new RuntimeException(
          String.format(
              "%s does not implement %s or %s",
              application.getClass().getCanonicalName(),
              HasAndroidInjector.class.getCanonicalName(),
              HasBroadcastReceiverInjector.class.getCanonicalName()));
    }

    injector.inject(broadcastReceiver);
  }

  /**
   * Injects {@code contentProvider} if an associated {@link AndroidInjector} implementation can be
   * found, otherwise throws an {@link IllegalArgumentException}.
   *
   * @throws RuntimeException if the {@link Application} doesn't implement {@link
   *     HasAndroidInjector} or {@link HasContentProviderInjector}.
   */
  public static void inject(ContentProvider contentProvider) {
    checkNotNull(contentProvider, "contentProvider");
    Application application = (Application) contentProvider.getContext().getApplicationContext();

    AndroidInjector<? super ContentProvider> injector;
    if (application instanceof HasAndroidInjector) {
      injector = ((HasAndroidInjector) application).androidInjector();
      checkNotNull(injector, "%s.androidInjector() returned null", application.getClass());
    } else if (application instanceof HasContentProviderInjector) {
      injector = ((HasContentProviderInjector) application).contentProviderInjector();
      checkNotNull(injector, "%s.contentProviderInjector() returned null", application.getClass());
    } else {
      throw new RuntimeException(
          String.format(
              "%s does not implement %s or %s",
              application.getClass().getCanonicalName(),
              HasAndroidInjector.class.getCanonicalName(),
              HasBroadcastReceiverInjector.class.getCanonicalName()));
    }

    injector.inject(contentProvider);
  }

  private AndroidInjection() {}
}
