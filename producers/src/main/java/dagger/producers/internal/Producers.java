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
package dagger.producers.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.monitoring.ProducerMonitor;
import java.util.Set;
import javax.inject.Provider;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility methods for use in generated producer code.
 *
 * @author Jesse Beder
 * @since 2.0
 */
public final class Producers {
  /**
   * Returns a future of {@link Produced} that represents the completion (either success or failure)
   * of the given future. If the input future succeeds, then the resulting future also succeeds with
   * a successful {@code Produced}; if the input future fails, then the resulting future succeeds
   * with a failing {@code Produced}.
   *
   * <p>Cancelling the resulting future will propagate the cancellation to the input future; but
   * cancelling the input future will trigger the resulting future to succeed with a failing
   * {@code Produced}.
   */
  // TODO(beder): Document what happens with an InterruptedException after you figure out how to
  // trigger one in a test.
  public static <T> ListenableFuture<Produced<T>> createFutureProduced(ListenableFuture<T> future) {
    return Futures.catchingAsync(
        Futures.transform(
            future,
            new Function<T, Produced<T>>() {
              @Override
              public Produced<T> apply(final T value) {
                return Produced.successful(value);
              }
            }),
        Throwable.class,
        Producers.<T>futureFallbackForProduced());

  }

  private static final AsyncFunction<Throwable, Produced<Object>> FUTURE_FALLBACK_FOR_PRODUCED =
      new AsyncFunction<Throwable, Produced<Object>>() {
        @Override
        public ListenableFuture<Produced<Object>> apply(Throwable t) throws Exception {
          Produced<Object> produced = Produced.failed(t);
          return Futures.immediateFuture(produced);
        }
      };

  @SuppressWarnings({"unchecked", "rawtypes"}) // bivariant implementation
  private static <T> AsyncFunction<Throwable, Produced<T>> futureFallbackForProduced() {
    return (AsyncFunction) FUTURE_FALLBACK_FOR_PRODUCED;
  }

  /**
   * Returns a future of a {@code Set} that contains a single element: the result of the input
   * future.
   */
  public static <T> ListenableFuture<Set<T>> createFutureSingletonSet(ListenableFuture<T> future) {
    return Futures.transform(future, new Function<T, Set<T>>() {
      @Override public Set<T> apply(T value) {
        return ImmutableSet.of(value);
      }
    });
  }

  /**
   * Returns a producer that immediately executes the binding logic for the given provider every
   * time it is called.
   */
  public static <T> Producer<T> producerFromProvider(final Provider<T> provider) {
    checkNotNull(provider);
    return new AbstractProducer<T>() {
      @Override
      protected ListenableFuture<T> compute(ProducerMonitor unusedMonitor) {
        return Futures.immediateFuture(provider.get());
      }
    };
  }

  /** Returns a producer that succeeds with the given value. */
  public static <T> Producer<T> immediateProducer(final T value) {
    return new Producer<T>() {
      @Override
      public ListenableFuture<T> get() {
        return Futures.immediateFuture(value);
      }
    };
  }

  /** Returns a producer that fails with the given exception. */
  public static <T> Producer<T> immediateFailedProducer(final Throwable throwable) {
    return new Producer<T>() {
      @Override
      public ListenableFuture<T> get() {
        return Futures.immediateFailedFuture(throwable);
      }
    };
  }

  private Producers() {}
}
