/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.android.flags;

import android.content.Context;
import dagger.Module;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.internal.Preconditions;
import dagger.multibindings.Multibinds;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Set;
import javax.inject.Qualifier;

/**
 * Runtime flag for the Fragment.getContext() fix. See https://github.com/google/dagger/pull/2620
 * for this change. Controls if fragment code should use the fixed getContext() behavior where it
 * correctly returns null after a fragment is removed. This fixed behavior matches the behavior of a
 * regular, non-Hilt fragment and can help catch issues where a removed or leaked fragment is
 * incorrectly used.
 *
 * <p>In order to set the flag, bind a boolean value qualified with
 * {@link DisableFragmentGetContextFix} into a set in the {@code SingletonComponent}. A set is used
 * instead of an optional binding to avoid a dependency on Guava. Only one value may be bound into
 * the set within a given app. If this is not set, the default is to not use the fix. Example for
 * binding the value:
 *
 * <pre><code>
 * {@literal @}Module
 * {@literal @}InstallIn(SingletonComponent.class)
 * public final class DisableFragmentGetContextFixModule {
 *   {@literal @}Provides
 *   {@literal @}IntoSet
 *   {@literal @}FragmentGetContextFix.DisableFragmentGetContextFix
 *   static Boolean provideDisableFragmentGetContextFix() {
 *     return // true or false depending on some rollout logic for your app
 *   }
 * }
 * </code></pre>
 */
public final class FragmentGetContextFix {

  /** Qualifier annotation to bind disable the Fragment.getContext() fix at runtime. */
  @Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD})
  @Qualifier
  public @interface DisableFragmentGetContextFix {}

  public static boolean isFragmentGetContextFixDisabled(Context context) {
    // Use a set here instead of an optional to avoid the Guava dependency
    Set<Boolean> flagSet = EntryPointAccessors.fromApplication(
        context, FragmentGetContextFixEntryPoint.class).getDisableFragmentGetContextFix();

    // TODO(b/199927963): Consider adding a plugin to check this at compile time
    Preconditions.checkState(flagSet.size() <= 1,
        "Cannot bind the flag @DisableFragmentGetContextFix more than once.");

    if (flagSet.isEmpty()) {
      return true;
    } else {
      return flagSet.iterator().next();
    }
  }

  /** Entry point for getting the flag. */
  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface FragmentGetContextFixEntryPoint {
    @DisableFragmentGetContextFix Set<Boolean> getDisableFragmentGetContextFix();
  }

  /** Declare the empty flag set. */
  @Module
  @InstallIn(SingletonComponent.class)
  abstract static class FragmentGetContextFixModule {
    @Multibinds
    @DisableFragmentGetContextFix
    abstract Set<Boolean> disableFragmentGetContextFix();
  }

  private FragmentGetContextFix() {
  }
}
