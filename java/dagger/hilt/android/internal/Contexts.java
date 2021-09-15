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

package dagger.hilt.android.internal;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;

/**
 * Utility methods for dealing with contexts.
 */
public final class Contexts {

  /** Finds the android Application from a context. */
  public static Application getApplication(Context context) {
    if (context instanceof Application) {
      return (Application) context;
    }

    Context unwrapContext = context;
    while (unwrapContext instanceof ContextWrapper) {
      unwrapContext = ((ContextWrapper) unwrapContext).getBaseContext();
      if (unwrapContext instanceof Application) {
        return (Application) unwrapContext;
      }
    }

    throw new IllegalStateException(
        "Could not find an Application in the given context: " + context);
  }

  private Contexts() {}
}
