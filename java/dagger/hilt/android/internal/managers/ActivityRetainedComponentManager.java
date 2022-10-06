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

package dagger.hilt.android.internal.managers;

import android.content.Context;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.android.components.ActivityRetainedComponent;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.lifecycle.RetainedLifecycleImpl;
import dagger.hilt.android.scopes.ActivityRetainedScoped;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.internal.GeneratedComponentManager;

/** A manager for the creation of components that survives activity configuration changes. */
final class ActivityRetainedComponentManager
    implements GeneratedComponentManager<ActivityRetainedComponent> {

  /** Entry point for {@link ActivityRetainedComponentBuilder}. */
  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface ActivityRetainedComponentBuilderEntryPoint {
    ActivityRetainedComponentBuilder retainedComponentBuilder();
  }

  /** Entry point for {@link ActivityRetainedLifecycle}. */
  @EntryPoint
  @InstallIn(ActivityRetainedComponent.class)
  public interface ActivityRetainedLifecycleEntryPoint {
    ActivityRetainedLifecycle getActivityRetainedLifecycle();
  }

  static final class ActivityRetainedComponentViewModel extends ViewModel {
    private final ActivityRetainedComponent component;

    ActivityRetainedComponentViewModel(ActivityRetainedComponent component) {
      this.component = component;
    }

    ActivityRetainedComponent getComponent() {
      return component;
    }

    @Override
    protected void onCleared() {
      super.onCleared();
      ActivityRetainedLifecycle lifecycle =
          EntryPoints.get(component, ActivityRetainedLifecycleEntryPoint.class)
              .getActivityRetainedLifecycle();
      ((RetainedLifecycleImpl) lifecycle).dispatchOnCleared();
    }
  }

  private final ViewModelStoreOwner viewModelStoreOwner;
  private final Context context;

  @Nullable private volatile ActivityRetainedComponent component;
  private final Object componentLock = new Object();

  ActivityRetainedComponentManager(ComponentActivity activity) {
    this.viewModelStoreOwner = activity;
    this.context = activity;
  }

  private ViewModelProvider getViewModelProvider(
      ViewModelStoreOwner owner, Context context) {
    return new ViewModelProvider(
        owner,
        new ViewModelProvider.Factory() {
          @NonNull
          @Override
          @SuppressWarnings("unchecked")
          public <T extends ViewModel> T create(@NonNull Class<T> aClass) {
            ActivityRetainedComponent component =
                EntryPointAccessors.fromApplication(
                    context, ActivityRetainedComponentBuilderEntryPoint.class)
                    .retainedComponentBuilder()
                    .build();
            return (T) new ActivityRetainedComponentViewModel(component);
          }
        });
  }

  @Override
  public ActivityRetainedComponent generatedComponent() {
    if (component == null) {
      synchronized (componentLock) {
        if (component == null) {
          component = createComponent();
        }
      }
    }
    return component;
  }

  private ActivityRetainedComponent createComponent() {
    return getViewModelProvider(viewModelStoreOwner, context)
        .get(ActivityRetainedComponentViewModel.class)
        .getComponent();
  }

  @Module
  @InstallIn(ActivityRetainedComponent.class)
  abstract static class LifecycleModule {
    @Provides
    @ActivityRetainedScoped
    static ActivityRetainedLifecycle provideActivityRetainedLifecycle() {
      return new RetainedLifecycleImpl();
    }
  }
}
