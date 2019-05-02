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
public final class MapProducerTest {
  @Test
  public void success() throws Exception {
    Producer<Map<Integer, String>> mapProducer =
        MapProducer.<Integer, String>builder(2)
            .put(15, Producers.immediateProducer("fifteen"))
            .put(42, Producers.immediateProducer("forty two"))
            .build();
    Map<Integer, String> map = mapProducer.get().get();
    assertThat(map).hasSize(2);
    assertThat(map).containsEntry(15, "fifteen");
    assertThat(map).containsEntry(42, "forty two");
  }

  @Test
  public void failingContribution() throws Exception {
    RuntimeException cause = new RuntimeException("monkey");
    Producer<Map<Integer, String>> mapProducer =
        MapProducer.<Integer, String>builder(2)
            .put(15, Producers.immediateProducer("fifteen"))
            // TODO(ronshapiro): remove the type parameter when we drop java7 support
            .put(42, Producers.<String>immediateFailedProducer(cause))
            .build();
    try {
      mapProducer.get().get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isSameInstanceAs(cause);
    }
  }
}
