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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests {@link SetOfProducedProducer}.
 */
@RunWith(JUnit4.class)
public class SetOfProducedProducerTest {
  @Test
  public void success() throws Exception {
    Producer<Set<Produced<Integer>>> producer =
        SetOfProducedProducer.create(
            Producers.<Set<Integer>>immediateProducer(ImmutableSet.of(1, 2)),
            Producers.<Set<Integer>>immediateProducer(ImmutableSet.of(5, 7)));
    assertThat(producer.get().get())
        .containsExactly(
            Produced.successful(1),
            Produced.successful(2),
            Produced.successful(5),
            Produced.successful(7));
  }

  @Test
  public void failure() throws Exception {
    RuntimeException e = new RuntimeException("monkey");
    Producer<Set<Produced<Integer>>> producer =
        SetOfProducedProducer.create(
            Producers.<Set<Integer>>immediateProducer(ImmutableSet.of(1, 2)),
            Producers.<Set<Integer>>immediateFailedProducer(e));
    assertThat(producer.get().get())
        .containsExactly(
            Produced.successful(1), Produced.successful(2), Produced.<Integer>failed(e));
  }

  @Test
  public void delegateSetNpe() throws Exception {
    Producer<Set<Produced<Integer>>> producer =
        SetOfProducedProducer.create(Producers.<Set<Integer>>immediateProducer(null));
    Results<Integer> results = Results.create(producer.get().get());
    assertThat(results.successes).isEmpty();
    assertThat(results.failures).hasSize(1);
    assertThat(Iterables.getOnlyElement(results.failures).getCause())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void oneOfDelegateSetNpe() throws Exception {
    Producer<Set<Produced<Integer>>> producer =
        SetOfProducedProducer.create(
            Producers.<Set<Integer>>immediateProducer(null),
            Producers.<Set<Integer>>immediateProducer(ImmutableSet.of(7, 3)));
    Results<Integer> results = Results.create(producer.get().get());
    assertThat(results.successes).containsExactly(3, 7);
    assertThat(results.failures).hasSize(1);
    assertThat(Iterables.getOnlyElement(results.failures).getCause())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void delegateElementNpe() throws Exception {
    Producer<Set<Produced<Integer>>> producer =
        SetOfProducedProducer.create(
            Producers.<Set<Integer>>immediateProducer(Collections.<Integer>singleton(null)));
    Results<Integer> results = Results.create(producer.get().get());
    assertThat(results.successes).isEmpty();
    assertThat(results.failures).hasSize(1);
    assertThat(Iterables.getOnlyElement(results.failures).getCause())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void oneOfDelegateElementNpe() throws Exception {
    Producer<Set<Produced<Integer>>> producer =
        SetOfProducedProducer.create(
            Producers.<Set<Integer>>immediateProducer(Sets.newHashSet(Arrays.asList(5, 2, null))));
    Results<Integer> results = Results.create(producer.get().get());
    assertThat(results.successes).containsExactly(2, 5);
    assertThat(results.failures).hasSize(1);
    assertThat(Iterables.getOnlyElement(results.failures).getCause())
        .isInstanceOf(NullPointerException.class);
  }

  static final class Results<T> {
    final ImmutableSet<T> successes;
    final ImmutableSet<ExecutionException> failures;

    private Results(ImmutableSet<T> successes, ImmutableSet<ExecutionException> failures) {
      this.successes = successes;
      this.failures = failures;
    }

    static <T> Results<T> create(Set<Produced<T>> setOfProduced) {
      ImmutableSet.Builder<T> successes = ImmutableSet.builder();
      ImmutableSet.Builder<ExecutionException> failures = ImmutableSet.builder();
      for (Produced<T> produced : setOfProduced) {
        try {
          successes.add(produced.get());
        } catch (ExecutionException e) {
          failures.add(e);
        }
      }
      return new Results<T>(successes.build(), failures.build());
    }
  }
}
