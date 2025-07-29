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
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static dagger.internal.DaggerCollections.hasDuplicates;
import static dagger.internal.DaggerCollections.presizedList;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * A {@link Producer} implementation used to implement {@link Set} bindings. This producer returns a
 * future {@code Set<Produced<T>>} whose elements are populated by subsequent calls to the delegate
 * {@link Producer#get} methods.
 */
public final class SetOfProducedProducer<T> extends AbstractProducer<Set<Produced<T>>> {
  public static <T> Producer<Set<T>> empty() {
    return SetProducer.empty();
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

    public SetOfProducedProducer<T> build() {
      assert !hasDuplicates(individualProducers)
          : "Codegen error?  Duplicates in the producer list";
      assert !hasDuplicates(collectionProducers)
          : "Codegen error?  Duplicates in the producer list";

      return new SetOfProducedProducer<T>(individualProducers, collectionProducers);
    }
  }

  private final List<Producer<T>> individualProducers;
  private final List<Producer<Collection<T>>> collectionProducers;

  private SetOfProducedProducer(
      List<Producer<T>> individualProducers, List<Producer<Collection<T>>> collectionProducers) {
    this.individualProducers = individualProducers;
    this.collectionProducers = collectionProducers;
  }

  /**
   * Returns a future {@link Set} of {@link Produced} elements given by each of the producers.
   *
   * <p>If any of the delegate collections, or any elements therein, are null, then that
   * corresponding {@code Produced} element will fail with a NullPointerException.
   *
   * <p>Canceling this future will attempt to cancel all of the component futures; but if any of the
   * delegate futures fail or are canceled, this future succeeds, with the appropriate failed {@link
   * Produced}.
   *
   * @throws NullPointerException if any of the delegate producers return null
   */
  @Override
  public ListenableFuture<Set<Produced<T>>> compute() {
    List<ListenableFuture<? extends Produced<? extends Collection<T>>>> futureProducedCollections =
        new ArrayList<ListenableFuture<? extends Produced<? extends Collection<T>>>>(
            individualProducers.size() + collectionProducers.size());
    for (Producer<T> producer : individualProducers) {
      // TODO(ronshapiro): Don't require individual productions to be added to a collection just to
      // be materialized into futureProducedCollections.
      futureProducedCollections.add(
          Producers.createFutureProduced(
              Producers.createFutureSingletonSet(checkNotNull(producer.get()))));
    }
    for (Producer<Collection<T>> producer : collectionProducers) {
      futureProducedCollections.add(Producers.createFutureProduced(checkNotNull(producer.get())));
    }

    return Futures.transform(
        Futures.allAsList(futureProducedCollections),
        new Function<List<Produced<? extends Collection<T>>>, Set<Produced<T>>>() {
          @Override
          public Set<Produced<T>> apply(
              List<Produced<? extends Collection<T>>> producedCollections) {
            ImmutableSet.Builder<Produced<T>> builder = ImmutableSet.builder();
            for (Produced<? extends Collection<T>> producedCollection : producedCollections) {
              try {
                Collection<T> collection = producedCollection.get();
                if (collection == null) {
                  // TODO(beder): This is a vague exception. Can we somehow point to the failing
                  // producer? See the similar comment in the component writer about null
                  // provisions.
                  builder.add(
                      Produced.<T>failed(
                          new NullPointerException(
                              "Cannot contribute a null collection into a producer set binding when"
                                  + " it's injected as Set<Produced<T>>.")));
                } else {
                  for (T value : collection) {
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
        },
        directExecutor());
  }
}
