/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.migration;

import android.app.Service;
import android.content.BroadcastReceiver;
import androidx.fragment.app.Fragment;
import android.view.View;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import dagger.hilt.android.internal.migration.InjectedByHilt;
import dagger.hilt.internal.Preconditions;

/**
 * Utility methods for validating if an {@link dagger.hilt.android.AndroidEntryPoint}-annotated
 * class that is also annotated with {@link OptionalInject} was injected by Hilt.
 *
 * @see OptionalInject
 */
public final class OptionalInjectCheck {

  /**
   * Returns true if the Activity was injected by Hilt.
   *
   * @throws IllegalArgumentException if the given instance is not an AndroidEntryPoint nor is
   *     annotated with {@link OptionalInject}.
   */
  public static boolean wasInjectedByHilt(@NonNull ComponentActivity activity) {
    return check(activity);
  }

  /**
   * Returns true if the BroadcastReceiver was injected by Hilt.
   *
   * @throws IllegalArgumentException if the given instance is not an AndroidEntryPoint nor is
   *     annotated with {@link OptionalInject}.
   */
  public static boolean wasInjectedByHilt(@NonNull BroadcastReceiver broadcastReceiver) {
    return check(broadcastReceiver);
  }

  /**
   * Returns true if the Fragment was injected by Hilt.
   *
   * @throws IllegalArgumentException if the given instance is not an AndroidEntryPoint nor is
   *     annotated with {@link OptionalInject}.
   */
  public static boolean wasInjectedByHilt(@NonNull Fragment fragment) {
    return check(fragment);
  }

  /**
   * Returns true if the Service was injected by Hilt.
   *
   * @throws IllegalArgumentException if the given instance is not an AndroidEntryPoint nor is
   *     annotated with {@link OptionalInject}.
   */
  public static boolean wasInjectedByHilt(@NonNull Service service) {
    return check(service);
  }

  /**
   * Returns true if the View was injected by Hilt.
   *
   * @throws IllegalArgumentException if the given instance is not an AndroidEntryPoint nor is
   *     annotated with {@link OptionalInject}.
   */
  public static boolean wasInjectedByHilt(@NonNull View view) {
    return check(view);
  }

  private static boolean check(@NonNull Object obj) {
    Preconditions.checkNotNull(obj);
    Preconditions.checkArgument(
        obj instanceof InjectedByHilt,
        "'%s' is not an optionally injected android entry point. Check that you have annotated"
            + " the class with both @AndroidEntryPoint and @OptionalInject.",
        obj.getClass());
    return ((InjectedByHilt) obj).wasInjectedByHilt();
  }

  private OptionalInjectCheck() {}
}
