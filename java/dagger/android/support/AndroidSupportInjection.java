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

import static android.util.Log.DEBUG;
import static dagger.internal.Preconditions.checkNotNull;

import android.app.Activity;
import android.app.Application;
import android.support.v4.app.Fragment;
import android.util.Log;
import dagger.android.AndroidInjector;
import dagger.android.HasAndroidInjector;
import dagger.internal.Beta;

/** Injects core Android types from support libraries. */
@Beta
public final class AndroidSupportInjection {
  private static final String TAG = "dagger.android.support";

  /**
   * Injects {@code fragment} if an associated {@link AndroidInjector} implementation can be found,
   * otherwise throws an {@link IllegalArgumentException}.
   *
   * <p>Uses the following algorithm to find the appropriate {@link AndroidInjector} to use to
   * inject {@code fragment}:
   *
   * <ol>
   *   <li>Walks the parent-fragment hierarchy to find a fragment that implements {@link
   *       HasAndroidInjector} or {@link HasSupportFragmentInjector}, and if none do
   *   <li>Uses the {@code fragment}'s {@link Fragment#getActivity() activity} if it implements
   *       {@link HasAndroidInjector} or {@link HasSupportFragmentInjector}, and if not
   *   <li>Uses the {@link android.app.Application} if it implements {@link HasAndroidInjector}
   *       {@link HasSupportFragmentInjector}.
   * </ol>
   *
   * If none of them implement {@link HasAndroidInjector} or {@link HasSupportFragmentInjector}, a
   * {@link IllegalArgumentException} is thrown.
   *
   * @throws IllegalArgumentException if no parent fragment, activity, or application implements
   *     {@link HasAndroidInjector} or {@link HasSupportFragmentInjector}.
   */
  public static void inject(Fragment fragment) {
    checkNotNull(fragment, "fragment");

    Object hasInjector = findHasSupportFragmentInjector(fragment);
    AndroidInjector<? super Fragment> injector;
    if (hasInjector instanceof HasAndroidInjector) {
      injector = ((HasAndroidInjector) hasInjector).androidInjector();
      checkNotNull(injector, "%s.androidInjector() returned null", hasInjector.getClass());
    } else if (hasInjector instanceof HasSupportFragmentInjector) {
      injector = ((HasSupportFragmentInjector) hasInjector).supportFragmentInjector();
      checkNotNull(injector, "%s.supportFragmentInjector() returned null", hasInjector.getClass());
    } else {
      throw new RuntimeException(
          String.format(
              "%s does not implement %s or %s",
              hasInjector.getClass().getCanonicalName(),
              HasAndroidInjector.class.getCanonicalName(),
              HasSupportFragmentInjector.class.getCanonicalName()));
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

  private static Object findHasSupportFragmentInjector(Fragment fragment) {
    Fragment parentFragment = fragment;
    while ((parentFragment = parentFragment.getParentFragment()) != null) {
      if (parentFragment instanceof HasAndroidInjector
          || parentFragment instanceof HasSupportFragmentInjector) {
        return parentFragment;
      }
    }
    Activity activity = fragment.getActivity();
    if (activity instanceof HasAndroidInjector || activity instanceof HasSupportFragmentInjector) {
      return activity;
    }
    Application application = activity.getApplication();
    if (application instanceof HasAndroidInjector
        || application instanceof HasSupportFragmentInjector) {
      return application;
    }
    throw new IllegalArgumentException(
        String.format("No injector was found for %s", fragment.getClass().getCanonicalName()));
  }

  private AndroidSupportInjection() {}
}
