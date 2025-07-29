/*
 * Copyright (C) 2024 The Dagger Authors.
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

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.internal.LazyClassKeyMap;
import java.util.Map;

/** Wrapper around {@link MapProducer} to be compatible with @LazyClassKey annotated map. */
public final class LazyMapProducer<V> extends AbstractProducer<Map<Class<?>, V>> {
  AbstractProducer<Map<String, V>> delegate;

  public static <V> LazyMapProducer<V> of(AbstractProducer<Map<String, V>> delegate) {
    return new LazyMapProducer<V>(delegate);
  }

  private LazyMapProducer(AbstractProducer<Map<String, V>> delegate) {
    this.delegate = delegate;
  }

  @Override
  public ListenableFuture<Map<Class<?>, V>> compute() {
    return Futures.transform(
        delegate.compute(),
        new Function<Map<String, V>, Map<Class<?>, V>>() {
          @Override
          public Map<Class<?>, V> apply(Map<String, V> classMap) {
            return LazyClassKeyMap.of((Map<String, V>) classMap);
          }
        },
        directExecutor());
  }
}
