/*
 * Copyright (C) 2012 Google Inc.
 * Copyright (C) 2012 Square Inc.
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

import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Provider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests of injection of Lazy<T> bindings.
 */
@RunWith(JUnit4.class)
public final class InjectionOfLazyTest {
  @Test public void lazyValueCreation() {
    final AtomicInteger counter = new AtomicInteger();
    class TestEntryPoint {
      @Inject Lazy<Integer> i;
      @Inject Lazy<Integer> j;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides Integer provideInteger() {
        return counter.incrementAndGet();
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertEquals(0, counter.get());
    assertEquals(1, ep.i.get().intValue());
    assertEquals(1, counter.get());
    assertEquals(2, ep.j.get().intValue());
    assertEquals(1, ep.i.get().intValue());
    assertEquals(2, counter.get());
  }

  @Test public void lazyNullCreation() {
    final AtomicInteger provideCounter = new AtomicInteger(0);
    class TestEntryPoint {
      @Inject Lazy<String> i;
    }
    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides String provideInteger() {
        provideCounter.incrementAndGet();
        return null;
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertEquals(0, provideCounter.get());
    assertNull(ep.i.get());
    assertEquals(1, provideCounter.get());
    assertNull(ep.i.get()); // still null
    assertEquals(1, provideCounter.get()); // still only called once.
  }

  @Test public void providerOfLazyOfSomething() {
    final AtomicInteger counter = new AtomicInteger();
    class TestEntryPoint {
      @Inject Provider<Lazy<Integer>> providerOfLazyInteger;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides Integer provideInteger() {
        return counter.incrementAndGet();
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertEquals(0, counter.get());
    Lazy<Integer> i = ep.providerOfLazyInteger.get();
    assertEquals(1, i.get().intValue());
    assertEquals(1, counter.get());
    assertEquals(1, i.get().intValue());
    Lazy<Integer> j = ep.providerOfLazyInteger.get();
    assertEquals(2, j.get().intValue());
    assertEquals(2, counter.get());
    assertEquals(1, i.get().intValue());
  }

  @Test public void sideBySideLazyVsProvider() {
    final AtomicInteger counter = new AtomicInteger();
    class TestEntryPoint {
      @Inject Provider<Integer> providerOfInteger;
      @Inject Lazy<Integer> lazyInteger;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides Integer provideInteger() {
        return counter.incrementAndGet();
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertEquals(0, counter.get());
    assertEquals(0, counter.get());
    assertEquals(1, ep.lazyInteger.get().intValue());
    assertEquals(1, counter.get());
    assertEquals(2, ep.providerOfInteger.get().intValue()); // fresh instance
    assertEquals(1, ep.lazyInteger.get().intValue()); // still the same instance
    assertEquals(2, counter.get());
    assertEquals(3, ep.providerOfInteger.get().intValue()); // fresh instance
    assertEquals(1, ep.lazyInteger.get().intValue()); // still the same instance.
  }

  private <T> T injectWithModule(T ep, Object ... modules) {
    return ObjectGraph.create(modules).inject(ep);
  }
}
