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
import static com.google.common.collect.Maps.newLinkedHashMapWithExpectedSize;
import static dagger.producers.internal.Producers.producerFromProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.internal.Beta;
import dagger.producers.Producer;
import java.util.Map;
import javax.inject.Provider;

/**
 * A {@link Producer} implementation used to implement {@link Map} bindings. This factory returns an
 * immediate future of {@code Map<K, Producer<V>>} when calling {@link #get}.
 *
 * @author Jesse Beder
 */
@Beta
public final class MapOfProducerProducer<K, V> extends AbstractProducer<Map<K, Producer<V>>> {
  private static final MapOfProducerProducer<Object, Object> EMPTY =
      new MapOfProducerProducer<Object, Object>(ImmutableMap.<Object, Producer<Object>>of());

  private final ImmutableMap<K, Producer<V>> contributingMap;

  /** Returns a new {@link Builder}. */
  public static <K, V> Builder<K, V> builder(int size) {
    return new Builder<K, V>(size);
  }

  /** Returns a producer of an empty map. */
  @SuppressWarnings("unchecked") // safe contravariant cast
  public static <K, V> MapOfProducerProducer<K, V> empty() {
    return (MapOfProducerProducer<K, V>) EMPTY;
  }

  private MapOfProducerProducer(ImmutableMap<K, Producer<V>> contributingMap) {
    this.contributingMap = contributingMap;
  }

  @Override
  public ListenableFuture<Map<K, Producer<V>>> compute() {
    return Futures.<Map<K, Producer<V>>>immediateFuture(contributingMap);
  }

  /**
   * A builder to help build the {@link MapOfProducerProducer}
   */
  public static final class Builder<K, V> {
    private final Map<K, Producer<V>> mapBuilder;

    private Builder(int size) {
      this.mapBuilder = newLinkedHashMapWithExpectedSize(size);
    }

    /** Returns a new {@link MapOfProducerProducer}. */
    public MapOfProducerProducer<K, V> build() {
      return new MapOfProducerProducer<K, V>(ImmutableMap.copyOf(mapBuilder));
    }

    /** Associates key with producerOfValue in {@code Builder}. */
    public Builder<K, V> put(K key, Producer<V> producerOfValue) {
      checkNotNull(key, "key");
      checkNotNull(producerOfValue, "producer of value");
      mapBuilder.put(key, producerOfValue);
      return this;
    }

    /** Associates key with providerOfValue in {@code Builder}. */
    public Builder<K, V> put(K key, Provider<V> providerOfValue) {
      checkNotNull(key, "key");
      checkNotNull(providerOfValue, "provider of value");
      mapBuilder.put(key, producerFromProvider(providerOfValue));
      return this;
    }
  }
}
