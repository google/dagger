/*
 * Copyright (C) 2015 Google, Inc.
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
package dagger.producers.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import dagger.producers.monitoring.ProducerMonitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A {@link Producer} implementation used to implement {@link Set} bindings. This producer returns
 * a future {@link Set} whose elements are populated by subsequent calls to the delegate
 * {@link Producer#get} methods.
 *
 * @author Jesse Beder
 * @since 2.0
 */
public final class SetProducer<T> extends AbstractProducer<Set<T>> {
  private static final Producer<Set<Object>> EMPTY_PRODUCER =
      new Producer<Set<Object>>() {
        @Override
        public ListenableFuture<Set<Object>> get() {
          return Futures.<Set<Object>>immediateFuture(ImmutableSet.<Object>of());
        }
      };

  @SuppressWarnings({"unchecked", "rawtypes"}) // safe covariant cast
  public static <T> Producer<Set<T>> create() {
    return (Producer) EMPTY_PRODUCER;
  }

  /**
   * Returns the supplied producer.  If there's just one producer, there's no need to wrap it or its
   * result.
   */
  public static <T> Producer<Set<T>> create(Producer<Set<T>> producer) {
    return producer;
  }

  /**
   * Returns a new producer that creates {@link Set} futures from the union of the given
   * {@link Producer} instances.
   */
  @SafeVarargs
  public static <T> Producer<Set<T>> create(Producer<Set<T>>... producers) {
    return new SetProducer<T>(ImmutableSet.copyOf(producers));
  }

  private final Set<Producer<Set<T>>> contributingProducers;

  private SetProducer(Set<Producer<Set<T>>> contributingProducers) {
    super();
    this.contributingProducers = contributingProducers;
  }

  /**
   * Returns a future {@link Set} whose iteration order is that of the elements given by each of the
   * producers, which are invoked in the order given at creation.
   *
   * <p>If any of the delegate sets, or any elements therein, are null, then this future will fail
   * with a NullPointerException.
   *
   * <p>Canceling this future will attempt to cancel all of the component futures, and if any of the
   * delegate futures fails or is canceled, this one is, too.
   *
   * @throws NullPointerException if any of the delegate producers return null
   */
  @Override
  public ListenableFuture<Set<T>> compute(ProducerMonitor unusedMonitor) {
    List<ListenableFuture<Set<T>>> futureSets =
        new ArrayList<ListenableFuture<Set<T>>>(contributingProducers.size());
    for (Producer<Set<T>> producer : contributingProducers) {
      ListenableFuture<Set<T>> futureSet = producer.get();
      if (futureSet == null) {
        throw new NullPointerException(producer + " returned null");
      }
      futureSets.add(futureSet);
    }
    return Futures.transform(Futures.allAsList(futureSets), new Function<List<Set<T>>, Set<T>>() {
      @Override public Set<T> apply(List<Set<T>> sets) {
        ImmutableSet.Builder<T> builder = ImmutableSet.builder();
        for (Set<T> set : sets) {
          builder.addAll(set);
        }
        return builder.build();
      }
    });
  }
}
