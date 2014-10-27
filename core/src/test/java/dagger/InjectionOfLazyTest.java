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

import dagger.internal.TestingLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Module(injects = TestEntryPoint.class)
    class TestModule {
      @Provides Integer provideInteger() {
        return counter.incrementAndGet();
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertThat(counter.get()).isEqualTo(0);
    assertThat(ep.i.get().intValue()).isEqualTo(1);
    assertThat(counter.get()).isEqualTo(1);
    assertThat(ep.j.get().intValue()).isEqualTo(2);
    assertThat(ep.i.get().intValue()).isEqualTo(1);
    assertThat(counter.get()).isEqualTo(2);
  }

  @Test public void lazyNullCreation() {
    final AtomicInteger provideCounter = new AtomicInteger(0);
    class TestEntryPoint {
      @Inject Lazy<String> i;
    }
    @Module(injects = TestEntryPoint.class)
    class TestModule {
      @Provides String provideInteger() {
        provideCounter.incrementAndGet();
        return null;
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertThat(provideCounter.get()).isEqualTo(0);
    assertThat(ep.i.get()).isNull();
    assertThat(provideCounter.get()).isEqualTo(1);
    assertThat(ep.i.get()).isNull(); // still null
    assertThat(provideCounter.get()).isEqualTo(1); // still only called once.
  }

  @Test public void providerOfLazyOfSomething() {
    final AtomicInteger counter = new AtomicInteger();
    class TestEntryPoint {
      @Inject Provider<Lazy<Integer>> providerOfLazyInteger;
    }

    @Module(injects = TestEntryPoint.class)
    class TestModule {
      @Provides Integer provideInteger() {
        return counter.incrementAndGet();
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertThat(counter.get()).isEqualTo(0);
    Lazy<Integer> i = ep.providerOfLazyInteger.get();
    assertThat(i.get().intValue()).isEqualTo(1);
    assertThat(counter.get()).isEqualTo(1);
    assertThat(i.get().intValue()).isEqualTo(1);
    Lazy<Integer> j = ep.providerOfLazyInteger.get();
    assertThat(j.get().intValue()).isEqualTo(2);
    assertThat(counter.get()).isEqualTo(2);
    assertThat(i.get().intValue()).isEqualTo(1);
  }

  @Test public void sideBySideLazyVsProvider() {
    final AtomicInteger counter = new AtomicInteger();
    class TestEntryPoint {
      @Inject Provider<Integer> providerOfInteger;
      @Inject Lazy<Integer> lazyInteger;
    }

    @Module(injects = TestEntryPoint.class)
    class TestModule {
      @Provides Integer provideInteger() {
        return counter.incrementAndGet();
      }
    }

    TestEntryPoint ep = injectWithModule(new TestEntryPoint(), new TestModule());
    assertThat(counter.get()).isEqualTo(0);
    assertThat(counter.get()).isEqualTo(0);
    assertThat(ep.lazyInteger.get().intValue()).isEqualTo(1);
    assertThat(counter.get()).isEqualTo(1);
    assertThat(ep.providerOfInteger.get().intValue()).isEqualTo(2); // fresh instance
    assertThat(ep.lazyInteger.get().intValue()).isEqualTo(1); // still the same instance
    assertThat(counter.get()).isEqualTo(2);
    assertThat(ep.providerOfInteger.get().intValue()).isEqualTo(3); // fresh instance
    assertThat(ep.lazyInteger.get().intValue()).isEqualTo(1); // still the same instance.
  }

  private <T> T injectWithModule(T ep, Object ... modules) {
    return ObjectGraph.createWith(new TestingLoader(), modules).inject(ep);
  }
}
