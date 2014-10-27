/*
 * Copyright (C) 2013 Google Inc.
 * Copyright (C) 2013 Square Inc.
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
package dagger;

import dagger.internal.TestingLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Test Singleton and Lazy bindings for thread-safety.
 */
@RunWith(JUnit4.class)
public final class ThreadSafetyTest {
  private static final Integer FIRST_VALUE = 0;
  private static final int THREAD_COUNT = 100;

  private final ExecutorService es = Executors.newFixedThreadPool(THREAD_COUNT);
  private final CountDownLatch latch = new CountDownLatch(THREAD_COUNT + 1);

  static class LazyEntryPoint {
    @Inject Lazy<Integer> lazy;
  }

  @Module(injects = { Long.class, LazyEntryPoint.class })
  static class LatchingModule {
    private final AtomicInteger count = new AtomicInteger(FIRST_VALUE);
    private final CountDownLatch latch;
    LatchingModule(CountDownLatch latch) {
      this.latch = latch;
    }

    @Provides @Singleton Long provideLong() {
      return Long.valueOf(provideInteger());
    }

    @Provides Integer provideInteger() {
      try {
        latch.await();
      } catch (InterruptedException e) {
        throw new AssertionError("Interrupted Thread!!");
      }
      return count.getAndIncrement();
    }
  }

  @Test public void concurrentSingletonAccess() throws Exception {
    final List<Future<Long>> futures = new ArrayList<Future<Long>>();
    final ObjectGraph graph =
        ObjectGraph.createWith(new TestingLoader(), new LatchingModule(latch));
    for (int i = 0; i < THREAD_COUNT; i++) {
      futures.add(es.submit(new Callable<Long>() {
        @Override public Long call() {
          latch.countDown();
          return graph.get(Long.class);
        }
      }));
    }
    latch.countDown();
    for (Future<Long> future : futures) {
      assertThat(future.get(1, TimeUnit.SECONDS))
          .overridingErrorMessage("Lock failure - count should never increment")
          .isEqualTo(0);
    }
  }

  @Test public void concurrentLazyAccess() throws Exception {
    final List<Future<Integer>> futures = new ArrayList<Future<Integer>>();
    final ObjectGraph graph =
        ObjectGraph.createWith(new TestingLoader(), new LatchingModule(latch));
    final LazyEntryPoint lep = graph.get(LazyEntryPoint.class);
    for (int i = 0; i < THREAD_COUNT; i++) {
      futures.add(es.submit(new Callable<Integer>() {
        @Override public Integer call() {
          latch.countDown();
          return lep.lazy.get();
        }
      }));
    }
    latch.countDown();
    for (Future<Integer> future : futures) {
      assertThat(future.get(1, TimeUnit.SECONDS))
          .overridingErrorMessage("Lock failure - count should never increment")
          .isEqualTo(0);
    }
  }
}
