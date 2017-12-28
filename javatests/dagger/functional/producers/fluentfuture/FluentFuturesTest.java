/*
 * Copyright (C) 2017 The Dagger Authors.
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

package dagger.functional.producers.fluentfuture;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.util.concurrent.FluentFuture;
import dagger.functional.producers.fluentfuture.FluentFutures.Component;
import dagger.functional.producers.fluentfuture.FluentFutures.Dependency;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FluentFuturesTest {

  @Test
  public void testFluentFutures() throws Exception {
    Component component =
        DaggerFluentFutures_Component.builder()
            .executor(directExecutor())
            .dependency(
                new Dependency() {
                  @Override
                  public FluentFuture<Float> floatFuture() {
                    return FluentFuture.from(immediateFuture(42.0f));
                  }
                })
            .build();
    assertThat(component.string().isDone()).isTrue();
    assertThat(component.string().get()).isEqualTo("hello");
    assertThat(component.setOfDouble().isDone()).isTrue();
    assertThat(component.setOfDouble().get()).containsExactly(5.0, 6.0, 7.0, 42.0);
  }
}
