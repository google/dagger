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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

/**
 * Tests {@link Producers}.
 */
@RunWith(JUnit4.class)
public class ProducersTest {
  @Test public void createFutureProduced_success() throws Exception {
    ListenableFuture<String> future = Futures.immediateFuture("monkey");
    ListenableFuture<Produced<String>> producedFuture = Producers.createFutureProduced(future);
    assertThat(producedFuture.isDone()).isTrue();
    assertThat(producedFuture.get().get()).isEqualTo("monkey");
  }

  @Test public void createFutureProduced_failure() throws Exception {
    ListenableFuture<String> future = Futures.immediateFailedFuture(new RuntimeException("monkey"));
    ListenableFuture<Produced<String>> producedFuture = Producers.createFutureProduced(future);
    assertThat(producedFuture.isDone()).isTrue();
    assertThat(getProducedException(producedFuture.get()).getCause()).hasMessage("monkey");
  }

  @Test public void createFutureProduced_cancelPropagatesBackwards() throws Exception {
    ListenableFuture<String> future = SettableFuture.create();
    ListenableFuture<Produced<String>> producedFuture = Producers.createFutureProduced(future);
    assertThat(producedFuture.isDone()).isFalse();
    producedFuture.cancel(false);
    assertThat(future.isCancelled()).isTrue();
  }

  @Test public void createFutureProduced_cancelDoesNotPropagateForwards() throws Exception {
    ListenableFuture<String> future = SettableFuture.create();
    ListenableFuture<Produced<String>> producedFuture = Producers.createFutureProduced(future);
    assertThat(producedFuture.isDone()).isFalse();
    future.cancel(false);
    assertThat(producedFuture.isCancelled()).isFalse();
    assertThat(getProducedException(producedFuture.get()).getCause())
        .isInstanceOf(CancellationException.class);
  }

  private <T> ExecutionException getProducedException(Produced<T> produced) {
    try {
      T value = produced.get();
      throw new IllegalArgumentException("produced did not throw, but returned " + value);
    } catch (ExecutionException e) {
      return e;
    }
  }

  @Test public void createFutureSingletonSet_success() throws Exception {
    ListenableFuture<String> future = Futures.immediateFuture("monkey");
    ListenableFuture<Set<String>> setFuture = Producers.createFutureSingletonSet(future);
    assertThat(setFuture.isDone()).isTrue();
    assertThat(setFuture.get()).containsExactly("monkey");
  }

  @Test public void createFutureSingletonSet_failure() throws Exception {
    ListenableFuture<String> future = Futures.immediateFailedFuture(new RuntimeException("monkey"));
    ListenableFuture<Set<String>> setFuture = Producers.createFutureSingletonSet(future);
    assertThat(setFuture.isDone()).isTrue();
    try {
      setFuture.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).hasMessage("monkey");
    }
  }

  @Test public void producerFromProvider() throws Exception {
    Producer<Integer> producer = Producers.producerFromProvider(new Provider<Integer>() {
      int i = 0;

      @Override public Integer get() {
        return i++;
      }
    });
    assertThat(producer.get().get()).isEqualTo(0);
    assertThat(producer.get().get()).isEqualTo(0);
    assertThat(producer.get().get()).isEqualTo(0);
  }
}
