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

package dagger.internal.codegen;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Utilities for streams. */
public final class DaggerStreams {

  /**
   * Returns a {@link Collector} that accumulates the input elements into a new {@link
   * ImmutableList}, in encounter order.
   */
  public static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
    return collectingAndThen(toList(), ImmutableList::copyOf);
  }

  /**
   * Returns a {@link Collector} that accumulates the input elements into a new {@link
   * ImmutableSet}, in encounter order.
   */
  public static <T> Collector<T, ?, ImmutableSet<T>> toImmutableSet() {
    return collectingAndThen(toList(), ImmutableSet::copyOf);
  }

  /**
   * Returns a {@link Collector} that accumulates elements into an {@code ImmutableMap} whose keys
   * and values are the result of applying the provided mapping functions to the input elements.
   * Entries appear in the result {@code ImmutableMap} in encounter order.
   */
  public static <T, K, V> Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
      Function<? super T, K> keyMapper, Function<? super T, V> valueMapper) {
    return Collectors.mapping(
        value -> Maps.immutableEntry(keyMapper.apply(value), valueMapper.apply(value)),
        Collector.of(
            ImmutableMap::builder,
            (ImmutableMap.Builder<K, V> builder, Map.Entry<K, V> entry) -> builder.put(entry),
            (left, right) -> left.putAll(right.build()),
            ImmutableMap.Builder::build));
  }

  /**
   * Returns a function from {@link Object} to {@code Stream<T>}, which returns a stream containing
   * its input if its input is an instance of {@code T}.
   *
   * <p>Use as an argument to {@link Stream#flatMap(Function)}:
   *
   * <pre>{@code Stream<Bar>} barStream = fooStream.flatMap(instancesOf(Bar.class));</pre>
   */
  public static <T> Function<Object, Stream<T>> instancesOf(Class<T> to) {
    return f -> to.isInstance(f) ? Stream.of(to.cast(f)) : Stream.empty();
  }

  private DaggerStreams() {}
}
