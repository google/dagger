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
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.fail;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Retention;
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
    assertThat(first).isSameAs(second);
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
    assertThat(first).isSameAs(second);
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
}
