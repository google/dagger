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

/** A listener for producer future cancellation. */
public interface CancellationListener {
  /** Called when the future for a producer this listener has been added to is cancelled. */
  // Note that this name is intentionally a bit verbose to make it unlikely that it will conflict
  // with any user-defined methods on a component.
  void onProducerFutureCancelled(boolean mayInterruptIfRunning);
}
