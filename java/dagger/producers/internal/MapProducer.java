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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static dagger.producers.internal.Producers.producerFromProvider;

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
public final class MapProducer<K, V> extends AbstractProducer<Map<K, V>> {
  private final ImmutableMap<K, Producer<V>> contributingMap;

  private MapProducer(ImmutableMap<K, Producer<V>> contributingMap) {
    this.contributingMap = contributingMap;
  }

  /** Returns a new {@link Builder}. */
  public static <K, V> Builder<K, V> builder(int size) {
    return new Builder<>(size);
  }

  /** A builder for {@link MapProducer} */
  public static final class Builder<K, V> {
    private final ImmutableMap.Builder<K, Producer<V>> mapBuilder;

    private Builder(int size) {
      mapBuilder = ImmutableMap.builderWithExpectedSize(size);
    }

    /** Associates {@code key} with {@code producerOfValue}. */
    public Builder<K, V> put(K key, Producer<V> producerOfValue) {
      checkNotNull(key, "key");
      checkNotNull(producerOfValue, "producer of value");
      mapBuilder.put(key, producerOfValue);
      return this;
    }

    /** Associates {@code key} with {@code providerOfValue}. */
    public Builder<K, V> put(K key, Provider<V> providerOfValue) {
      checkNotNull(key, "key");
      checkNotNull(providerOfValue, "provider of value");
      mapBuilder.put(key, producerFromProvider(providerOfValue));
      return this;
    }

    // TODO(b/118630627): make this accept MapProducer<K, V>, and change all framework fields to be
    // of that type so we don't need an unsafe cast
    /** Adds contributions from a super-implementation of a component into this builder. */
    public Builder<K, V> putAll(Producer<Map<K, V>> mapProducer) {
      mapBuilder.putAll(((MapProducer<K, V>) mapProducer).contributingMap);
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
    for (final Entry<K, Producer<V>> entry : contributingMap.entrySet()) {
      listOfEntries.add(
          Futures.transform(entry.getValue().get(), new Function<V, Entry<K, V>>() {
            @Override
            public Entry<K, V> apply(V computedValue) {
              return Maps.immutableEntry(entry.getKey(), computedValue);
            }
          }, directExecutor()));
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
