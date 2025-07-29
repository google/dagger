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

import static dagger.internal.Providers.asDaggerProvider;
import static dagger.producers.internal.Producers.entryPointViewOf;
import static dagger.producers.internal.Producers.nonCancellationPropagatingViewOf;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.internal.Provider;
import dagger.producers.Producer;
import java.util.Map;

/**
 * A {@link Producer} implementation used to implement {@link Map} bindings. This factory returns an
 * immediate future of {@code Map<K, Producer<V>>} when calling {@link #get}.
 */
public final class MapOfProducerProducer<K, V> extends AbstractMapProducer<K, V, Producer<V>> {
  /** Returns a new {@link Builder}. */
  public static <K, V> Builder<K, V> builder(int size) {
    return new Builder<>(size);
  }

  private MapOfProducerProducer(ImmutableMap<K, Producer<V>> contributingMap) {
    super(contributingMap);
  }

  @Override
  public ListenableFuture<Map<K, Producer<V>>> compute() {
    return Futures.<Map<K, Producer<V>>>immediateFuture(contributingMap());
  }

  /** A builder for {@link MapOfProducerProducer} */
  public static final class Builder<K, V> extends AbstractMapProducer.Builder<K, V, Producer<V>> {
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
    public Builder<K, V> putAll(Producer<Map<K, Producer<V>>> mapOfProducerProducer) {
      super.putAll(mapOfProducerProducer);
      return this;
    }

    /** Returns a new {@link MapOfProducerProducer}. */
    public MapOfProducerProducer<K, V> build() {
      return new MapOfProducerProducer<>(mapBuilder.build());
    }
  }

  @Override
  public Producer<Map<K, Producer<V>>> newDependencyView() {
    return newTransformedValuesView(MapOfProducerProducer.<V>toDependencyView());
  }

  @Override
  public Producer<Map<K, Producer<V>>> newEntryPointView(
      CancellationListener cancellationListener) {
    return newTransformedValuesView(
        MapOfProducerProducer.<V>toEntryPointView(cancellationListener));
  }

  private Producer<Map<K, Producer<V>>> newTransformedValuesView(
      Function<Producer<V>, Producer<V>> valueTransformationFunction) {
    return dagger.producers.Producers.<Map<K, Producer<V>>>immediateProducer(
        ImmutableMap.copyOf(Maps.transformValues(contributingMap(), valueTransformationFunction)));
  }

  @SuppressWarnings("unchecked")
  private static <T> Function<Producer<T>, Producer<T>> toDependencyView() {
    return (Function) TO_DEPENDENCY_VIEW;
  }

  private static <T> Function<Producer<T>, Producer<T>> toEntryPointView(
      final CancellationListener cancellationListener) {
    return new Function<Producer<T>, Producer<T>>() {
      @Override
      public Producer<T> apply(Producer<T> input) {
        return entryPointViewOf(input, cancellationListener);
      }
    };
  }

  private static final Function<Producer<?>, Producer<?>> TO_DEPENDENCY_VIEW =
      new Function<Producer<?>, Producer<?>>() {
        @Override
        public Producer<?> apply(Producer<?> input) {
          return nonCancellationPropagatingViewOf(input);
        }
      };
}
