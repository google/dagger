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
package dagger2.producers.internal;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import dagger2.producers.Producer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

/**
 * Tests {@link AbstractProducer}.
 */
@RunWith(JUnit4.class)
public class AbstractProducerTest {
  @Test public void get_nullPointerException() {
    Producer<Object> producer = new AbstractProducer<Object>() {
      @Override public ListenableFuture<Object> compute() {
        return null;
      }
    };
    try {
      producer.get();
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test public void get() throws Exception {
    Producer<Integer> producer = new AbstractProducer<Integer>() {
      int i = 0;

      @Override public ListenableFuture<Integer> compute() {
        return Futures.immediateFuture(i++);
      }
    };
    assertThat(producer.get().get()).isEqualTo(0);
    assertThat(producer.get().get()).isEqualTo(0);
    assertThat(producer.get().get()).isEqualTo(0);
  }
}
