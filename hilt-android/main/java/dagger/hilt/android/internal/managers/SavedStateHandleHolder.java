/*
 * Copyright (C) 2023 The Dagger Authors.
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

import static dagger.hilt.internal.Preconditions.checkNotNull;
import static dagger.hilt.internal.Preconditions.checkState;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.lifecycle.viewmodel.MutableCreationExtras;
import dagger.hilt.android.internal.ThreadUtil;

/** Implementation for SavedStateHandleHolder. */
public final class SavedStateHandleHolder {
  private CreationExtras extras;
  private SavedStateHandle handle;
  private final boolean nonComponentActivity;

  SavedStateHandleHolder(@Nullable CreationExtras extras) {
    nonComponentActivity = (extras == null);
    this.extras = extras;
  }

  SavedStateHandle getSavedStateHandle() {
    ThreadUtil.ensureMainThread();
    checkState(
        !nonComponentActivity,
        "Activity that does not extend ComponentActivity cannot use SavedStateHandle");
    if (handle != null) {
      return handle;
    }
    checkNotNull(
        extras,
        "The first access to SavedStateHandle should happen between super.onCreate() and"
            + " super.onDestroy()");
    // Clean up default args, since those are unused and we don't want to duplicate those for each
    // SavedStateHandle
    MutableCreationExtras mutableExtras = new MutableCreationExtras(extras);
    mutableExtras.set(SavedStateHandleSupport.DEFAULT_ARGS_KEY, Bundle.EMPTY);
    extras = mutableExtras;
    handle = SavedStateHandleSupport.createSavedStateHandle(extras);

    extras = null;
    return handle;
  }

  public void clear() {
    extras = null;
  }

  public void setExtras(CreationExtras extras) {
    if (handle != null) {
      // If handle is already created, we don't need to store CreationExtras.
      return;
    }
    this.extras = extras;
  }

  public boolean isInvalid() {
    return handle == null && extras == null;
  }
}
