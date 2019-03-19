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

package dagger.functional.producers.badexecutor;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.functional.producers.ExecutorModule;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** This test verifies behavior when the executor throws {@link RejectedExecutionException}. */
@RunWith(JUnit4.class)
public final class BadExecutorTest {
  private SimpleComponent component;

  @Before
  public void setUpComponent() {
    ComponentDependency dependency =
        new ComponentDependency() {
          @Override
          public ListenableFuture<Double> doubleDep() {
            return Futures.immediateFuture(42.0);
          }
        };
    ListeningExecutorService executorService = MoreExecutors.newDirectExecutorService();
    component =
        DaggerSimpleComponent.builder()
            .executorModule(new ExecutorModule(executorService))
            .componentDependency(dependency)
            .build();
    executorService.shutdown();
  }

  @Test
  public void rejectNoArgMethod() throws Exception {
    try {
      component.noArgStr().get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isInstanceOf(RejectedExecutionException.class);
    }
  }

  @Test
  public void rejectSingleArgMethod() throws Exception {
    try {
      component.singleArgInt().get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isInstanceOf(RejectedExecutionException.class);
    }
  }

  @Test
  public void rejectSingleArgFromComponentDepMethod() throws Exception {
    try {
      component.singleArgBool().get();
      fail();
    } catch (ExecutionException e) {
      assertThat(e).hasCauseThat().isInstanceOf(RejectedExecutionException.class);
    }
  }

  @Test
  public void doNotRejectComponentDepMethod() throws Exception {
    assertThat(component.doubleDep().get()).isEqualTo(42.0);
  }
}
