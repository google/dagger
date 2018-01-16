/*
 * Copyright (C) 2017 The Dagger Authors.
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.CallSuper;
import dagger.internal.Beta;

/**
 * A {@link BroadcastReceiver} that injects its members in every call to {@link #onReceive(Context,
 * Intent)}.
 *
 * <p>This class should only be used for {@link BroadcastReceiver}s that are declared in an {@code
 * AndroidManifest.xml}. If, instead, the {@link BroadcastReceiver} is created in code, prefer
 * constructor injection.
 *
 * <p>Note: this class is <em>not thread safe</em> and should not be used with multiple {@link
 * android.os.Handler}s in calls to {@link Context#registerReceiver(BroadcastReceiver,
 * android.content.IntentFilter, String, android.os.Handler)}. Injection is performed on each
 * invocation to {@link #onReceive(Context, Intent)} which could result in inconsistent views of
 * injected dependencies across threads.
 *
 * <p>Subclasses should override {@link #onReceive(Context, Intent)} and call {@code
 * super.onReceive(context, intent)} immediately to ensure injection is performed immediately.
 */
@Beta
public abstract class DaggerBroadcastReceiver extends BroadcastReceiver {
  @CallSuper
  @Override
  public void onReceive(Context context, Intent intent) {
    AndroidInjection.inject(this, context);
  }
}
