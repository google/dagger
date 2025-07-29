/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.android

import android.app.Activity
import android.content.Context
import androidx.fragment.app.Fragment
import android.view.View
import dagger.hilt.EntryPoints
import dagger.hilt.android.internal.Contexts

/** Utility functions for dealing with entry points for standard Android components. */
object EntryPointAccessors {
  /**
   * Returns the entry point interface from an application. The context can be any context derived
   * from the application context. May only be used with entry point interfaces installed in the
   * SingletonComponent.
   */
  @JvmStatic
  fun <T> fromApplication(context: Context, entryPoint: Class<T>): T =
    EntryPoints.get(Contexts.getApplication(context.applicationContext), entryPoint)

  /**
   * Returns the entry point interface from an application. The context can be any context derived
   * from the application context. May only be used with entry point interfaces installed in the
   * SingletonComponent.
   */
  inline fun <reified T> fromApplication(context: Context): T =
    fromApplication(context, T::class.java)

  /**
   * Returns the entry point interface from an activity. May only be used with entry point
   * interfaces installed in the ActivityComponent.
   */
  @JvmStatic
  fun <T> fromActivity(activity: Activity, entryPoint: Class<T>): T =
    EntryPoints.get(activity, entryPoint)

  /**
   * Returns the entry point interface from an activity. May only be used with entry point
   * interfaces installed in the ActivityComponent.
   */
  inline fun <reified T> fromActivity(activity: Activity): T =
    fromActivity(activity, T::class.java)

  /**
   * Returns the entry point interface from a fragment. May only be used with entry point interfaces
   * installed in the FragmentComponent.
   */
  @JvmStatic
  fun <T> fromFragment(fragment: Fragment, entryPoint: Class<T>): T =
    EntryPoints.get(fragment, entryPoint)

  /**
   * Returns the entry point interface from a fragment. May only be used with entry point interfaces
   * installed in the FragmentComponent.
   */
  inline fun <reified T> fromFragment(fragment: Fragment): T =
    fromFragment(fragment, T::class.java)

  /**
   * Returns the entry point interface from a view. May only be used with entry point interfaces
   * installed in the ViewComponent or ViewNoFragmentComponent.
   */
  @JvmStatic
  fun <T> fromView(view: View, entryPoint: Class<T>): T = EntryPoints.get(view, entryPoint)

  /**
   * Returns the entry point interface from a view. May only be used with entry point interfaces
   * installed in the ViewComponent or ViewNoFragmentComponent.
   */
  inline fun <reified T> fromView(view: View): T =
    fromView(view, T::class.java)
}
