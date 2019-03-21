/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.android;

import dagger.BindsInstance;
import dagger.internal.Beta;

/**
 * Performs members-injection for a concrete subtype of a <a
 * href="https://developer.android.com/guide/components/">core Android type</a> (e.g., {@link
 * android.app.Activity} or {@link android.app.Fragment}).
 *
 * <p>Commonly implemented by {@link dagger.Subcomponent}-annotated types whose {@link
 * dagger.Subcomponent.Factory} extends {@link Factory}.
 *
 * @param <T> a concrete subtype of a core Android type
 * @see AndroidInjection
 * @see DispatchingAndroidInjector
 * @see ContributesAndroidInjector
 */
@Beta
public interface AndroidInjector<T> {

  /** Injects the members of {@code instance}. */
  void inject(T instance);

  /**
   * Creates {@link AndroidInjector}s for a concrete subtype of a core Android type.
   *
   * @param <T> the concrete type to be injected
   */
  interface Factory<T> {
    /**
     * Creates an {@link AndroidInjector} for {@code instance}. This should be the same instance
     * that will be passed to {@link #inject(Object)}.
     */
    AndroidInjector<T> create(@BindsInstance T instance);
  }

  /**
   * An adapter that lets the common {@link dagger.Subcomponent.Builder} pattern implement {@link
   * Factory}.
   *
   * @param <T> the concrete type to be injected
   * @deprecated Prefer {@link Factory} now that components can have {@link dagger.Component.Factory
   *     factories} instead of builders
   */
  @Deprecated
  abstract class Builder<T> implements AndroidInjector.Factory<T> {
    @Override
    public final AndroidInjector<T> create(T instance) {
      seedInstance(instance);
      return build();
    }

    /**
     * Provides {@code instance} to be used in the binding graph of the built {@link
     * AndroidInjector}. By default, this is used as a {@link BindsInstance} method, but it may be
     * overridden to provide any modules which need a reference to the activity.
     *
     * <p>This should be the same instance that will be passed to {@link #inject(Object)}.
     */
    @BindsInstance
    public abstract void seedInstance(T instance);

    /** Returns a newly-constructed {@link AndroidInjector}. */
    public abstract AndroidInjector<T> build();
  }
}
