/*
 * Copyright (C) 2015 The Dagger Authors.
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

import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;

/**
 * An abstract {@link Producer} implementation that memoizes the result of its compute method.
 */
public abstract class AbstractProducer<T> implements Producer<T> {
  private volatile ListenableFuture<T> instance = null;

  protected AbstractProducer() {}

  /** Computes this producer's future, which is then cached in {@link #get}. */
  protected abstract ListenableFuture<T> compute();

  @Override
  public final ListenableFuture<T> get() {
    // double-check idiom from EJ2: Item 71
    ListenableFuture<T> result = instance;
    if (result == null) {
      synchronized (this) {
        result = instance;
        if (result == null) {
          instance = result = compute();
          if (result == null) {
            throw new NullPointerException("compute returned null");
          }
        }
      }
    }
    return result;
  }
}
