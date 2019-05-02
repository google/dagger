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

package dagger.functional.cycle;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.Thread.State.BLOCKED;
import static java.lang.Thread.State.WAITING;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DoubleCheckCycleTest {
  // TODO(b/77916397): Migrate remaining tests in DoubleCheckTest to functional tests in this class.

  /** A qualifier for a reentrant scoped binding. */
  @Retention(RUNTIME)
  @Qualifier
  @interface Reentrant {}

  /** A module to be overridden in each test. */
  @Module
  static class OverrideModule {
    @Provides
    @Singleton
    Object provideObject() {
      throw new IllegalStateException("This method should be overridden in tests");
    }

    @Provides
    @Singleton
    @Reentrant
    Object provideReentrantObject(@Reentrant Provider<Object> provider) {
      throw new IllegalStateException("This method should be overridden in tests");
    }
  }

  @Singleton
  @Component(modules = OverrideModule.class)
  interface TestComponent {
    Object getObject();
    @Reentrant Object getReentrantObject();
  }

  @Test
  public void testNonReentrant() {
    AtomicInteger callCount = new AtomicInteger(0);

    // Provides a non-reentrant binding. The provides method should only be called once.
    DoubleCheckCycleTest.TestComponent component =
        DaggerDoubleCheckCycleTest_TestComponent.builder()
            .overrideModule(
                new OverrideModule() {
                  @Override Object provideObject() {
                    callCount.getAndIncrement();
                    return new Object();
                  }
                })
            .build();

    assertThat(callCount.get()).isEqualTo(0);
    Object first = component.getObject();
    assertThat(callCount.get()).isEqualTo(1);
    Object second = component.getObject();
    assertThat(callCount.get()).isEqualTo(1);
    assertThat(first).isSameInstanceAs(second);
  }

  @Test
  public void testReentrant() {
    AtomicInteger callCount = new AtomicInteger(0);

    // Provides a reentrant binding. Even though it's scoped, the provides method is called twice.
    // In this case, we allow it since the same instance is returned on the second call.
    DoubleCheckCycleTest.TestComponent component =
        DaggerDoubleCheckCycleTest_TestComponent.builder()
            .overrideModule(
                new OverrideModule() {
                  @Override Object provideReentrantObject(Provider<Object> provider) {
                    if (callCount.incrementAndGet() == 1) {
                      return provider.get();
                    }
                    return new Object();
                  }
                })
            .build();

    assertThat(callCount.get()).isEqualTo(0);
    Object first = component.getReentrantObject();
    assertThat(callCount.get()).isEqualTo(2);
    Object second = component.getReentrantObject();
    assertThat(callCount.get()).isEqualTo(2);
    assertThat(first).isSameInstanceAs(second);
  }

  @Test
  public void testFailingReentrant() {
    AtomicInteger callCount = new AtomicInteger(0);

    // Provides a failing reentrant binding. Even though it's scoped, the provides method is called
    // twice. In this case we throw an exception since a different instance is provided on the
    // second call.
    DoubleCheckCycleTest.TestComponent component =
        DaggerDoubleCheckCycleTest_TestComponent.builder()
            .overrideModule(
                new OverrideModule() {
                  @Override Object provideReentrantObject(Provider<Object> provider) {
                    if (callCount.incrementAndGet() == 1) {
                      provider.get();
                      return new Object();
                    }
                    return new Object();
                  }
                })
            .build();

    assertThat(callCount.get()).isEqualTo(0);
    try {
      component.getReentrantObject();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("Scoped provider was invoked recursively");
    }
    assertThat(callCount.get()).isEqualTo(2);
  }

  @Test(timeout = 5000)

  public void testGetFromMultipleThreads() throws Exception {
    AtomicInteger callCount = new AtomicInteger(0);
    AtomicInteger requestCount = new AtomicInteger(0);
    SettableFuture<Object> future = SettableFuture.create();

    // Provides a non-reentrant binding. In this case, we return a SettableFuture so that we can
    // control when the provides method returns.
    DoubleCheckCycleTest.TestComponent component =
        DaggerDoubleCheckCycleTest_TestComponent.builder()
            .overrideModule(
                new OverrideModule() {
                  @Override
                  Object provideObject() {
                    callCount.incrementAndGet();
                    try {
                      return Uninterruptibles.getUninterruptibly(future);
                    } catch (ExecutionException e) {
                      throw new RuntimeException(e);
                    }
                  }
                })
            .build();

    int numThreads = 10;
    CountDownLatch remainingTasks = new CountDownLatch(numThreads);
    List<Thread> tasks = new ArrayList<>(numThreads);
    List<Object> values = Collections.synchronizedList(new ArrayList<>(numThreads));

    // Set up multiple threads that call component.getObject().
    for (int i = 0; i < numThreads; i++) {
      tasks.add(
          new Thread(
              () -> {
                requestCount.incrementAndGet();
                values.add(component.getObject());
                remainingTasks.countDown();
              }));
    }

    // Check initial conditions
    assertThat(remainingTasks.getCount()).isEqualTo(10);
    assertThat(requestCount.get()).isEqualTo(0);
    assertThat(callCount.get()).isEqualTo(0);
    assertThat(values).isEmpty();

    // Start all threads
    tasks.forEach(Thread::start);

    // Wait for all threads to wait/block.
    long waiting = 0;
    while (waiting != numThreads) {
      waiting =
          tasks.stream()
              .map(Thread::getState)
              .filter(state -> state == WAITING || state == BLOCKED)
              .count();
    }

    // Check the intermediate state conditions.
    // * All 10 threads should have requested the binding, but none should have finished.
    // * Only 1 thread should have reached the provides method.
    // * None of the threads should have set a value (since they are waiting for future to be set).
    assertThat(remainingTasks.getCount()).isEqualTo(10);
    assertThat(requestCount.get()).isEqualTo(10);
    assertThat(callCount.get()).isEqualTo(1);
    assertThat(values).isEmpty();

    // Set the future and wait on all remaining threads to finish.
    Object futureValue = new Object();
    future.set(futureValue);
    remainingTasks.await();

    // Check the final state conditions.
    // All values should be set now, and they should all be equal to the same instance.
    assertThat(remainingTasks.getCount()).isEqualTo(0);
    assertThat(requestCount.get()).isEqualTo(10);
    assertThat(callCount.get()).isEqualTo(1);
    assertThat(values).isEqualTo(Collections.nCopies(numThreads, futureValue));
  }
}
