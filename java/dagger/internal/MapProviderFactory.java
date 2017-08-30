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
import static dagger.internal.Preconditions.checkNotNull;
import static java.util.Collections.unmodifiableMap;

import dagger.Lazy;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Provider;

/**
 * A {@link Factory} implementation used to implement {@link Map} bindings. This factory returns a
 * {@code Map<K, Provider<V>>} when calling {@link #get} (as specified by {@link Factory}).
 *
 * @author Chenying Hou
 * @since 2.0
 */
public final class MapProviderFactory<K, V>
    implements Factory<Map<K, Provider<V>>>, Lazy<Map<K, Provider<V>>> {
  private final Map<K, Provider<V>> contributingMap;

  /**
   * Returns a new {@link Builder}
   */
  public static <K, V> Builder<K, V> builder(int size) {
    return new Builder<>(size);
  }

  private MapProviderFactory(Map<K, Provider<V>> contributingMap) {
    this.contributingMap = unmodifiableMap(contributingMap);
  }

  /**
   * Returns a {@code Map<K, Provider<V>>} whose iteration order is that of the elements
   * given by each of the providers, which are invoked in the order given at creation.
   *
   */
  @Override
  public Map<K, Provider<V>> get() {
    return this.contributingMap;
  }

  /** A builder for {@link MapProviderFactory}. */
  public static final class Builder<K, V> {
    private final LinkedHashMap<K, Provider<V>> map;

    private Builder(int size) {
      this.map = newLinkedHashMapWithExpectedSize(size);
    }

    /** Associates {@code key} with {@code providerOfValue}. */
    public Builder<K, V> put(K key, Provider<V> providerOfValue) {
      map.put(checkNotNull(key, "key"), checkNotNull(providerOfValue, "provider"));
      return this;
    }

    /** Returns a new {@link MapProviderFactory}. */
    public MapProviderFactory<K, V> build() {
      return new MapProviderFactory<>(map);
    }
  }
}
