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

import java.util.Map;
import java.util.Map.Entry;
import javax.inject.Provider;

/**
 * A {@link Factory} implementation used to implement {@link Map} bindings. This factory returns a
 * {@code Map<K, V>} when calling {@link #get} (as specified by {@link Factory}).
 *
 * @author Chenying Hou
 * @since 2.0
 *
 */
public final class MapFactory<K, V> implements Factory<Map<K, V>> {
  private final Map<K, Provider<V>> contributingMap;

  private MapFactory(Map<K, Provider<V>> map) {
    this.contributingMap = unmodifiableMap(map);
  }

  /**
   * Returns a new MapFactory.
   */
  public static <K, V> MapFactory<K, V> create(Provider<Map<K, Provider<V>>> mapProviderFactory) {
    Map<K, Provider<V>> map = mapProviderFactory.get();
    return new MapFactory<K, V>(map);
  }

  /**
   * Returns a {@code Map<K, V>} whose iteration order is that of the elements
   * given by each of the providers, which are invoked in the order given at creation.
   */
  @Override
  public Map<K, V> get() {
    Map<K, V> result = newLinkedHashMapWithExpectedSize(contributingMap.size());
    for (Entry<K, Provider<V>> entry: contributingMap.entrySet()) {
      result.put(entry.getKey(), entry.getValue().get());
    }
    return unmodifiableMap(result);
  }
}
