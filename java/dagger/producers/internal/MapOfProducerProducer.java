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
import static dagger.producers.internal.Producers.producerFromProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import java.util.Map;
import javax.inject.Provider;

/**
 * A {@link Producer} implementation used to implement {@link Map} bindings. This factory returns an
 * immediate future of {@code Map<K, Producer<V>>} when calling {@link #get}.
 */
public final class MapOfProducerProducer<K, V> extends AbstractProducer<Map<K, Producer<V>>> {
  private final ImmutableMap<K, Producer<V>> contributingMap;

  /** Returns a new {@link Builder}. */
  public static <K, V> Builder<K, V> builder() {
    return new Builder<>();
  }

  private MapOfProducerProducer(ImmutableMap<K, Producer<V>> contributingMap) {
    this.contributingMap = contributingMap;
  }

  @Override
  public ListenableFuture<Map<K, Producer<V>>> compute() {
    return Futures.<Map<K, Producer<V>>>immediateFuture(contributingMap);
  }

  /** A builder for {@link MapOfProducerProducer} */
  public static final class Builder<K, V> {
    private final ImmutableMap.Builder<K, Producer<V>> mapBuilder = ImmutableMap.builder();

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

    /** Returns a new {@link MapOfProducerProducer}. */
    public MapOfProducerProducer<K, V> build() {
      return new MapOfProducerProducer<>(mapBuilder.build());
    }
  }
}
