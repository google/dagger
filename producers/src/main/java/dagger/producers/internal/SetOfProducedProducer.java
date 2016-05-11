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
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.monitoring.ProducerMonitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * A {@link Producer} implementation used to implement {@link Set} bindings. This producer returns a
 * future {@code Set<Produced<T>>} whose elements are populated by subsequent calls to the delegate
 * {@link Producer#get} methods.
 *
 * @author Jesse Beder
 * @since 2.0
 */
public final class SetOfProducedProducer<T> extends AbstractProducer<Set<Produced<T>>> {
  public static <T> Producer<Set<T>> create() {
    return SetProducer.create();
  }

  /**
   * Returns a new producer that creates {@link Set} futures from the union of the given
   * {@link Producer} instances.
   */
  @SafeVarargs
  public static <T> Producer<Set<Produced<T>>> create(Producer<Set<T>>... producers) {
    return new SetOfProducedProducer<T>(ImmutableSet.copyOf(producers));
  }

  private final ImmutableSet<Producer<Set<T>>> contributingProducers;

  private SetOfProducedProducer(ImmutableSet<Producer<Set<T>>> contributingProducers) {
    this.contributingProducers = contributingProducers;
  }

  /**
   * Returns a future {@link Set} of {@link Produced} values whose iteration order is that of the
   * elements given by each of the producers, which are invoked in the order given at creation.
   *
   * <p>If any of the delegate sets, or any elements therein, are null, then that corresponding
   * {@code Produced} element will fail with a NullPointerException.
   *
   * <p>Canceling this future will attempt to cancel all of the component futures; but if any of the
   * delegate futures fail or are canceled, this future succeeds, with the appropriate failed
   * {@link Produced}.
   *
   * @throws NullPointerException if any of the delegate producers return null
   */
  @Override
  public ListenableFuture<Set<Produced<T>>> compute(ProducerMonitor unusedMonitor) {
    List<ListenableFuture<Produced<Set<T>>>> futureProducedSets =
        new ArrayList<ListenableFuture<Produced<Set<T>>>>(contributingProducers.size());
    for (Producer<Set<T>> producer : contributingProducers) {
      ListenableFuture<Set<T>> futureSet = producer.get();
      if (futureSet == null) {
        throw new NullPointerException(producer + " returned null");
      }
      futureProducedSets.add(Producers.createFutureProduced(futureSet));
    }
    return Futures.transform(
        Futures.allAsList(futureProducedSets),
        new Function<List<Produced<Set<T>>>, Set<Produced<T>>>() {
          @Override
          public Set<Produced<T>> apply(List<Produced<Set<T>>> producedSets) {
            ImmutableSet.Builder<Produced<T>> builder = ImmutableSet.builder();
            for (Produced<Set<T>> producedSet : producedSets) {
              try {
                Set<T> set = producedSet.get();
                if (set == null) {
                  // TODO(beder): This is a vague exception. Can we somehow point to the failing
                  // producer? See the similar comment in the component writer about null
                  // provisions.
                  builder.add(
                      Produced.<T>failed(
                          new NullPointerException(
                              "Cannot contribute a null set into a producer set binding when it's"
                                  + " injected as Set<Produced<T>>.")));
                } else {
                  for (T value : set) {
                    if (value == null) {
                      builder.add(
                          Produced.<T>failed(
                              new NullPointerException(
                                  "Cannot contribute a null element into a producer set binding"
                                      + " when it's injected as Set<Produced<T>>.")));
                    } else {
                      builder.add(Produced.successful(value));
                    }
                  }
                }
              } catch (ExecutionException e) {
                builder.add(Produced.<T>failed(e.getCause()));
              }
            }
            return builder.build();
          }
        });
  }
}
