/*
 * Copyright (C) 2025 The Dagger Authors.
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

package dagger.functional.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.Provides;
import dagger.producers.ProducerModule;
import dagger.producers.Produces;
import dagger.producers.Production;
import dagger.producers.ProductionComponent;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ProvidesInProducerTest {
  @ProductionComponent(modules = TestModule.class)
  interface TestComponent {
    ListenableFuture<String> string();
  }

  @ProducerModule
  static class TestModule {
    @Provides
    @Production
    static Executor provideExecutor() {
      return MoreExecutors.directExecutor();
    }

    @Produces
    static String produceString() {
      return "produced";
    }
  }

  @Test
  public void compileTest() throws Exception {
    TestComponent component = DaggerProvidesInProducerTest_TestComponent.create();
    assertThat(component.string().isDone()).isTrue();
    assertThat(Futures.getDone(component.string())).isEqualTo("produced");
  }
}
