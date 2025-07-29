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
import androidx.fragment.app.Fragment;
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
   * <p>Uses the following algorithm to find the appropriate {@code AndroidInjector<Fragment>} to
   * use to inject {@code fragment}:
   *
   * <ol>
   *   <li>Walks the parent-fragment hierarchy to find the a fragment that implements {@link
   *       HasAndroidInjector}, and if none do
   *   <li>Uses the {@code fragment}'s {@link Fragment#getActivity() activity} if it implements
   *       {@link HasAndroidInjector}, and if not
   *   <li>Uses the {@link android.app.Application} if it implements {@link HasAndroidInjector}.
   * </ol>
   *
   * If none of them implement {@link HasAndroidInjector}, a {@link IllegalArgumentException} is
   * thrown.
   *
   * @throws IllegalArgumentException if no parent fragment, activity, or application implements
   *     {@link HasAndroidInjector}.
   */
  public static void inject(Fragment fragment) {
    checkNotNull(fragment, "fragment");
    HasAndroidInjector hasAndroidInjector = findHasAndroidInjectorForFragment(fragment);
    if (Log.isLoggable(TAG, DEBUG)) {
      Log.d(
          TAG,
          String.format(
              "An injector for %s was found in %s",
              fragment.getClass().getCanonicalName(),
              hasAndroidInjector.getClass().getCanonicalName()));
    }

    inject(fragment, hasAndroidInjector);
  }

  private static void inject(Object target, HasAndroidInjector hasAndroidInjector) {
    AndroidInjector<Object> androidInjector = hasAndroidInjector.androidInjector();
    checkNotNull(
        androidInjector, "%s.androidInjector() returned null", hasAndroidInjector.getClass());

    androidInjector.inject(target);
  }

  private static HasAndroidInjector findHasAndroidInjectorForFragment(Fragment fragment) {
    Fragment parentFragment = fragment;
    while ((parentFragment = parentFragment.getParentFragment()) != null) {
      if (parentFragment instanceof HasAndroidInjector) {
        return (HasAndroidInjector) parentFragment;
      }
    }
    Activity activity = fragment.getActivity();
    if (activity instanceof HasAndroidInjector) {
      return (HasAndroidInjector) activity;
    }
    if (activity.getApplication() instanceof HasAndroidInjector) {
      return (HasAndroidInjector) activity.getApplication();
    }
    throw new IllegalArgumentException(
        String.format("No injector was found for %s", fragment.getClass().getCanonicalName()));
  }

  private AndroidSupportInjection() {}
}
