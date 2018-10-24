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

package dagger.android.androidx;

import static android.util.Log.DEBUG;
import static dagger.internal.Preconditions.checkNotNull;

import android.app.Activity;
import android.util.Log;

import androidx.fragment.app.Fragment;
import dagger.android.AndroidInjector;
import dagger.android.support.HasSupportFragmentInjector;
import dagger.internal.Beta;

/** Injects core Android types from support libraries. */
@Beta
public final class AndroidxInjection {
  private static final String TAG = "dagger.android.androidx";

  /**
   * Injects {@code fragment} if an associated {@link AndroidInjector} implementation
   * can be found, otherwise throws an {@link IllegalArgumentException}.
   *
   * <p>Uses the following algorithm to find the appropriate {@code AndroidInjector<Fragment>} to
   * use to inject {@code fragment}:
   *
   * <ol>
   *   <li>Walks the parent-fragment hierarchy to find the a fragment that implements {@link
   *       HasAndroidxFragmentInjector}, and if none do
   *   <li>Uses the {@code fragment}'s {@link Fragment#getActivity() activity} if it implements
   *       {@link HasAndroidxFragmentInjector}, and if not
   *   <li>Uses the {@link android.app.Application} if it implements {@link
   *       HasSupportFragmentInjector}.
   * </ol>
   *
   * If none of them implement {@link HasAndroidxFragmentInjector}, a {@link
   * IllegalArgumentException} is thrown.
   *
   * @throws IllegalArgumentException if no parent fragment, activity, or application implements
   *     {@link HasSupportFragmentInjector}.
   */
  public static void inject(Fragment fragment) {
    checkNotNull(fragment, "fragment");
    HasAndroidxFragmentInjector hasAndroidxFragmentInjector = findHasFragmentInjector(fragment);
    if (Log.isLoggable(TAG, DEBUG)) {
      Log.d(
          TAG,
          String.format(
              "An injector for %s was found in %s",
              fragment.getClass().getCanonicalName(),
                  hasAndroidxFragmentInjector.getClass().getCanonicalName()));
    }

    AndroidInjector<Fragment> fragmentInjector =
            hasAndroidxFragmentInjector.androidxFragmentInjector();
    checkNotNull(
        fragmentInjector,
        "%s.supportFragmentInjector() returned null",
            hasAndroidxFragmentInjector.getClass());

    fragmentInjector.inject(fragment);
  }

  private static HasAndroidxFragmentInjector findHasFragmentInjector(Fragment fragment) {
    Fragment parentFragment = fragment;
    while ((parentFragment = parentFragment.getParentFragment()) != null) {
      if (parentFragment instanceof HasSupportFragmentInjector) {
        return (HasAndroidxFragmentInjector) parentFragment;
      }
    }
    Activity activity = fragment.getActivity();
    if (activity instanceof HasSupportFragmentInjector) {
      return (HasAndroidxFragmentInjector) activity;
    }
    if (activity.getApplication() instanceof HasSupportFragmentInjector) {
      return (HasAndroidxFragmentInjector) activity.getApplication();
    }
    throw new IllegalArgumentException(
        String.format("No injector was found for %s", fragment.getClass().getCanonicalName()));
  }

  private AndroidxInjection() {}
}
