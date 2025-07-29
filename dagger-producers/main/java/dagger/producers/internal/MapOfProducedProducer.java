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

import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static dagger.internal.Providers.asDaggerProvider;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.internal.Provider;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.List;
import java.util.Map;

/**
 * A {@link Producer} implementation used to implement {@link Map} bindings. This producer returns a
 * {@code Map<K, Produced<V>>} which is populated by calls to the delegate {@link Producer#get}
 * methods.
 */
public final class MapOfProducedProducer<K, V> extends AbstractMapProducer<K, V, Produced<V>> {
  private MapOfProducedProducer(ImmutableMap<K, Producer<V>> contributingMap) {
    super(contributingMap);
  }

  @Override
  public ListenableFuture<Map<K, Produced<V>>> compute() {
    return Futures.transform(
        Futures.allAsList(
            Iterables.transform(
                contributingMap().entrySet(), MapOfProducedProducer.<K, V>entryUnwrapper())),
        new Function<List<Map.Entry<K, Produced<V>>>, Map<K, Produced<V>>>() {
          @Override
          public Map<K, Produced<V>> apply(List<Map.Entry<K, Produced<V>>> entries) {
            return ImmutableMap.copyOf(entries);
          }
        },
        directExecutor());
  }

  private static final Function<
          Map.Entry<Object, Producer<Object>>,
          ListenableFuture<Map.Entry<Object, Produced<Object>>>>
      ENTRY_UNWRAPPER =
          new Function<
              Map.Entry<Object, Producer<Object>>,
              ListenableFuture<Map.Entry<Object, Produced<Object>>>>() {
            @Override
            public ListenableFuture<Map.Entry<Object, Produced<Object>>> apply(
                final Map.Entry<Object, Producer<Object>> entry) {
              return transform(
                  Producers.createFutureProduced(entry.getValue().get()),
                  new Function<Produced<Object>, Map.Entry<Object, Produced<Object>>>() {
                    @Override
                    public Map.Entry<Object, Produced<Object>> apply(Produced<Object> value) {
                      return Maps.immutableEntry(entry.getKey(), value);
                    }
                  },
                  directExecutor());
            }
          };

  @SuppressWarnings({"unchecked", "rawtypes"}) // bivariate implementation
  private static <K, V>
      Function<Map.Entry<K, Producer<V>>, ListenableFuture<Map.Entry<K, Produced<V>>>>
          entryUnwrapper() {
    return (Function) ENTRY_UNWRAPPER;
  }

  /** Returns a new {@link Builder}. */
  public static <K, V> Builder<K, V> builder(int size) {
    return new Builder<>(size);
  }

  /** A builder for {@link MapOfProducedProducer}. */
  public static final class Builder<K, V> extends AbstractMapProducer.Builder<K, V, Produced<V>> {
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

    /**
     * Legacy javax version of the method to support libraries compiled with an older version of
     * Dagger. Do not use directly.
     */
    @Deprecated
    public Builder<K, V> put(K key, javax.inject.Provider<V> providerOfValue) {
      return put(key, asDaggerProvider(providerOfValue));
    }

    @Override
    public Builder<K, V> putAll(Producer<Map<K, Produced<V>>> mapOfProducedProducer) {
      super.putAll(mapOfProducedProducer);
      return this;
    }

    /** Returns a new {@link MapOfProducedProducer}. */
    public MapOfProducedProducer<K, V> build() {
      return new MapOfProducedProducer<>(mapBuilder.build());
    }
  }
}
