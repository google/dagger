/*
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

import javax.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.fest.assertions.Assertions.assertThat;

@RunWith(JUnit4.class)
public final class LazyInjectionTest {
  @Test public void getLazyDoesNotCauseEntryPointsToBeLoaded() {
    @Module(entryPoints = LazyEntryPoint.class)
    class TestModule {
    }

    ObjectGraph.create(new TestModule());
    assertThat(lazyEntryPointLoaded).isFalse();
  }

  private static boolean lazyEntryPointLoaded = false;
  static class LazyEntryPoint {
    static {
      lazyEntryPointLoaded = true;
    }
  }

  @Test public void getLazyDoesNotCauseProvidesParametersToBeLoaded() {
    @Module
    class TestModule {
      @Provides Object provideObject(LazyProvidesParameter parameter) {
        throw new AssertionError();
      }
    }

    ObjectGraph.create(new TestModule());
    assertThat(lazyProvidesParameterLoaded).isFalse();
  }

  private static boolean lazyProvidesParameterLoaded = false;
  static class LazyProvidesParameter {
    static {
      lazyProvidesParameterLoaded = true;
    }
  }

  @Test public void getLazyDoesNotCauseProvidesResultToBeLoaded() {
    @Module
    class TestModule {
      @Provides LazyProvidesResult provideLazy() {
        throw new AssertionError();
      }
    }

    ObjectGraph.create(new TestModule());
    assertThat(lazyProvidesResultLoaded).isFalse();
  }

  private static boolean lazyProvidesResultLoaded = false;
  static class LazyProvidesResult {
    static {
      lazyProvidesResultLoaded = true;
    }
  }

  @Test public void getLazyDoesNotCauseStaticsToBeLoaded() {
    @Module(staticInjections = LazyInjectStatics.class)
    class TestModule {
    }

    ObjectGraph.create(new TestModule());
    assertThat(LazyInjectStaticsLoaded).isFalse();
  }

  private static boolean LazyInjectStaticsLoaded = false;
  static class LazyInjectStatics {
    static {
      LazyInjectStaticsLoaded = true;
    }
  }

  @Test public void lazyInjectionRequiresProvidesMethod() {
    class TestEntryPoint {
      @Inject String injected;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
      @Provides String provideString(Integer integer) {
        return integer.toString();
      }
      @Provides Integer provideInteger() {
        return 5;
      }
    }

    ObjectGraph objectGraph = ObjectGraph.create(new TestModule());
    TestEntryPoint entryPoint = new TestEntryPoint();
    objectGraph.inject(entryPoint);
    assertThat(entryPoint.injected).isEqualTo("5");
  }
}
