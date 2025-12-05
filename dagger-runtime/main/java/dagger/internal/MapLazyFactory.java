/*
 * Copyright (C) 2014 The Dagger Authors.
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

package dagger.internal;

import static dagger.internal.DaggerCollections.newLinkedHashMapWithExpectedSize;
import static dagger.internal.Providers.asDaggerProvider;
import static java.util.Collections.unmodifiableMap;

import dagger.Lazy;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A {@link Factory} implementation used to implement {@link Map} bindings. This factory returns a
 * {@code Map<K, Lazy<V>>} when calling {@link #get} (as specified by {@link Factory}).
 */
public final class MapLazyFactory<K, V> extends AbstractMapFactory<K, V, Lazy<V>> {
  private static final Provider<Map<Object, Object>> EMPTY =
      InstanceFactory.create(Collections.emptyMap());

  /** Returns a new {@link Builder} */
  public static <K, V> Builder<K, V> builder(int size) {
    return new Builder<>(size);
  }

  /** Returns a factory of an empty map. */
  @SuppressWarnings("unchecked") // safe contravariant cast
  public static <K, V> Provider<Map<K, Lazy<V>>> emptyMapProvider() {
    return (Provider<Map<K, Lazy<V>>>) (Provider) EMPTY;
  }

  private MapLazyFactory(Map<K, Provider<V>> map) {
    super(map);
  }

  /**
   * Returns a {@code Map<K, Lazy<V>>} whose iteration order is that of the elements given by each
   * of the providers, which are invoked in the order given at creation.
   */
  @Override
  public Map<K, Lazy<V>> get() {
    Map<K, Lazy<V>> result = newLinkedHashMapWithExpectedSize(contributingMap().size());
    for (Entry<K, Provider<V>> entry : contributingMap().entrySet()) {
      result.put(entry.getKey(), DoubleCheck.lazy(entry.getValue()));
    }
    return unmodifiableMap(result);
  }

  /** A builder for {@link MapLazyFactory}. */
  public static final class Builder<K, V> extends AbstractMapFactory.Builder<K, V, Lazy<V>> {
    private Builder(int size) {
      super(size);
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
    public Builder<K, V> putAll(Provider<Map<K, Lazy<V>>> mapFactory) {
      super.putAll(mapFactory);
      return this;
    }

    /**
     * Legacy javax version of the method to support libraries compiled with an older version of
     * Dagger. Do not use directly.
     */
    @Deprecated
    public Builder<K, V> putAll(javax.inject.Provider<Map<K, Lazy<V>>> mapFactory) {
      return putAll(asDaggerProvider(mapFactory));
    }

    /** Returns a new {@link MapLazyFactory}. */
    public MapLazyFactory<K, V> build() {
      return new MapLazyFactory<>(map);
    }
  }
}
