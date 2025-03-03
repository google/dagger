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

import static androidx.lifecycle.SavedStateHandleSupport.createSavedStateHandle;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.savedstate.SavedStateRegistryOwner;
import dagger.Module;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.components.ViewModelComponent;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.multibindings.Multibinds;
import java.util.Map;
import javax.inject.Provider;
import kotlin.jvm.functions.Function1;

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

  /** Hilt entry point for getting the multi-binding map of ViewModels. */
  @EntryPoint
  @InstallIn(ViewModelComponent.class)
  public interface ViewModelFactoriesEntryPoint {
    @HiltViewModelMap
    Map<Class<?>, Provider<ViewModel>> getHiltViewModelMap();

    // From ViewModel class names to user defined @AssistedFactory-annotated implementations.
    @HiltViewModelAssistedMap
    Map<Class<?>, Object> getHiltViewModelAssistedMap();
  }

  /** Creation extra key for the callbacks that create @AssistedInject-annotated ViewModels. */
  public static final CreationExtras.Key<Function1<Object, ViewModel>> CREATION_CALLBACK_KEY =
      new CreationExtras.Key<Function1<Object, ViewModel>>() {};

  /** Hilt module for providing the empty multi-binding map of ViewModels. */
  @Module
  @InstallIn(ViewModelComponent.class)
  interface ViewModelModule {
    @Multibinds
    @HiltViewModelMap
    Map<Class<?>, ViewModel> hiltViewModelMap();

    @Multibinds
    @HiltViewModelAssistedMap
    Map<Class<?>, Object> hiltViewModelAssistedMap();
  }

  private final Map<Class<?>, Boolean> hiltViewModelKeys;
  private final ViewModelProvider.Factory delegateFactory;
  private final ViewModelProvider.Factory hiltViewModelFactory;

  public HiltViewModelFactory(
      @NonNull Map<Class<?>, Boolean> hiltViewModelKeys,
      @NonNull ViewModelProvider.Factory delegateFactory,
      @NonNull ViewModelComponentBuilder viewModelComponentBuilder) {
    this.hiltViewModelKeys = hiltViewModelKeys;
    this.delegateFactory = delegateFactory;
    this.hiltViewModelFactory =
        new ViewModelProvider.Factory() {
          @NonNull
          @Override
          public <T extends ViewModel> T create(
              @NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
            RetainedLifecycleImpl lifecycle = new RetainedLifecycleImpl();
            ViewModelComponent component =
                viewModelComponentBuilder
                    .savedStateHandle(createSavedStateHandle(extras))
                    .viewModelLifecycle(lifecycle)
                    .build();
            T viewModel = createViewModel(component, modelClass, extras);
            viewModel.addCloseable(lifecycle::dispatchOnCleared);
            return viewModel;
          }

          private <T extends ViewModel> T createViewModel(
              @NonNull ViewModelComponent component,
              @NonNull Class<T> modelClass,
              @NonNull CreationExtras extras) {
            Provider<? extends ViewModel> provider =
                EntryPoints.get(component, ViewModelFactoriesEntryPoint.class)
                    .getHiltViewModelMap()
                    .get(modelClass);
            Function1<Object, ViewModel> creationCallback = extras.get(CREATION_CALLBACK_KEY);
            Object assistedFactory =
                EntryPoints.get(component, ViewModelFactoriesEntryPoint.class)
                    .getHiltViewModelAssistedMap()
                    .get(modelClass);

            if (assistedFactory == null) {
              if (creationCallback == null) {
                if (provider == null) {
                  throw new IllegalStateException(
                      "Expected the @HiltViewModel-annotated class "
                          + modelClass.getName()
                          + " to be available in the multi-binding of "
                          + "@HiltViewModelMap"
                          + " but none was found.");
                } else {
                  return (T) provider.get();
                }
              } else {
                // Provider could be null or non-null.
                throw new IllegalStateException(
                    "Found creation callback but class "
                        + modelClass.getName()
                        + " does not have an assisted factory specified in @HiltViewModel.");
              }
            } else {
              if (provider == null) {
                if (creationCallback == null) {
                  throw new IllegalStateException(
                      "Found @HiltViewModel-annotated class "
                          + modelClass.getName()
                          + " using @AssistedInject but no creation callback"
                          + " was provided in CreationExtras.");
                } else {
                  return (T) creationCallback.invoke(assistedFactory);
                }
              } else {
                // Creation callback could be null or non-null.
                throw new AssertionError(
                    "Found the @HiltViewModel-annotated class "
                        + modelClass.getName()
                        + " in both the multi-bindings of "
                        + "@HiltViewModelMap and @HiltViewModelAssistedMap.");
              }
            }
          }
        };
  }

  @NonNull
  @Override
  public <T extends ViewModel> T create(
      @NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
    if (hiltViewModelKeys.containsKey(modelClass)) {
      return hiltViewModelFactory.create(modelClass, extras);
    } else {
      return delegateFactory.create(modelClass, extras);
    }
  }

  @NonNull
  @Override
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    if (hiltViewModelKeys.containsKey(modelClass)) {
      return hiltViewModelFactory.create(modelClass);
    } else {
      return delegateFactory.create(modelClass);
    }
  }

  @EntryPoint
  @InstallIn(ActivityComponent.class)
  interface ActivityCreatorEntryPoint {
    @HiltViewModelMap.KeySet
    Map<Class<?>, Boolean> getViewModelKeys();

    ViewModelComponentBuilder getViewModelComponentBuilder();
  }

  public static ViewModelProvider.Factory createInternal(
      @NonNull Activity activity,
      @NonNull SavedStateRegistryOwner owner,
      @Nullable Bundle defaultArgs,
      @NonNull ViewModelProvider.Factory delegateFactory) {
    return createInternal(activity, delegateFactory);
  }

  public static ViewModelProvider.Factory createInternal(
      @NonNull Activity activity, @NonNull ViewModelProvider.Factory delegateFactory) {
    ActivityCreatorEntryPoint entryPoint =
        EntryPoints.get(activity, ActivityCreatorEntryPoint.class);
    return new HiltViewModelFactory(
        entryPoint.getViewModelKeys(),
        delegateFactory,
        entryPoint.getViewModelComponentBuilder()
    );
  }
}
