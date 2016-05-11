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

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.producers.Producer;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

/**
 * Tests {@link SetProducer}.
 */
@RunWith(JUnit4.class)
public class SetProducerTest {
  @Test public void success() throws Exception {
    Producer<Set<Integer>> producer =
        SetProducer.create(
            Producers.<Set<Integer>>immediateProducer(ImmutableSet.of(1, 2)),
            Producers.<Set<Integer>>immediateProducer(ImmutableSet.of(5, 7)));
    assertThat(producer.get().get()).containsExactly(1, 2, 5, 7);
  }

  @Test public void delegateSetNpe() throws Exception {
    Producer<Set<Integer>> producer =
        SetProducer.create(
            Producers.<Set<Integer>>immediateProducer(ImmutableSet.of(1, 2)),
            Producers.<Set<Integer>>immediateProducer(null));
    ListenableFuture<Set<Integer>> future = producer.get();
    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(NullPointerException.class);
    }
  }

  @Test public void delegateElementNpe() throws Exception {
    Producer<Set<Integer>> producer =
        SetProducer.create(
            Producers.<Set<Integer>>immediateProducer(ImmutableSet.of(1, 2)),
            Producers.<Set<Integer>>immediateProducer(Collections.<Integer>singleton(null)));
    ListenableFuture<Set<Integer>> future = producer.get();
    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(NullPointerException.class);
    }
  }
}
