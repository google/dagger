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

import static dagger.hilt.internal.Preconditions.checkNotNull;

import androidx.fragment.app.Fragment;
import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;
import dagger.Module;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.components.FragmentComponent;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.multibindings.Multibinds;
import java.util.Map;
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
  public static ViewModelProvider.Factory getActivityFactory(ComponentActivity activity,
      ViewModelProvider.Factory delegateFactory) {
    return EntryPoints.get(activity, ActivityEntryPoint.class)
        .getHiltInternalFactoryFactory()
        .fromActivity(activity, delegateFactory);
  }

  /**
   * Retrieves the default view model factory for the activity.
   *
   * <p>Do not use except in Hilt generated code!
   */
  public static ViewModelProvider.Factory getFragmentFactory(
      Fragment fragment, ViewModelProvider.Factory delegateFactory) {
    return EntryPoints.get(fragment, FragmentEntryPoint.class)
        .getHiltInternalFactoryFactory()
        .fromFragment(fragment, delegateFactory);
  }

  /** Internal factory for the Hilt ViewModel Factory. */
  public static final class InternalFactoryFactory {

    private final Map<Class<?>, Boolean> keySet;
    private final ViewModelComponentBuilder viewModelComponentBuilder;

    @Inject
    InternalFactoryFactory(
        @HiltViewModelMap.KeySet Map<Class<?>, Boolean> keySet,
        ViewModelComponentBuilder viewModelComponentBuilder) {
      this.keySet = keySet;
      this.viewModelComponentBuilder = viewModelComponentBuilder;
    }

    ViewModelProvider.Factory fromActivity(
        ComponentActivity activity, ViewModelProvider.Factory delegateFactory) {
      return getHiltViewModelFactory(
          delegateFactory);
    }

    ViewModelProvider.Factory fromFragment(
        Fragment fragment, ViewModelProvider.Factory delegateFactory) {
      return getHiltViewModelFactory(delegateFactory);
    }

    private ViewModelProvider.Factory getHiltViewModelFactory(
        ViewModelProvider.Factory delegate) {
      return new HiltViewModelFactory(keySet, checkNotNull(delegate), viewModelComponentBuilder);
    }
  }

  /** The activity module to declare the optional factories. */
  @Module
  @InstallIn(ActivityComponent.class)
  interface ActivityModule {
    @Multibinds
    @HiltViewModelMap.KeySet
    abstract Map<Class<?>, Boolean> viewModelKeys();
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
