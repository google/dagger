/*
 * Copyright (C) 2016 The Dagger Authors.
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

import dagger.producers.Producer;
import dagger.producers.Producers;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MapOfProducerProducerTest {
  @Test
  public void success() throws Exception {
    MapOfProducerProducer<Integer, String> mapOfProducerProducer =
        MapOfProducerProducer.<Integer, String>builder(2)
            .put(15, Producers.<String>immediateProducer("fifteen"))
            .put(42, Producers.<String>immediateProducer("forty two"))
            .build();
    Map<Integer, Producer<String>> map = mapOfProducerProducer.get().get();
    assertThat(map).hasSize(2);
    assertThat(map).containsKey(15);
    assertThat(map.get(15).get().get()).isEqualTo("fifteen");
    assertThat(map).containsKey(42);
    assertThat(map.get(42).get().get()).isEqualTo("forty two");
  }

  @Test
  public void failingContributionDoesNotFailMap() throws Exception {
    RuntimeException cause = new RuntimeException("monkey");
    MapOfProducerProducer<Integer, String> mapOfProducerProducer =
        MapOfProducerProducer.<Integer, String>builder(2)
            .put(15, Producers.<String>immediateProducer("fifteen"))
            .put(42, Producers.<String>immediateFailedProducer(cause))
            .build();
    Map<Integer, Producer<String>> map = mapOfProducerProducer.get().get();
    assertThat(map).hasSize(2);
    assertThat(map).containsKey(15);
    assertThat(map.get(15).get().get()).isEqualTo("fifteen");
    assertThat(map).containsKey(42);
    try {
      map.get(42).get().get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isSameInstanceAs(cause);
    }
  }
}
