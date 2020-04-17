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

package dagger.hilt.android.internal.lifecycle;

import androidx.lifecycle.ViewModelProvider;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.activity.ComponentActivity;
import dagger.Module;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.components.FragmentComponent;
import dagger.multibindings.Multibinds;
import java.util.Set;

/**
 * Modules and entry points for the default view model factory used by activities and fragments
 * annotated with @AndroidEntryPoint.
 *
 * <p>Entry points are used to acquire the factory because injected fields in the generated
 * activities and fragments are ignored by Dagger when using the transform due to the generated
 * class not being part of the hierarchy during compile time.
 */
public final class DefaultViewModelFactories {

  /**
   * Retrieves the default view model factory for the activity.
   *
   * <p>Do not use except in Hilt generated code!
   */
  @Nullable
  public static ViewModelProvider.Factory getActivityFactory(ComponentActivity activity) {
    return getFactoryFromSet(
        EntryPoints.get(activity, ActivityEntryPoint.class).getActivityViewModelFactory());
  }

  /**
   * Retrieves the default view model factory for the activity.
   *
   * <p>Do not use except in Hilt generated code!
   */
  @Nullable
  public static ViewModelProvider.Factory getFragmentFactory(Fragment fragment) {
    return getFactoryFromSet(
        EntryPoints.get(fragment, FragmentEntryPoint.class).getFragmentViewModelFactory());
  }

  @Nullable
  private static ViewModelProvider.Factory getFactoryFromSet(Set<ViewModelProvider.Factory> set) {
    // A multibinding set is used instead of BindsOptionalOf because Optional is not available in
    // Android until API 24 and we don't want to have Guava as a transitive dependency.
    if (set.isEmpty()) {
      return null;
    }
    if (set.size() > 1) {
      throw new IllegalStateException(
          "At most one default view model factory is expected. Found " + set);
    }
    ViewModelProvider.Factory factory = set.iterator().next();
    if (factory == null) {
      throw new IllegalStateException("Default view model factory must not be null.");
    }
    return factory;
  }

  /** The activity module to declare the optional factory. */
  @Module
  @InstallIn(ActivityComponent.class)
  public interface ActivityModule {
    @Multibinds
    @DefaultActivityViewModelFactory
    Set<ViewModelProvider.Factory> defaultViewModelFactory();
  }

  /** The activity entry point to retrieve the factory. */
  @EntryPoint
  @InstallIn(ActivityComponent.class)
  public interface ActivityEntryPoint {
    @DefaultActivityViewModelFactory
    Set<ViewModelProvider.Factory> getActivityViewModelFactory();
  }

  /** The fragment module to declare the optional factory. */
  @Module
  @InstallIn(FragmentComponent.class)
  public interface FragmentModule {
    @Multibinds
    @DefaultFragmentViewModelFactory
    Set<ViewModelProvider.Factory> defaultViewModelFactory();
  }

  /** The fragment entry point to retrieve the factory. */
  @EntryPoint
  @InstallIn(FragmentComponent.class)
  public interface FragmentEntryPoint {
    @DefaultFragmentViewModelFactory
    Set<ViewModelProvider.Factory> getFragmentViewModelFactory();
  }

  private DefaultViewModelFactories() {}
}
