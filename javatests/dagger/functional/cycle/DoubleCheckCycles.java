/*
 * Copyright (C) 2017 The Dagger Authors.
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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Retention;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.inject.Singleton;

/**
 * A functional test for scoped providers that recursively call {@link Provider#get()} during
 * construction (b/28829473).
 */
interface DoubleCheckCycles {

  /** A qualifier for a non-reentrant scoped binding. */
  @Qualifier
  @Retention(RUNTIME)
  @interface NonReentrant {}

  /** Provides a non-reentrant scoped binding. The provides method should only be called once. */
  @Module
  final class NonReentrantModule {
    int callCount;

    @Provides
    @Singleton
    @NonReentrant
    Object provideNonReentrant() {
      callCount++;
      return new Object();
    }
  }

  /** A qualifier for a reentrant scoped binding. */
  @Qualifier
  @Retention(RUNTIME)
  @interface Reentrant {}

  /**
   * Provides a reentrant scoped binding. The provides method is actually called twice even though
   * it's scoped, but we allow this since the same instance is returned both times.
   */
  @Module
  final class ReentrantModule {
    int callCount;

    @Provides
    @Singleton
    @Reentrant
    Object provideReentrant(@Reentrant Provider<Object> provider) {
      callCount++;
      if (callCount == 1) {
        return provider.get();
      }
      return new Object();
    }
  }

  /** A qualifier for a failing reentrant scoped binding. */
  @Qualifier
  @Retention(RUNTIME)
  @interface FailingReentrant {}

  /**
   * Provides a failing reentrant scoped binding. Similar to the other reentrant module, the
   * provides method is called twice. However, in this case we throw since a different instance is
   * provided for each call.
   */
  @Module
  final class FailingReentrantModule {
    int callCount;

    @Provides
    @Singleton
    @FailingReentrant
    Object provideFailingReentrantA(@FailingReentrant Provider<Object> provider) {
      callCount++;
      if (callCount == 1) {
        provider.get();
        return new Object();
      }
      return new Object();
    }
  }

  @Singleton
  @Component(modules = {
    NonReentrantModule.class,
    ReentrantModule.class,
    FailingReentrantModule.class,
  })
  interface TestComponent {
    @NonReentrant Object getNonReentrant();
    @Reentrant Object getReentrant();
    @FailingReentrant Object getFailingReentrant();
  }
}
