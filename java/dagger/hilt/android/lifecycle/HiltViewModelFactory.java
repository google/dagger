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

package dagger.hilt.android.lifecycle;

import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.savedstate.SavedStateRegistryOwner;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ViewModelComponent;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.lifecycle.ViewModelInjectMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;

/**
 * View Model Provider Factory for the Hilt Extension.
 *
 * <p>A provider for this factory will be installed in the {@link
 * dagger.hilt.android.components.ActivityComponent} and {@link
 * dagger.hilt.android.components.FragmentComponent}. An instance of this factory will also be the
 * default factory by activities and fragments annotated with {@link
 * dagger.hilt.android.AndroidEntryPoint}.
 */
public final class HiltViewModelFactory implements ViewModelProvider.Factory {

  @EntryPoint
  @InstallIn(ViewModelComponent.class)
  interface ViewModelFactoriesEntryPoint {
    @ViewModelInjectMap
    Map<String, Provider<ViewModel>> getHiltViewModelInjectMap();
  }

  private final Set<String> viewModelInjectKeys;
  private final ViewModelProvider.Factory delegateFactory;
  private final AbstractSavedStateViewModelFactory viewModelInjectFactory;

  public HiltViewModelFactory(
      @NonNull SavedStateRegistryOwner owner,
      @Nullable Bundle defaultArgs,
      @NonNull Set<String> viewModelInjectKeys,
      @NonNull ViewModelProvider.Factory delegateFactory,
      @NonNull ViewModelComponentBuilder viewModelComponentBuilder) {
    this.viewModelInjectKeys = viewModelInjectKeys;
    this.delegateFactory = delegateFactory;
    this.viewModelInjectFactory =
        new AbstractSavedStateViewModelFactory(owner, defaultArgs) {
          @NonNull
          @Override
          @SuppressWarnings("unchecked")
          protected <T extends ViewModel> T create(
              @NonNull String key, @NonNull Class<T> modelClass, @NonNull SavedStateHandle handle) {
            ViewModelComponent component =
                viewModelComponentBuilder.savedStateHandle(handle).build();
            Provider<? extends ViewModel> provider =
                EntryPoints.get(component, ViewModelFactoriesEntryPoint.class)
                    .getHiltViewModelInjectMap()
                    .get(modelClass.getName());
            if (provider == null) {
              throw new IllegalStateException(
                  "Expected the @ViewModelInject-annotated class '"
                      + modelClass.getName()
                      + "' to be available in the multi-binding of "
                      + "@ViewModelInjectMap but none was found.");
            }
            return (T) provider.get();
          }
        };
  }

  @NonNull
  @Override
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    if (viewModelInjectKeys.contains(modelClass.getName())) {
      return viewModelInjectFactory.create(modelClass);
    } else {
      return delegateFactory.create(modelClass);
    }
  }
}
