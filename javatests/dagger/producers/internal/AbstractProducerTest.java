/*
 * Copyright (C) 2014 The Dagger Authors.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link AbstractProducer}.
 */
@RunWith(JUnit4.class)
public class AbstractProducerTest {
  @Test
  @SuppressWarnings("CheckReturnValue")
  public void get_nullPointerException() {
    Producer<Object> producer = new DelegateProducer<>(null);
    try {
      producer.get();
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void get() throws Exception {
    Producer<Integer> producer =
        new AbstractProducer<Integer>() {
          int i = 0;

          @Override
          public ListenableFuture<Integer> compute() {
            return Futures.immediateFuture(i++);
          }
        };
    assertThat(producer.get().get()).isEqualTo(0);
    assertThat(producer.get().get()).isEqualTo(0);
    assertThat(producer.get().get()).isEqualTo(0);
  }

  static final class DelegateProducer<T> extends AbstractProducer<T> {
    private final ListenableFuture<T> delegate;

    DelegateProducer(ListenableFuture<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public ListenableFuture<T> compute() {
      return delegate;
    }
  }
}
