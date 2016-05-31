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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.DaggerCollections.hasDuplicates;
import static dagger.internal.DaggerCollections.presizedList;

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
   * Constructs a new {@link Builder} for a {@link SetProducer} with {@code individualProducerSize}
   * individual {@code Producer<T>} and {@code setProducerSize} {@code Producer<Set<T>>} instances.
   */
  public static <T> Builder<T> builder(int individualProducerSize, int setProducerSize) {
    return new Builder<T>(individualProducerSize, setProducerSize);
  }

  /**
   * A builder to accumulate {@code Producer<T>} and {@code Producer<Set<T>>} instances. These are
   * only intended to be single-use and from within generated code. Do <em>NOT</em> add producers
   * after calling {@link #build()}.
   */
  public static final class Builder<T> {
    private final List<Producer<T>> individualProducers;
    private final List<Producer<Set<T>>> setProducers;

    private Builder(int individualProducerSize, int setProducerSize) {
      individualProducers = presizedList(individualProducerSize);
      setProducers = presizedList(setProducerSize);
    }

    public Builder<T> addProducer(Producer<T> individualProducer) {
      assert individualProducer != null : "Codegen error? Null producer";
      individualProducers.add(individualProducer);
      return this;
    }

    public Builder<T> addSetProducer(Producer<Set<T>> multipleProducer) {
      assert multipleProducer != null : "Codegen error? Null producer";
      setProducers.add(multipleProducer);
      return this;
    }

    public SetProducer<T> build() {
      assert !hasDuplicates(individualProducers)
          : "Codegen error?  Duplicates in the producer list";
      assert !hasDuplicates(setProducers)
          : "Codegen error?  Duplicates in the producer list";

      return new SetProducer<T>(individualProducers, setProducers);
    }
  }

  private final List<Producer<T>> individualProducers;
  private final List<Producer<Set<T>>> setProducers;

  private SetProducer(
      List<Producer<T>> individualProducers, List<Producer<Set<T>>> setProducers) {
    this.individualProducers = individualProducers;
    this.setProducers = setProducers;
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
  public ListenableFuture<Set<T>> compute() {
    List<ListenableFuture<T>> individualFutures =
        new ArrayList<ListenableFuture<T>>(individualProducers.size());
    for (Producer<T> producer : individualProducers) {
      individualFutures.add(checkNotNull(producer.get()));
    }

    List<ListenableFuture<Set<T>>> futureSets =
        new ArrayList<ListenableFuture<Set<T>>>(setProducers.size() + 1);
    futureSets.add(
        Futures.transform(
            Futures.allAsList(individualFutures),
            // TODO(ronshapiro): make static instances of these transformation functions
            new Function<List<T>, Set<T>>() {
              @Override
              public Set<T> apply(List<T> list) {
                return ImmutableSet.copyOf(list);
              }
            }));

    for (Producer<Set<T>> producer : setProducers) {
      futureSets.add(checkNotNull(producer.get()));
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
