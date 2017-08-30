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
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static dagger.producers.internal.Producers.producerFromProvider;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.List;
import java.util.Map;
import javax.inject.Provider;

/**
 * A {@link Producer} implementation used to implement {@link Map} bindings. This producer returns a
 * {@code Map<K, Produced<V>>} which is populated by calls to the delegate {@link Producer#get}
 * methods.
 *
 * @author Jesse Beder
 */
public final class MapOfProducedProducer<K, V> extends AbstractProducer<Map<K, Produced<V>>> {
  private final Map<K, Producer<V>> mapOfProducers;

  private MapOfProducedProducer(Map<K, Producer<V>> mapOfProducers) {
    this.mapOfProducers = mapOfProducers;
  }

  @Override
  public ListenableFuture<Map<K, Produced<V>>> compute() {
    return Futures.transform(
        Futures.allAsList(
            Iterables.transform(
                mapOfProducers.entrySet(), MapOfProducedProducer.<K, V>entryUnwrapper())),
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
  public static <K, V> Builder<K, V> builder() {
    return new Builder<>();
  }

  /** A builder for {@link MapOfProducedProducer}. */
  public static final class Builder<K, V> {
    private final ImmutableMap.Builder<K, Producer<V>> mapBuilder = ImmutableMap.builder();

    /** Returns a new {@link MapOfProducedProducer}. */
    public MapOfProducedProducer<K, V> build() {
      return new MapOfProducedProducer<>(mapBuilder.build());
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
  }
}
