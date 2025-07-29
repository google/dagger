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

package dagger.hilt.android.migration;

import android.app.Application;
import androidx.annotation.NonNull;
import dagger.hilt.android.internal.migration.HasCustomInject;
import dagger.hilt.internal.Preconditions;

/**
 * Utility methods for injecting the application when using {@link CustomInject}.
 *
 * @see OptionalInject
 */
public final class CustomInjection {

  /** Injects the passed in application. */
  public static void inject(@NonNull Application app) {
    Preconditions.checkNotNull(app);
    Preconditions.checkArgument(
        app instanceof HasCustomInject,
        "'%s' is not a custom inject application. Check that you have annotated"
            + " the application with both @HiltAndroidApp and @CustomInject.",
        app.getClass());
    ((HasCustomInject) app).customInject();
  }

  private CustomInjection() {}
}
