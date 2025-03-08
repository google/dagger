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

package dagger.hilt.android.internal;

import android.os.Looper;

/** Thread utility methods. */
public final class ThreadUtil {

  private static Thread mainThread;

  private ThreadUtil() {}

  /** Returns true if the current thread is the Main thread. */
  public static boolean isMainThread() {
    if (mainThread == null) {
      mainThread = Looper.getMainLooper().getThread();
    }
    return Thread.currentThread() == mainThread;
  }

  /** Checks that the current thread is the Main thread. Otherwise throws an exception. */
  public static void ensureMainThread() {
    if (!isMainThread()) {
      throw new IllegalStateException("Must be called on the Main thread.");
    }
  }
}
