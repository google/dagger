/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.producers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.testing.EqualsTester;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link Produced}.
 */
@RunWith(JUnit4.class)
public class ProducedTest {
  @Test public void successfulProduced() throws ExecutionException {
    Object o = new Object();
    assertThat(Produced.successful(5).get()).isEqualTo(5);
    assertThat(Produced.successful("monkey").get()).isEqualTo("monkey");
    assertThat(Produced.successful(o).get()).isSameInstanceAs(o);
  }

  @Test public void failedProduced() {
    RuntimeException cause = new RuntimeException("monkey");
    try {
      Produced.failed(cause).get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isSameInstanceAs(cause);
    }
  }

  @Test public void producedEquivalence() {
    RuntimeException e1 = new RuntimeException("monkey");
    RuntimeException e2 = new CancellationException();
    new EqualsTester()
        .addEqualityGroup(Produced.successful(132435), Produced.successful(132435))
        .addEqualityGroup(Produced.successful("hi"), Produced.successful("hi"))
        .addEqualityGroup(Produced.failed(e1), Produced.failed(e1))
        .addEqualityGroup(Produced.failed(e2), Produced.failed(e2))
        .testEquals();
  }
}
