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

package dagger.hilt.android.lifecycle;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * A class for registered listeners on a retained lifecycle (generally backed up by a ViewModel).
 */
public interface RetainedLifecycle {

  /**
   * Adds a new {@link OnClearedListener} for receiving a callback when the lifecycle is cleared.
   *
   * @param listener The listener that should be added.
   */
  @MainThread
  void addOnClearedListener(@NonNull OnClearedListener listener);

  /**
   * Removes a {@link OnClearedListener} previously added via {@link
   * #addOnClearedListener(OnClearedListener)}.
   *
   * @param listener The listener that should be removed.
   */
  @MainThread
  void removeOnClearedListener(@NonNull OnClearedListener listener);

  /** Listener for when the retained lifecycle is cleared. */
  interface OnClearedListener {
    void onCleared();
  }
}
