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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import dagger.functional.producers.cancellation.CancellationComponent.Dependency;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests cancellation of tasks in production components. */
@RunWith(JUnit4.class)
public class ProducerCancellationTest {

  private final ProducerTester tester = new ProducerTester();
  private final CancellationComponent component =
      DaggerCancellationComponent.builder()
          .module(new CancellationModule(tester))
          .dependency(new Dependency(tester))
          .executor(MoreExecutors.directExecutor())
          .build();

  @Test
  public void initialState() {
    tester.assertNoStartedNodes();
  }

  @Test
  public void cancellingOneEntryPoint_cancelsAllRunningNodes() {
    ListenableFuture<String> entryPoint1 = component.entryPoint1();
    tester.assertStarted("leaf2", "leaf3").only();

    assertThat(entryPoint1.cancel(true)).isTrue();
    assertThat(entryPoint1.isCancelled()).isTrue();

    tester.assertCancelled("leaf2", "leaf3").only();

    // The other entry points were also cancelled in the process, from the user's perspective.
    assertThat(component.entryPoint2().get().isCancelled()).isTrue();
    assertThat(component.entryPoint3().isCancelled()).isTrue();

    // The underlying tasks weren't actually started, even though we just requested them above,
    // because the node was cancelled already along with the component.
    tester.assertNotStarted("entryPoint2", "entryPoint3");
  }

  @SuppressWarnings({"CheckReturnValue", "FutureReturnValueIgnored"})
  @Test
  public void cancellingNonEntryPointProducer_doesNotCancelUnderlyingTask() {
    ListenableFuture<String> entryPoint1 = component.entryPoint1();
    tester.assertStarted("leaf2", "leaf3").only();

    tester.complete("leaf2", "leaf3");

    tester.assertStarted("bar");

    // foo's dependencies are complete, but it is not yet started because baz depends on
    // Producer<foo>, so it won't be started until baz calls get() on it.
    // baz not started yet because it needs bar to complete first.
    tester.assertNotStarted("foo", "baz");

    // Complete bar, triggering baz to run. It calls get() on the foo Producer, so that also starts
    // once its dependency leaf1 is complete.
    tester.complete("bar", "leaf1");
    tester.assertStarted("baz", "foo");

    // baz then cancelled the foo Producer's future, but that didn't cancel the underlying task.
    tester.assertNotCancelled("foo");

    // If we cancel the entry point, that does cancel the task.
    entryPoint1.cancel(true);
    tester.assertCancelled("foo");
  }

  @SuppressWarnings({"CheckReturnValue", "FutureReturnValueIgnored"})
  @Test
  public void cancellingProducerFromComponentDependency_cancelsUnderlyingTask() {
    // Start leaf2/leaf3 tasks.
    component.entryPoint1();
    tester.assertStarted("leaf2", "leaf3").only();
    tester.assertNotCancelled("leaf2", "leaf3");

    // Nothing's requested dependencyFuture yet.
    tester.assertNotStarted("dependencyFuture");

    // entryPoint3 injects Producer of dependency future, then cancels that future. Then also
    // returns that future as the entry point.
    ListenableFuture<String> entryPoint = component.entryPoint3();

    tester.assertStarted("dependencyFuture");
    tester.assertCancelled("dependencyFuture");

    // Even though the entry point future returned from the component is not the dependency future
    // itself, the cancellation should have propagated out to it and cancelled it.
    assertThat(entryPoint.isCancelled()).isTrue();

    // And that cancellation should have cancelled the other tasks running in the component.
    tester.assertCancelled("leaf2", "leaf3");
  }
}
