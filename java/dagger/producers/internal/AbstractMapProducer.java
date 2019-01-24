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
import dagger.producers.Producer;
import java.util.Map;
import javax.inject.Provider;

/**
 * An {@code abstract} {@link Producer} implementation used to implement {@link Map} bindings.
 *
 * @param <K>The key type of the map that this produces
 * @param <V>The type that each contributing producer
 * @param <V2>The value type of the map that this produces. For {@link MapProducer}, {@code V} and
 *     {@code V2} will be equivalent.
 */
abstract class AbstractMapProducer<K, V, V2> extends AbstractProducer<Map<K, V2>> {
  private final ImmutableMap<K, Producer<V>> contributingMap;

  AbstractMapProducer(ImmutableMap<K, Producer<V>> contributingMap) {
    this.contributingMap = contributingMap;
  }

  /** The map of {@link Producer}s that contribute to this map binding. */
  final ImmutableMap<K, Producer<V>> contributingMap() {
    return contributingMap;
  }

  /** A builder for {@link AbstractMapProducer} */
  public abstract static class Builder<K, V, V2> {
    final ImmutableMap.Builder<K, Producer<V>> mapBuilder;

    Builder(int size) {
      mapBuilder = ImmutableMap.builderWithExpectedSize(size);
    }

    // Unfortunately, we cannot return a self-type here because a raw Producer type passed to one of
    // these methods affects the returned type of the method. The first put*() call erases the self
    // type to the "raw" self type, and the second erases the type to the upper bound
    // (AbstractMapProducer.Builder), which doesn't have a build() method.
    //
    // The methods are therefore not declared public so that each subtype will redeclare them and
    // expand their accessibility

    /** Associates {@code key} with {@code producerOfValue}. */
    Builder<K, V, V2> put(K key, Producer<V> producerOfValue) {
      checkNotNull(key, "key");
      checkNotNull(producerOfValue, "producer of value");
      mapBuilder.put(key, producerOfValue);
      return this;
    }

    /** Associates {@code key} with {@code providerOfValue}. */
    Builder<K, V, V2> put(K key, Provider<V> providerOfValue) {
      checkNotNull(key, "key");
      checkNotNull(providerOfValue, "provider of value");
      mapBuilder.put(key, producerFromProvider(providerOfValue));
      return this;
    }

    /** Adds contributions from a super-implementation of a component into this builder. */
    Builder<K, V, V2> putAll(Producer<Map<K, V2>> mapOfProducers) {
      if (mapOfProducers instanceof DelegateProducer) {
        @SuppressWarnings("unchecked")
        DelegateProducer<Map<K, V2>> asDelegateProducer = (DelegateProducer) mapOfProducers;
        return putAll(asDelegateProducer.getDelegate());
      }
      @SuppressWarnings("unchecked")
      AbstractMapProducer<K, V, ?> asAbstractMapProducer =
          ((AbstractMapProducer<K, V, ?>) (Producer) mapOfProducers);
      mapBuilder.putAll(asAbstractMapProducer.contributingMap);
      return this;
    }
  }
}
