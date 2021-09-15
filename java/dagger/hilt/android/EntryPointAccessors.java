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

package dagger.hilt.android;

import android.app.Activity;
import android.content.Context;
import androidx.fragment.app.Fragment;
import android.view.View;
import dagger.hilt.EntryPoints;
import dagger.hilt.android.internal.Contexts;
import javax.annotation.Nonnull;

/** Static utility methods for dealing with entry points for standard Android components. */
public final class EntryPointAccessors {

  /**
   * Returns the entry point interface from an application. The context can be any context derived
   * from the application context. May only be used with entry point interfaces installed in the
   * SingletonComponent.
   */
  @Nonnull
  public static <T> T fromApplication(Context context, Class<T> entryPoint) {
    return EntryPoints.get(Contexts.getApplication(context.getApplicationContext()), entryPoint);
  }

  /**
   * Returns the entry point interface from an activity. May only be used with entry point
   * interfaces installed in the ActivityComponent.
   */
  @Nonnull
  public static <T> T fromActivity(Activity activity, Class<T> entryPoint) {
    return EntryPoints.get(activity, entryPoint);
  }

  /**
   * Returns the entry point interface from a fragment. May only be used with entry point interfaces
   * installed in the FragmentComponent.
   */
  @Nonnull
  public static <T> T fromFragment(Fragment fragment, Class<T> entryPoint) {
    return EntryPoints.get(fragment, entryPoint);
  }

  /**
   * Returns the entry point interface from a view. May only be used with entry point interfaces
   * installed in the ViewComponent or ViewNoFragmentComponent.
   */
  @Nonnull
  public static <T> T fromView(View view, Class<T> entryPoint) {
    return EntryPoints.get(view, entryPoint);
  }

  private EntryPointAccessors() {}
}
