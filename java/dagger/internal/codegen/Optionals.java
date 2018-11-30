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

package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.asList;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

/** Utilities for {@link Optional}s. */
final class Optionals {
  /**
   * A {@link Comparator} that puts empty {@link Optional}s before present ones, and compares
   * present {@link Optional}s by their values.
   */
  static <C extends Comparable<C>> Comparator<Optional<C>> optionalComparator() {
    return Comparator.comparing((Optional<C> optional) -> optional.isPresent())
        .thenComparing(Optional::get);
  }

  static <T> Comparator<Optional<T>> emptiesLast(Comparator<? super T> valueComparator) {
    checkNotNull(valueComparator);
    return Comparator.comparing(o -> o.orElse(null), Comparator.nullsLast(valueComparator));
  }

  /** Returns the first argument that is present, or empty if none are. */
  @SafeVarargs
  static <T> Optional<T> firstPresent(Optional<T> first, Optional<T> second, Optional<T>... rest) {
    return asList(first, second, rest)
        .stream()
        .filter(Optional::isPresent)
        .findFirst()
        .orElse(Optional.empty());
  }

  /**
   * Walks a chain of present optionals as defined by successive calls to {@code nextFunction},
   * returning the value of the final optional that is present. The first optional in the chain is
   * the result of {@code nextFunction(start)}.
   */
  static <T> T rootmostValue(T start, Function<T, Optional<T>> nextFunction) {
    T current = start;
    for (Optional<T> next = nextFunction.apply(start);
        next.isPresent();
        next = nextFunction.apply(current)) {
      current = next.get();
    }
    return current;
  }

  private Optionals() {}
}
