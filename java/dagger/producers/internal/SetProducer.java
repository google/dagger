/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static dagger.internal.DaggerCollections.hasDuplicates;
import static dagger.internal.DaggerCollections.presizedList;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A {@link Producer} implementation used to implement {@link Set} bindings. This producer returns
 * a future {@link Set} whose elements are populated by subsequent calls to the delegate
 * {@link Producer#get} methods.
 */
public final class SetProducer<T> extends AbstractProducer<Set<T>> {
  private static final Producer<Set<Object>> EMPTY_PRODUCER =
      dagger.producers.Producers.<Set<Object>>immediateProducer(ImmutableSet.<Object>of());

  @SuppressWarnings({"unchecked", "rawtypes"}) // safe covariant cast
  public static <T> Producer<Set<T>> empty() {
    return (Producer) EMPTY_PRODUCER;
  }

  /**
   * Constructs a new {@link Builder} for a {@link SetProducer} with {@code individualProducerSize}
   * individual {@code Producer<T>} and {@code collectionProducerSize} {@code
   * Producer<Collection<T>>} instances.
   */
  public static <T> Builder<T> builder(int individualProducerSize, int collectionProducerSize) {
    return new Builder<T>(individualProducerSize, collectionProducerSize);
  }

  /**
   * A builder to accumulate {@code Producer<T>} and {@code Producer<Collection<T>>} instances.
   * These are only intended to be single-use and from within generated code. Do <em>NOT</em> add
   * producers after calling {@link #build()}.
   */
  public static final class Builder<T> {
    private final List<Producer<T>> individualProducers;
    private final List<Producer<Collection<T>>> collectionProducers;

    private Builder(int individualProducerSize, int collectionProducerSize) {
      individualProducers = presizedList(individualProducerSize);
      collectionProducers = presizedList(collectionProducerSize);
    }

    @SuppressWarnings("unchecked")
    public Builder<T> addProducer(Producer<? extends T> individualProducer) {
      assert individualProducer != null : "Codegen error? Null producer";
      individualProducers.add((Producer<T>) individualProducer);
      return this;
    }

    @SuppressWarnings("unchecked")
    public Builder<T> addCollectionProducer(
        Producer<? extends Collection<? extends T>> multipleProducer) {
      assert multipleProducer != null : "Codegen error? Null producer";
      collectionProducers.add((Producer<Collection<T>>) multipleProducer);
      return this;
    }

    public SetProducer<T> build() {
      assert !hasDuplicates(individualProducers)
          : "Codegen error?  Duplicates in the producer list";
      assert !hasDuplicates(collectionProducers)
          : "Codegen error?  Duplicates in the producer list";

      return new SetProducer<T>(individualProducers, collectionProducers);
    }
  }

  private final List<Producer<T>> individualProducers;
  private final List<Producer<Collection<T>>> collectionProducers;

  private SetProducer(
      List<Producer<T>> individualProducers, List<Producer<Collection<T>>> collectionProducers) {
    this.individualProducers = individualProducers;
    this.collectionProducers = collectionProducers;
  }

  /**
   * Returns a future {@link Set} that contains the elements given by each of the producers.
   *
   * <p>If any of the delegate collections, or any elements therein, are null, then this future will
   * fail with a NullPointerException.
   *
   * <p>Canceling this future will attempt to cancel all of the component futures, and if any of the
   * delegate futures fails or is canceled, this one is, too.
   *
   * @throws NullPointerException if any of the delegate producers return null
   */
  @Override
  public ListenableFuture<Set<T>> compute() {
    List<ListenableFuture<T>> individualFutures =
        new ArrayList<ListenableFuture<T>>(individualProducers.size());
    for (Producer<T> producer : individualProducers) {
      individualFutures.add(checkNotNull(producer.get()));
    }

    // Presize the list of collections produced by the amount of collectionProducers, with one more
    // for the consolidate individualFutures from Futures.allAsList.
    List<ListenableFuture<? extends Collection<T>>> futureCollections =
        new ArrayList<ListenableFuture<? extends Collection<T>>>(collectionProducers.size() + 1);
    futureCollections.add(Futures.allAsList(individualFutures));
    for (Producer<Collection<T>> producer : collectionProducers) {
      futureCollections.add(checkNotNull(producer.get()));
    }
    return transform(
        Futures.allAsList(futureCollections),
        new Function<List<Collection<T>>, Set<T>>() {
          @Override
          public Set<T> apply(List<Collection<T>> sets) {
            ImmutableSet.Builder<T> builder = ImmutableSet.builder();
            for (Collection<T> set : sets) {
              builder.addAll(set);
            }
            return builder.build();
          }
        },
        directExecutor());
  }
}
