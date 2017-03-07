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

import static dagger.internal.Preconditions.checkNotNull;

import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;
import dagger.internal.Beta;

/** Injects core Android types. */
@Beta
public final class AndroidInjection {
  private static final String TAG = "dagger.android";

  /**
   * Injects {@code activity} if an associated {@link AndroidInjector.Factory} implementation can be
   * found, otherwise throws an {@link IllegalArgumentException}.
   *
   * @throws RuntimeException if the {@link Application} doesn't implement {@link
   *     HasDispatchingActivityInjector}, or if no {@code AndroidInjector.Factory<? extends
   *     Activity>} is bound for {@code activity}.
   */
  public static void inject(Activity activity) {
    checkNotNull(activity, "activity");
    Application application = activity.getApplication();
    if (!(application instanceof HasDispatchingActivityInjector)) {
      throw new RuntimeException(
          String.format(
              "%s does not implement %s",
              application.getClass().getCanonicalName(),
              HasDispatchingActivityInjector.class.getCanonicalName()));
    }

    DispatchingAndroidInjector<Activity> activityInjector =
        ((HasDispatchingActivityInjector) application).activityInjector();
    checkNotNull(
        activityInjector,
        "%s.activityInjector() returned null",
        application.getClass().getCanonicalName());

    activityInjector.inject(activity);
  }

  /**
   * Injects {@code fragment} if an associated {@link AndroidInjector.Factory} implementation can be
   * found, otherwise throws an {@link IllegalArgumentException}.
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
   * @throws IllegalArgumentException if no {@code AndroidInjector.Factory<? extends Fragment>} is
   *     bound for {@code fragment}.
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

  /**
   * Injects {@code service} if an associated {@link AndroidInjector.Factory} implementation can be
   * found, otherwise throws an {@link IllegalArgumentException}.
   *
   * @throws RuntimeException if the {@link Application} doesn't implement {@link
   *     HasDispatchingServiceInjector}, or if no {@code AndroidInjector.Factory<? extends Service>}
   *     is bound for {@code service}.
   */
  public static void inject(Service service) {
    checkNotNull(service, "service");
    Application application = service.getApplication();
    if (!(application instanceof HasDispatchingServiceInjector)) {
      throw new RuntimeException(
          String.format(
              "%s does not implement %s",
              application.getClass().getCanonicalName(),
              HasDispatchingServiceInjector.class.getCanonicalName()));
    }

    DispatchingAndroidInjector<Service> serviceInjector =
        ((HasDispatchingServiceInjector) application).serviceInjector();
    checkNotNull(
        serviceInjector,
        "%s.serviceInjector() returned null",
        application.getClass().getCanonicalName());

    serviceInjector.inject(service);
  }

  /**
   * Injects {@code broadcastReceiver} if an associated {@link AndroidInjector.Factory}
   * implementation can be found, otherwise throws an {@link IllegalArgumentException}.
   *
   * @throws RuntimeException if the {@link Application} from {@link
   *     Context#getApplicationContext()} doesn't implement {@link
   *     HasDispatchingBroadcastReceiverInjector}, or if no {@code AndroidInjector.Factory<? extends
   *     BroadcastReceiver>} is bound for {@code broadcastReceiver}.
   */
  public static void inject(BroadcastReceiver broadcastReceiver, Context context) {
    checkNotNull(broadcastReceiver, "broadcastReceiver");
    checkNotNull(context, "context");
    Application application = (Application) context.getApplicationContext();
    if (!(application instanceof HasDispatchingBroadcastReceiverInjector)) {
      throw new RuntimeException(
          String.format(
              "%s does not implement %s",
              application.getClass().getCanonicalName(),
              HasDispatchingBroadcastReceiverInjector.class.getCanonicalName()));
    }

    DispatchingAndroidInjector<BroadcastReceiver> broadcastReceiverInjector =
        ((HasDispatchingBroadcastReceiverInjector) application).broadcastReceiverInjector();
    checkNotNull(
        broadcastReceiverInjector,
        "%s.broadcastReceiverInjector() returned null",
        application.getClass().getCanonicalName());

    broadcastReceiverInjector.inject(broadcastReceiver);
  }

  private AndroidInjection() {}
}
