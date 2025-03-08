/*
 * Copyright (C) 2022 The Dagger Authors.
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

import androidx.annotation.NonNull;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.ThreadUtil;
import dagger.hilt.android.lifecycle.RetainedLifecycle;
import java.util.HashSet;
import java.util.Set;

/** Internal implementation. Do not use. */
public final class RetainedLifecycleImpl
    implements ActivityRetainedLifecycle,
        ViewModelLifecycle {

  private final Set<RetainedLifecycle.OnClearedListener> listeners = new HashSet<>();
  private boolean onClearedDispatched = false;

  public RetainedLifecycleImpl() {}

  @Override
  public void addOnClearedListener(@NonNull RetainedLifecycle.OnClearedListener listener) {
    ThreadUtil.ensureMainThread();
    throwIfOnClearedDispatched();
    listeners.add(listener);
  }

  @Override
  public void removeOnClearedListener(@NonNull RetainedLifecycle.OnClearedListener listener) {
    ThreadUtil.ensureMainThread();
    throwIfOnClearedDispatched();
    listeners.remove(listener);
  }

  public void dispatchOnCleared() {
    ThreadUtil.ensureMainThread();
    onClearedDispatched = true;
    for (RetainedLifecycle.OnClearedListener listener : listeners) {
      listener.onCleared();
    }
  }

  private void throwIfOnClearedDispatched() {
    if (onClearedDispatched) {
      throw new IllegalStateException(
          "There was a race between the call to add/remove an OnClearedListener and onCleared(). "
          + "This can happen when posting to the Main thread from a background thread, "
          + "which is not supported.");
    }
  }
}
