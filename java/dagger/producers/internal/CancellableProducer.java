/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.producers.internal;

import dagger.producers.Producer;

/** A {@link Producer} that can be cancelled directly even if it hasn't been started. */
public interface CancellableProducer<T> extends Producer<T> {

  /**
   * Cancels this producer. If {@link #get()} has already been called, the future it returns will be
   * cancelled if possible. If not, calling {@link #get()} will return a cancelled future and will
   * not actually start the underlying operation.
   *
   * @param mayInterruptIfRunning the value that should be passed to {@code Future.cancel(boolean)}
   *     for the futures for any running tasks when cancelling them
   */
  void cancel(boolean mayInterruptIfRunning);

  /** Returns a new view of this producer for use as a dependency of another node. */
  Producer<T> newDependencyView();

  /**
   * Returns a new view of this producer for use as an entry point.
   *
   * <p>When the view's future is cancelled, the given {@code cancellableListener} will be called.
   */
  Producer<T> newEntryPointView(CancellationListener cancellationListener);
}
