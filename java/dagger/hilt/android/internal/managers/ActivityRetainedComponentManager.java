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

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.ComponentActivity;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityRetainedComponent;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.internal.GeneratedComponentManager;

/** A manager for the creation of components that survives activity configuration changes. */
final class ActivityRetainedComponentManager
    implements GeneratedComponentManager<ActivityRetainedComponent> {

  @EntryPoint
  @InstallIn(ApplicationComponent.class)
  public interface LifecycleComponentBuilderEntryPoint {
    ActivityRetainedComponentBuilder retainedComponentBuilder();
  }

  static final class ActivityRetainedComponentViewModel extends ViewModel {
    private final ActivityRetainedComponent component;

    ActivityRetainedComponentViewModel(ActivityRetainedComponent component) {
      this.component = component;
    }

    ActivityRetainedComponent getComponent() {
      return component;
    }
  }

  private final ViewModelProvider viewModelProvider;

  @Nullable private volatile ActivityRetainedComponent component;
  private final Object componentLock = new Object();

  ActivityRetainedComponentManager(ComponentActivity activity) {
    this.viewModelProvider =
        new ViewModelProvider(
            activity,
            new ViewModelProvider.Factory() {
              @NonNull
              @Override
              @SuppressWarnings("unchecked")
              public <T extends ViewModel> T create(@NonNull Class<T> aClass) {
                ActivityRetainedComponent component =
                    EntryPoints.get(
                            activity.getApplication(), LifecycleComponentBuilderEntryPoint.class)
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
    return viewModelProvider.get(ActivityRetainedComponentViewModel.class).getComponent();
  }

}
