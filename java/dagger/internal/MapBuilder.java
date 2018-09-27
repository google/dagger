/*
 * Copyright (C) 2017 The Dagger Authors.
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

import java.util.Collections;
import java.util.Map;

/**
 * A fluent builder class that returns a {@link Map}. Used in component implementations where a map
 * must be created in one fluent statement for inlined request fulfillments.
 */
public final class MapBuilder<K, V> {
  private final Map<K, V> contributions;

  private MapBuilder(int size) {
    contributions = newLinkedHashMapWithExpectedSize(size);
  }

  /**
   * Creates a new {@link MapBuilder} with {@code size} elements.
   */
  public static <K, V> MapBuilder<K, V> newMapBuilder(int size) {
    return new MapBuilder<>(size);
  }

  public MapBuilder<K, V> put(K key, V value) {
    contributions.put(key, value);
    return this;
  }

  public MapBuilder<K, V> putAll(Map<K, V> map) {
    contributions.putAll(map);
    return this;
  }

  public Map<K, V> build() {
    switch (contributions.size()) {
      case 0:
        return Collections.emptyMap();
      default:
        return Collections.unmodifiableMap(contributions);
    }
  }
}
