/*
 * Copyright (C) 2018 The Dagger Authors.
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

package dagger.functional.producers.cancellation;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.functional.producers.cancellation.CancellationComponent.Dependency;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests cancellation of tasks in production subcomponents. */
@RunWith(JUnit4.class)
public class ProducerSubcomponentCancellationTest {

  private final ProducerTester tester = new ProducerTester();
  private final CancellationComponent component =
      DaggerCancellationComponent.builder()
          .module(new CancellationModule(tester))
          .dependency(new Dependency(tester))
          .executor(MoreExecutors.directExecutor())
          .build();
  private final CancellationSubcomponent subcomponent =
      component.subcomponentBuilder().module(new CancellationSubcomponentModule(tester)).build();

  @Test
  public void initialState() {
    tester.assertNoStartedNodes();
  }

  @Test
  public void cancellingSubcomponent_doesNotCancelParent() throws Exception {
    ListenableFuture<String> subcomponentEntryPoint = subcomponent.subcomponentEntryPoint();

    // Subcomponent entry point depends on all leaves from the parent component and on the single
    // leaf in the subcomponent itself, so they should all have started.
    tester.assertStarted("leaf1", "leaf2", "leaf3", "subLeaf").only();

    assertThat(subcomponentEntryPoint.cancel(true)).isTrue();
    assertThat(subcomponentEntryPoint.isCancelled()).isTrue();

    // None of the tasks running in the parent were cancelled.
    tester.assertNotCancelled("leaf1", "leaf2", "leaf3");
    tester.assertCancelled("subLeaf").only();

    // Finish all the parent tasks to ensure that it can still complete normally.
    tester.complete(
        "dependencyFuture",
        "leaf1",
        "leaf2",
        "leaf3",
        "foo",
        "bar",
        "baz",
        "qux",
        "entryPoint1",
        "entryPoint2");

    assertThat(component.entryPoint1().get(1, MILLISECONDS)).isEqualTo("completed");
    assertThat(component.entryPoint2().get().get(1, MILLISECONDS)).isEqualTo("completed");
  }

  @Test
  public void cancellingSubcomponent_preventsUnstartedNodesFromStarting() {
    ListenableFuture<String> subcomponentEntryPoint = subcomponent.subcomponentEntryPoint();

    tester.complete("subLeaf");
    tester.assertNotStarted("subTask1", "subTask2");

    subcomponentEntryPoint.cancel(true);

    // Complete the remaining dependencies of subTask1 and subTask2.
    tester.complete("leaf1", "leaf2", "leaf3", "foo", "bar", "baz", "qux");

    // Since the subcomponent was cancelled, they are not started.
    tester.assertNotStarted("subTask1", "subTask2");
  }

  @Test
  public void cancellingProducerFromComponentDependency_inSubcomponent_cancelsUnderlyingTask()
      throws Exception {
    // Request subcomponent's entry point.
    ListenableFuture<String> subcomponentEntryPoint = subcomponent.subcomponentEntryPoint();

    // Finish all parent tasks so that the subcomponent's tasks can start.
    tester.complete("leaf1", "leaf2", "leaf3", "foo", "bar", "baz", "qux", "subLeaf");

    tester.assertStarted("subTask1", "subTask2");
    tester.assertNotCancelled("subTask1", "subTask2");

    // When subTask2 runs, it cancels the dependency future.
    // TODO(cgdecker): Is this what we want to happen?
    // On the one hand, there's a policy of "futures from component dependencies come from outside
    // our control and should be cancelled unconditionally". On the other hand, the dependency is
    // coming from the parent component, and the policy is also not to cancel things belonging to
    // the parent unless it allows that.
    tester.assertCancelled("dependencyFuture");

    // The future it returns didn't depend directly on that future, though, so the subcomponent
    // should be able to complete normally.
    tester.complete("subTask1", "subTask2", "subEntryPoint");

    assertThat(subcomponentEntryPoint.get(1, MILLISECONDS)).isEqualTo("completed");
  }
}
