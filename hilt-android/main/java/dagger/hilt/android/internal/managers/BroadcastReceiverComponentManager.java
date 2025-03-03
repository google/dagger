/*
 * Copyright (C) 2019 The Dagger Authors.
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

import android.app.Application;
import android.content.Context;
import dagger.hilt.android.internal.Contexts;
import dagger.hilt.internal.GeneratedComponentManager;
import dagger.hilt.internal.Preconditions;

/**
 * Do not use except in Hilt generated code!
 *
 * <p>A manager for the creation of components that live in the BroadcastReceiver.
 */
public final class BroadcastReceiverComponentManager {

  public static Object generatedComponent(Context context) {
    Application application = Contexts.getApplication(context.getApplicationContext());

    Preconditions.checkArgument(
        application instanceof GeneratedComponentManager,
        "Hilt BroadcastReceiver must be attached to an @HiltAndroidApp Application. Found: %s",
        application.getClass());

    return ((GeneratedComponentManager<?>) application).generatedComponent();
  }

  private BroadcastReceiverComponentManager() {}
}
