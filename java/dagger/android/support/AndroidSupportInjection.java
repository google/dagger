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

import static dagger.internal.Preconditions.checkNotNull;

import android.app.Activity;
import android.support.v4.app.Fragment;
import android.util.Log;
import dagger.android.DispatchingAndroidInjector;
import dagger.internal.Beta;

/** Injects core Android types from support libraries. */
@Beta
public final class AndroidSupportInjection {
  private static final String TAG = "dagger.android.support";

  /**
   * Injects {@code fragment} if an associated {@link dagger.android.AndroidInjector.Factory}
   * implementation can be found, otherwise throws an {@link IllegalArgumentException}.
   *
   * <p>Uses the following algorithm to find the appropriate {@code
   * DispatchingAndroidInjector<Fragment>} to inject {@code fragment}:
   *
   * <ol>
   *   <li>Walks the parent-fragment hierarchy to find the a fragment that implements {@link
   *       HasDispatchingFragmentInjector}, and if none do
   *   <li>Uses the {@code fragment}'s {@link Fragment#getActivity() activity} if it implements
   *       {@link HasDispatchingFragmentInjector}, and if not
   *   <li>Uses the {@link android.app.Application} if it implements {@link
   *       HasDispatchingFragmentInjector}.
   * </ol>
   *
   * If none of them implement {@link HasDispatchingFragmentInjector}, a {@link
   * IllegalArgumentException} is thrown.
   *
   * @throws IllegalArgumentException if no {@code AndroidInjector.Factory<Fragment, ?>} is bound
   *     for {@code fragment}.
   */
  public static void inject(Fragment fragment) {
    checkNotNull(fragment, "fragment");
    HasDispatchingFragmentInjector hasDispatchingFragmentInjector =
        findHasFragmentInjector(fragment);
    Log.d(
        TAG,
        String.format(
            "An injector for %s was found in %s",
            fragment.getClass().getCanonicalName(),
            hasDispatchingFragmentInjector.getClass().getCanonicalName()));

    DispatchingAndroidInjector<Fragment> fragmentInjector =
        hasDispatchingFragmentInjector.fragmentInjector();
    checkNotNull(
        fragmentInjector,
        "%s.fragmentInjector() returned null",
        hasDispatchingFragmentInjector.getClass().getCanonicalName());

    fragmentInjector.inject(fragment);
  }

  private static HasDispatchingFragmentInjector findHasFragmentInjector(Fragment fragment) {
    Fragment parentFragment = fragment;
    while ((parentFragment = parentFragment.getParentFragment()) != null) {
      if (parentFragment instanceof HasDispatchingFragmentInjector) {
        return (HasDispatchingFragmentInjector) parentFragment;
      }
    }
    Activity activity = fragment.getActivity();
    if (activity instanceof HasDispatchingFragmentInjector) {
      return (HasDispatchingFragmentInjector) activity;
    }
    if (activity.getApplication() instanceof HasDispatchingFragmentInjector) {
      return (HasDispatchingFragmentInjector) activity.getApplication();
    }
    throw new IllegalArgumentException(
        String.format("No injector was found for %s", fragment.getClass().getCanonicalName()));
  }

  private AndroidSupportInjection() {}
}
