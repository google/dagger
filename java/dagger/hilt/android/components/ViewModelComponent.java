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

package dagger.hilt.android.components;

import dagger.hilt.DefineComponent;
import dagger.hilt.android.scopes.ViewModelScoped;

/**
 * A Hilt component that has the lifetime of a single {@link androidx.lifecycle.ViewModel}.
 *
 * <p>This Hilt component is the source of {@link
 * dagger.hilt.android.lifecycle.ViewModelInject}-annotated {@link
 * androidx.lifecycle.ViewModel}s used by the {@link
 * dagger.hilt.android.lifecycle.HiltViewModelFactory}. It contains a default binding of the {@link
 * androidx.lifecycle.SavedStateHandle} associated with the {@code ViewModel} that can be used
 * by other dependencies provided by the component.
 *
 * <p>Dependencies available in the {@link dagger.hilt.components.SingletonComponent} and {@link
 * ActivityRetainedComponent} are also available in this component since it is a child of {@code
 * ActivityRetainedComponent}.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#64;Module
 * &#64;InstallIn(ViewModelComponent.class)
 * public final class ViewModelMovieModule {
 *     &#64;Provides
 *     public static MovieRepository provideRepo(SavedStateHandle handle) {
 *         return new MovieRepository(handle.getString("movie-id"));
 *     }
 * }
 * </pre>
 *
 * <p>Dependencies in the {@code ViewModelComponent} can be scoped using the {@link ViewModelScoped}
 * annotation. This allows for a single instance of a dependency to be provided across the
 * dependencies of a single {@link dagger.hilt.android.lifecycle.ViewModelInject}-annotated {@code
 * ViewModel}.
 *
 * @see dagger.hilt.android.lifecycle.ViewModelInject
 * @see dagger.hilt.android.scopes.ViewModelScoped
 */
@ViewModelScoped
@DefineComponent(parent = ActivityRetainedComponent.class)
public interface ViewModelComponent {}
