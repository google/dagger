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

package dagger.hilt.android.internal.modules;

import android.app.Activity;
import android.app.Application;
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.components.ActivityRetainedComponent;
import dagger.hilt.android.components.FragmentComponent;
import dagger.hilt.android.components.ViewModelComponent;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultActivityViewModelFactory;
import dagger.hilt.android.internal.lifecycle.DefaultFragmentViewModelFactory;
import dagger.hilt.android.internal.lifecycle.HiltViewModelMap;
import dagger.hilt.android.lifecycle.HiltViewModelFactory;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;
import java.util.Map;
import java.util.Set;

/** Hilt Modules for providing ViewModel factories. */
public final class ViewModelFactoryModules {

  /** Hilt module for providing the empty multi-binding map of ViewModels. */
  @Module
  @InstallIn(ViewModelComponent.class)
  public abstract static class ViewModelModule {
    @NonNull
    @Multibinds
    @HiltViewModelMap
    abstract Map<String, ViewModel> viewModelFactoriesMap();
  }

  /**
   * Hilt module for providing the empty multi-binding map of @ViewModelInject-annotated classes
   * names.
   */
  @Module
  @InstallIn(ActivityRetainedComponent.class)
  public abstract static class ActivityRetainedModule {
    @NonNull
    @Multibinds
    @HiltViewModelMap.KeySet
    abstract Set<String> viewModelKeys();
  }

  /** Hilt module for providing the activity level ViewModelFactory */
  @Module
  @InstallIn(ActivityComponent.class)
  public static class ActivityModule {

    @Provides
    @IntoSet
    @NonNull
    @DefaultActivityViewModelFactory
    static ViewModelProvider.Factory provideFactory(
        @NonNull Activity activity,
            @NonNull
            Application application,
        @NonNull @HiltViewModelMap.KeySet Set<String> keySet,
        @NonNull ViewModelComponentBuilder componentBuilder) {
      // Hilt guarantees concrete activity is a subclass of ComponentActivity.
      ComponentActivity componentActivity = (ComponentActivity) activity;
      Bundle defaultArgs = activity.getIntent() != null ? activity.getIntent().getExtras() : null;
      SavedStateViewModelFactory delegate =
          new SavedStateViewModelFactory(application, componentActivity, defaultArgs);
      return new HiltViewModelFactory(
          componentActivity, defaultArgs, keySet, delegate, componentBuilder);
    }

    private ActivityModule() {}
  }

  /** Hilt module for providing the fragment level ViewModelFactory */
  @Module
  @InstallIn(FragmentComponent.class)
  public static final class FragmentModule {

    @Provides
    @IntoSet
    @NonNull
    @DefaultFragmentViewModelFactory
    static ViewModelProvider.Factory provideFactory(
        @NonNull Fragment fragment,
            @NonNull
            Application application,
        @NonNull @HiltViewModelMap.KeySet Set<String> keySet,
        @NonNull ViewModelComponentBuilder componentBuilder) {
      Bundle defaultArgs = fragment.getArguments();
      SavedStateViewModelFactory delegate =
          new SavedStateViewModelFactory(application, fragment, defaultArgs);
      return new HiltViewModelFactory(fragment, defaultArgs, keySet, delegate, componentBuilder);
    }

    private FragmentModule() {}
  }

  private ViewModelFactoryModules() {}
}
