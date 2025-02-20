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

package dagger.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;
import dagger.Lazy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DoubleCheckTest {
  @Test
  public void provider_nullPointerException() {
    try {
      DoubleCheck.provider(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void lazy_nullPointerException() {
    try {
      DoubleCheck.lazy(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  private static final Provider<Object> DOUBLE_CHECK_OBJECT_PROVIDER =
      DoubleCheck.provider(Object::new);

  @Test
  public void doubleWrapping_provider() {
    assertThat(DoubleCheck.provider(DOUBLE_CHECK_OBJECT_PROVIDER))
        .isSameInstanceAs(DOUBLE_CHECK_OBJECT_PROVIDER);
  }

  @Test
  public void doubleWrapping_lazy() {
    assertThat(DoubleCheck.lazy(DOUBLE_CHECK_OBJECT_PROVIDER))
        .isSameInstanceAs(DOUBLE_CHECK_OBJECT_PROVIDER);
  }

  @Test
  public void get() throws Exception {
    int numThreads = 10;
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);

    final CountDownLatch latch = new CountDownLatch(numThreads);
    LatchedProvider provider = new LatchedProvider(latch);
    final Lazy<Object> lazy = DoubleCheck.lazy(provider);

    List<Callable<Object>> tasks = Lists.newArrayListWithCapacity(numThreads);
    for (int i = 0; i < numThreads; i++) {
      tasks.add(
          () -> {
            latch.countDown();
            return lazy.get();
          });
    }

    List<Future<Object>> futures = executor.invokeAll(tasks);

    assertThat(provider.provisions.get()).isEqualTo(1);
    Set<Object> results = Sets.newIdentityHashSet();
    for (Future<Object> future : futures) {
      results.add(future.get());
    }
    assertThat(results).hasSize(1);
  }

  private static class LatchedProvider implements Provider<Object> {
    final AtomicInteger provisions;
    final CountDownLatch latch;

    LatchedProvider(CountDownLatch latch) {
      this.latch = latch;
      this.provisions = new AtomicInteger();
    }

    @Override
    public Object get() {
      if (latch != null) {
        Uninterruptibles.awaitUninterruptibly(latch);
      }
      provisions.incrementAndGet();
      return new Object();
    }
  }

  @Test public void reentranceWithoutCondition_throwsStackOverflow() {
    final AtomicReference<Provider<Object>> doubleCheckReference =
        new AtomicReference<>();
    Provider<Object> doubleCheck = DoubleCheck.provider(() -> doubleCheckReference.get().get());
    doubleCheckReference.set(doubleCheck);
    try {
      doubleCheck.get();
      fail();
    } catch (StackOverflowError expected) {}
  }

  @Test public void reentranceReturningSameInstance() {
    final AtomicReference<Provider<Object>> doubleCheckReference =
        new AtomicReference<>();
    final AtomicInteger invocationCount = new AtomicInteger();
    final Object object = new Object();
    Provider<Object> doubleCheck = DoubleCheck.provider(() -> {
        if (invocationCount.incrementAndGet() == 1) {
         doubleCheckReference.get().get();
       }
       return object;
     });
    doubleCheckReference.set(doubleCheck);
    assertThat(doubleCheck.get()).isSameInstanceAs(object);
  }

  @Test public void reentranceReturningDifferentInstances_throwsIllegalStateException() {
    final AtomicReference<Provider<Object>> doubleCheckReference =
        new AtomicReference<>();
    final AtomicInteger invocationCount = new AtomicInteger();
    Provider<Object> doubleCheck = DoubleCheck.provider(() -> {
       if (invocationCount.incrementAndGet() == 1) {
         doubleCheckReference.get().get();
       }
       return new Object();
     });
    doubleCheckReference.set(doubleCheck);
    try {
      doubleCheck.get();
      fail();
    } catch (IllegalStateException expected) {}
  }

  @Test
  public void instanceFactoryAsLazyDoesNotWrap() {
    Factory<Object> factory = InstanceFactory.create(new Object());
    assertThat(DoubleCheck.lazy(factory)).isSameInstanceAs(factory);
  }
}
