/*
 * Copyright (C) 2014 Google, Inc.
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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

final class Collections {
  /**
   * The maximum value for a signed 32-bit integer that is equal to a power of 2.
   */
  private static final int MAX_POWER_OF_TWO = 1 << (Integer.SIZE - 2);

  private Collections() {
  }

  /**
   * Creates a {@link LinkedHashSet} instance, with a high enough "initial capacity" that it
   * <em>should</em> hold {@code expectedSize} elements without growth.
   */
  static <E> LinkedHashSet<E> newLinkedHashSetWithExpectedSize(int expectedSize) {
    return new LinkedHashSet<E>(calculateInitialCapacity(expectedSize));
  }

  /**
   * Creates a {@link LinkedHashMap} instance, with a high enough "initial capacity" that it
   * <em>should</em> hold {@code expectedSize} elements without growth.
   */
  static <K, V> LinkedHashMap<K, V> newLinkedHashMapWithExpectedSize(int expectedSize) {
    return new LinkedHashMap<K, V>(calculateInitialCapacity(expectedSize));
  }

  private static int calculateInitialCapacity(int expectedSize) {
    if (expectedSize < 3) {
      return expectedSize + 1;
    }
    if (expectedSize < MAX_POWER_OF_TWO) {
      // This is the calculation used in JDK8 to resize when a putAll
      // happens; it seems to be the most conservative calculation we
      // can make.  0.75 is the default load factor.
      return (int) (expectedSize / 0.75F + 1.0F);
    }
    return Integer.MAX_VALUE; // any large value
  }
}
