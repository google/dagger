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

package dagger.hilt.android.scopes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.inject.Scope;

/**
 * Scope annotation for bindings that should exist for the life of a a single {@link
 * androidx.lifecycle.ViewModel}.
 *
 * <p>Use this scope annotation when you want to define a dependency in the {@link
 * dagger.hilt.android.components.ViewModelComponent} for which a single instance will be provided
 * across all other dependencies for a single {@link
 * dagger.hilt.android.lifecycle.ViewModelInject}-annotated {@code ViewModel}. Other {@code
 * ViewModel}s that request the scoped dependency will receive a different instance. For sharing the
 * same instance of a dependency across all {@code ViewModel}s use a scope from one of the parent
 * components of {@code dagger.hilt.android.components.ViewModelComponent}, such as {@link
 * javax.inject.Singleton} or {@link dagger.hilt.android.scopes.ActivityRetainedScoped}.
 *
 * <p>For example:
 *
 * <pre>
 * &#64;Module
 * &#64;InstallIn(ViewModelComponent.class)
 * public final class ViewModelMovieModule {
 *     &#64;Provides
 *     &#64;ViewModelScoped
 *     public static MovieRepository provideRepo(SavedStateHandle handle) {
 *         return new MovieRepository(handle.getString("movie-id"));
 *     }
 * }
 *
 * public final class MovieDetailFetcher {
 *     &#64;Inject MovieDetailFetcher(MovieRepository movieRepo) {
 *         // ...
 *     }
 * }
 *
 * public final class MoviePosterFetcher {
 *     &#64;Inject MoviePosterFetcher(MovieRepository movieRepo) {
 *         // ...
 *     }
 * }
 *
 * public class MovieViewModel extends ViewModel {
 *     &#64;ViewModelInject
 *     public MovieViewModel(MovieDetailFetcher detailFetcher, MoviePosterFetcher posterFetcher) {
 *         // Both detailFetcher and posterFetcher will contain the same instance of
 *         // the MovieRepository.
 *     }
 * }
 * </pre>
 *
 * @see dagger.hilt.android.lifecycle.ViewModelInject
 * @see dagger.hilt.android.components.ViewModelComponent
 */
@Scope
@Retention(RetentionPolicy.CLASS)
public @interface ViewModelScoped {}
