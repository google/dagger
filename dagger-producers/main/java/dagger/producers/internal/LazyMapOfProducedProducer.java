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
import dagger.producers.Produced;
import java.util.Map;

/**
 * Wrapper around {@link MapOfProducedProducer} to be compatible with @LazyClassKey annotated map.
 */
public final class LazyMapOfProducedProducer<V>
    extends AbstractProducer<Map<Class<?>, Produced<V>>> {
  AbstractProducer<Map<String, Produced<V>>> delegate;

  public static <V> LazyMapOfProducedProducer<V> of(
      AbstractProducer<Map<String, Produced<V>>> delegate) {
    return new LazyMapOfProducedProducer<V>(delegate);
  }

  private LazyMapOfProducedProducer(AbstractProducer<Map<String, Produced<V>>> delegate) {
    this.delegate = delegate;
  }

  @Override
  public ListenableFuture<Map<Class<?>, Produced<V>>> compute() {
    return Futures.transform(
        delegate.compute(),
        new Function<Map<String, Produced<V>>, Map<Class<?>, Produced<V>>>() {
          @Override
          public Map<Class<?>, Produced<V>> apply(Map<String, Produced<V>> classMap) {
            return LazyClassKeyMap.of(classMap);
          }
        },
        directExecutor());
  }
}
