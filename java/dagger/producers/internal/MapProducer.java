/*
 * Copyright (C) 2016 The Dagger Authors.
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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Provider;

/**
 * A {@link Producer} implementation used to implement {@link Map} bindings. This producer returns a
 * {@code Map<K, V>} which is populated by calls to the delegate {@link Producer#get} methods.
 */
public final class MapProducer<K, V> extends AbstractMapProducer<K, V, V> {
  private MapProducer(ImmutableMap<K, Producer<V>> contributingMap) {
    super(contributingMap);
  }

  /** Returns a new {@link Builder}. */
  public static <K, V> Builder<K, V> builder(int size) {
    return new Builder<>(size);
  }

  /** A builder for {@link MapProducer} */
  public static final class Builder<K, V> extends AbstractMapProducer.Builder<K, V, V> {
    private Builder(int size) {
      super(size);
    }

    @Override
    public Builder<K, V> put(K key, Producer<V> producerOfValue) {
      super.put(key, producerOfValue);
      return this;
    }

    @Override
    public Builder<K, V> put(K key, Provider<V> providerOfValue) {
      super.put(key, providerOfValue);
      return this;
    }

    @Override
    public Builder<K, V> putAll(Producer<Map<K, V>> mapProducer) {
      super.putAll(mapProducer);
      return this;
    }

    /** Returns a new {@link MapProducer}. */
    public MapProducer<K, V> build() {
      return new MapProducer<>(mapBuilder.build());
    }
  }

  @Override
  protected ListenableFuture<Map<K, V>> compute() {
    final List<ListenableFuture<Map.Entry<K, V>>> listOfEntries = new ArrayList<>();
    for (final Entry<K, Producer<V>> entry : contributingMap().entrySet()) {
      listOfEntries.add(
          Futures.transform(
              entry.getValue().get(),
              new Function<V, Entry<K, V>>() {
                @Override
                public Entry<K, V> apply(V computedValue) {
                  return Maps.immutableEntry(entry.getKey(), computedValue);
                }
              },
              directExecutor()));
    }

    return Futures.transform(
        Futures.allAsList(listOfEntries),
        new Function<List<Map.Entry<K, V>>, Map<K, V>>() {
          @Override
          public Map<K, V> apply(List<Map.Entry<K, V>> entries) {
            return ImmutableMap.copyOf(entries);
          }
        },
        directExecutor());
  }
}
