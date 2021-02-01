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

import android.app.Application;
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModelProvider;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.activity.ComponentActivity;
import androidx.savedstate.SavedStateRegistryOwner;
import dagger.Module;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.components.FragmentComponent;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.multibindings.Multibinds;
import java.util.Set;
import javax.inject.Inject;

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
  public static ViewModelProvider.Factory getActivityFactory(ComponentActivity activity) {
    return EntryPoints.get(activity, ActivityEntryPoint.class)
        .getHiltInternalFactoryFactory()
        .fromActivity(activity);
  }

  /**
   * Retrieves the default view model factory for the activity.
   *
   * <p>Do not use except in Hilt generated code!
   */
  public static ViewModelProvider.Factory getFragmentFactory(Fragment fragment) {
    return EntryPoints.get(fragment, FragmentEntryPoint.class)
        .getHiltInternalFactoryFactory()
        .fromFragment(fragment);
  }

  /** Internal factory for the Hilt ViewModel Factory. */
  public static final class InternalFactoryFactory {

    private final Application application;
    private final Set<String> keySet;
    private final ViewModelComponentBuilder viewModelComponentBuilder;
    @Nullable private final ViewModelProvider.Factory defaultActivityFactory;
    @Nullable private final ViewModelProvider.Factory defaultFragmentFactory;

    @Inject
    InternalFactoryFactory(
            Application application,
        @HiltViewModelMap.KeySet Set<String> keySet,
        ViewModelComponentBuilder viewModelComponentBuilder,
        // These default factory bindings are temporary for the transition of deprecating
        // the Hilt ViewModel extension for the built-in support
        @DefaultActivityViewModelFactory Set<ViewModelProvider.Factory> defaultActivityFactorySet,
        @DefaultFragmentViewModelFactory Set<ViewModelProvider.Factory> defaultFragmentFactorySet) {
      this.application = application;
      this.keySet = keySet;
      this.viewModelComponentBuilder = viewModelComponentBuilder;
      this.defaultActivityFactory = getFactoryFromSet(defaultActivityFactorySet);
      this.defaultFragmentFactory = getFactoryFromSet(defaultFragmentFactorySet);
    }

    ViewModelProvider.Factory fromActivity(ComponentActivity activity) {
      return getHiltViewModelFactory(activity,
          activity.getIntent() != null ? activity.getIntent().getExtras() : null,
          defaultActivityFactory);
    }

    ViewModelProvider.Factory fromFragment(Fragment fragment) {
      return getHiltViewModelFactory(fragment, fragment.getArguments(), defaultFragmentFactory);
    }

    private ViewModelProvider.Factory getHiltViewModelFactory(
        SavedStateRegistryOwner owner,
        @Nullable Bundle defaultArgs,
        @Nullable ViewModelProvider.Factory extensionDelegate) {
      ViewModelProvider.Factory delegate = extensionDelegate == null
          ? new SavedStateViewModelFactory(application, owner, defaultArgs)
          : extensionDelegate;
      return new HiltViewModelFactory(
          owner, defaultArgs, keySet, delegate, viewModelComponentBuilder);
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
  }

  /** The activity module to declare the optional factories. */
  @Module
  @InstallIn(ActivityComponent.class)
  interface ActivityModule {
    @Multibinds
    @HiltViewModelMap.KeySet
    abstract Set<String> viewModelKeys();

    @Multibinds
    @DefaultActivityViewModelFactory
    Set<ViewModelProvider.Factory> defaultActivityViewModelFactory();

    @Multibinds
    @DefaultFragmentViewModelFactory
    Set<ViewModelProvider.Factory> defaultFragmentViewModelFactory();
  }

  /** The activity entry point to retrieve the factory. */
  @EntryPoint
  @InstallIn(ActivityComponent.class)
  public interface ActivityEntryPoint {
    InternalFactoryFactory getHiltInternalFactoryFactory();
  }

  /** The fragment entry point to retrieve the factory. */
  @EntryPoint
  @InstallIn(FragmentComponent.class)
  public interface FragmentEntryPoint {
    InternalFactoryFactory getHiltInternalFactoryFactory();
  }

  private DefaultViewModelFactories() {}
}
