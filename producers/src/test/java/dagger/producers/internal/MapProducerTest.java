/*
 * Copyright (C) 2016 Google, Inc.
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

import com.google.common.collect.ImmutableMap;
import dagger.producers.Producer;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public final class MapProducerTest {
  @Test
  public void success() throws Exception {
    Producer<Map<Integer, String>> mapProducer =
        MapProducer.create(
            Producers.<Map<Integer, Producer<String>>>immediateProducer(
                ImmutableMap.<Integer, Producer<String>>of(
                    15,
                    Producers.<String>immediateProducer("fifteen"),
                    42,
                    Producers.<String>immediateProducer("forty two"))));
    Map<Integer, String> map = mapProducer.get().get();
    assertThat(map).hasSize(2);
    assertThat(map).containsEntry(15, "fifteen");
    assertThat(map).containsEntry(42, "forty two");
  }

  @Test
  public void failingContribution() throws Exception {
    RuntimeException cause = new RuntimeException("monkey");
    Producer<Map<Integer, String>> mapProducer =
        MapProducer.create(
            Producers.<Map<Integer, Producer<String>>>immediateProducer(
                ImmutableMap.<Integer, Producer<String>>of(
                    15,
                    Producers.<String>immediateProducer("fifteen"),
                    42,
                    Producers.<String>immediateFailedProducer(cause))));
    try {
      mapProducer.get().get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isSameAs(cause);
    }
  }

  @Test
  public void failingInput() throws Exception {
    RuntimeException cause = new RuntimeException("monkey");
    Producer<Map<Integer, String>> mapProducer =
        MapProducer.create(
            Producers.<Map<Integer, Producer<String>>>immediateFailedProducer(cause));
    try {
      mapProducer.get().get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e.getCause()).isSameAs(cause);
    }
  }
}
