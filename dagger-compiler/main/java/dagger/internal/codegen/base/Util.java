/*
 * Copyright (C) 2013 The Dagger Authors.
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

package dagger.internal.codegen.base;

import java.util.Map;
import java.util.function.Function;

/** General utilities for the annotation processor. */
public final class Util {

  /**
   * A version of {@link Map#computeIfAbsent(Object, Function)} that allows {@code mappingFunction}
   * to update {@code map}.
   */
  public static <K, V> V reentrantComputeIfAbsent(
      Map<K, V> map, K key, Function<? super K, ? extends V> mappingFunction) {
    V value = map.get(key);
    if (value == null) {
      value = mappingFunction.apply(key);
      if (value != null) {
        map.put(key, value);
      }
    }
    return value;
  }

  private Util() {}
}
