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

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import dagger.producers.Producers;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link SetProducer}.
 */
@RunWith(JUnit4.class)
public class SetProducerTest {
  @Test
  public void success() throws Exception {
    Producer<Set<Integer>> producer =
        SetProducer.<Integer>builder(1, 1)
            .addProducer(Producers.immediateProducer(1))
            .addCollectionProducer(Producers.<Set<Integer>>immediateProducer(ImmutableSet.of(5, 7)))
            .build();
    assertThat(producer.get().get()).containsExactly(1, 5, 7);
  }

  @Test
  public void delegateNpe() throws Exception {
    Producer<Set<Integer>> producer =
        SetProducer.<Integer>builder(1, 0)
            .addProducer(Producers.<Integer>immediateProducer(null))
            .build();
    ListenableFuture<Set<Integer>> future = producer.get();
    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isInstanceOf(NullPointerException.class);
    }
  }

  @Test
  public void delegateSetNpe() throws Exception {
    Producer<Set<Integer>> producer =
        SetProducer.<Integer>builder(0, 1)
            .addCollectionProducer(Producers.<Set<Integer>>immediateProducer(null))
            .build();
    ListenableFuture<Set<Integer>> future = producer.get();
    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isInstanceOf(NullPointerException.class);
    }
  }

  @Test
  public void delegateElementNpe() throws Exception {
    Producer<Set<Integer>> producer =
        SetProducer.<Integer>builder(0, 2)
            .addCollectionProducer(Producers.<Set<Integer>>immediateProducer(ImmutableSet.of(1, 2)))
            .addCollectionProducer(
                Producers.<Set<Integer>>immediateProducer(Collections.<Integer>singleton(null)))
            .build();
    ListenableFuture<Set<Integer>> future = producer.get();
    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isInstanceOf(NullPointerException.class);
    }
  }
}
