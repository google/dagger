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
import static java.util.Collections.unmodifiableMap;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Provider;

/**
 * A {@link Factory} implementation used to implement {@link Map} bindings. This factory returns a
 * {@code Map<K, V>} when calling {@link #get} (as specified by {@link Factory}).
 */
public final class MapFactory<K, V> extends AbstractMapFactory<K, V, V> {
  private static final Provider<Map<Object, Object>> EMPTY =
      InstanceFactory.create(Collections.emptyMap());

  /** Returns a new {@link Builder} */
  public static <K, V> Builder<K, V> builder(int size) {
    return new Builder<>(size);
  }

  /** Returns a factory of an empty map. */
  @SuppressWarnings("unchecked") // safe contravariant cast
  public static <K, V> Provider<Map<K, V>> emptyMapProvider() {
    return (Provider<Map<K, V>>) (Provider) EMPTY;
  }

  private MapFactory(Map<K, Provider<V>> map) {
    super(map);
  }

  /**
   * Returns a {@code Map<K, V>} whose iteration order is that of the elements given by each of the
   * providers, which are invoked in the order given at creation.
   */
  @Override
  public Map<K, V> get() {
    Map<K, V> result = newLinkedHashMapWithExpectedSize(contributingMap().size());
    for (Entry<K, Provider<V>> entry : contributingMap().entrySet()) {
      result.put(entry.getKey(), entry.getValue().get());
    }
    return unmodifiableMap(result);
  }

  /** A builder for {@link MapFactory}. */
  public static final class Builder<K, V> extends AbstractMapFactory.Builder<K, V, V> {
    private Builder(int size) {
      super(size);
    }

    public Builder<K, V> put(K key, Provider<V> providerOfValue) {
      super.put(key, providerOfValue);
      return this;
    }

    public Builder<K, V> putAll(Provider<Map<K, V>> mapFactory) {
      super.putAll(mapFactory);
      return this;
    }

    /** Returns a new {@link MapProviderFactory}. */
    public MapFactory<K, V> build() {
      return new MapFactory<>(map);
    }
  }
}
