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

import static dagger.internal.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A fluent builder class that returns a {@link Set}. Used in component implementations where a set
 * must be created in one fluent statement for inlined request fulfillments.
 */
public final class SetBuilder<T> {
  private static final String SET_CONTRIBUTIONS_CANNOT_BE_NULL =
      "Set contributions cannot be null";
  private final List<T> contributions;

  private SetBuilder(int estimatedSize) {
    contributions = new ArrayList<>(estimatedSize);
  }

  /**
   * {@code estimatedSize} is the number of bindings which contribute to the set. They may each
   * provide {@code [0..n)} instances to the set. Because the final size is unknown, {@code
   * contributions} are collected in a list and only hashed in {@link #build()}.
   */
  public static <T> SetBuilder<T> newSetBuilder(int estimatedSize) {
    return new SetBuilder<T>(estimatedSize);
  }

  public SetBuilder<T> add(T t) {
    contributions.add(checkNotNull(t, SET_CONTRIBUTIONS_CANNOT_BE_NULL));
    return this;
  }

  public SetBuilder<T> addAll(Collection<? extends T> collection) {
    for (T item : collection) {
      checkNotNull(item, SET_CONTRIBUTIONS_CANNOT_BE_NULL);
    }
    contributions.addAll(collection);
    return this;
  }

  public Set<T> build() {
    switch (contributions.size()) {
      case 0:
        return Collections.emptySet();
      case 1:
        return Collections.singleton(contributions.get(0));
      default:
        return Collections.unmodifiableSet(new HashSet<>(contributions));
    }
  }
}
