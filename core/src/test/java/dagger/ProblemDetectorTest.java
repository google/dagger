/*
 * Copyright (C) 2012 Square, Inc.
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

import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public final class ProblemDetectorTest {
  @Test public void atInjectCircularDependenciesDetected() {
    class TestEntryPoint {
      @Inject Rock rock;
    }

    @Module(entryPoints = TestEntryPoint.class)
    class TestModule {
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.validate();
      fail();
    } catch (RuntimeException expected) {
    }
  }

  @Test public void providesCircularDependenciesDetected() {
    @Module
    class TestModule {
      @Provides Integer provideInteger(String s) {
        throw new AssertionError();
      }
      @Provides String provideString(Integer i) {
        throw new AssertionError();
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    try {
      graph.validate();
      fail();
    } catch (RuntimeException expected) {
    }
  }

  @Test public void validateLazy() {
    @Module
    class TestModule {
      @Provides Integer dependOnLazy(Lazy<String> lazyString) {
        throw new AssertionError();
      }
      @Provides String provideLazyValue() {
        throw new AssertionError();
      }
    }

    ObjectGraph graph = ObjectGraph.create(new TestModule());
    graph.validate();
  }

  static class Rock {
    @Inject Scissors scissors;
  }

  static class Scissors {
    @Inject Paper paper;
  }

  static class Paper {
    @Inject Rock rock;
  }
}
