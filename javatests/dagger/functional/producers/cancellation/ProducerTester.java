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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Helper for testing producers.
 *
 * <p>Maintains a set of nodes (futures mapped to names) representing the results of different
 * producer nodes and allows those nodes to be "started" (when returned from a producer method),
 * completed, and cancelled, as well as to be queried for their state. Additionally, provides
 * assertions about the state of nodes.
 */
final class ProducerTester {

  private final Map<String, TestFuture> futures = new HashMap<>();

  /** Starts the given node. */
  ListenableFuture<String> start(String node) {
    return getOrCreate(node).start();
  }

  private TestFuture getOrCreate(String node) {
    TestFuture result = futures.get(node);
    if (result == null) {
      result = new TestFuture(node);
      futures.put(node, result);
    }
    return result;
  }

  /** Returns whether or not the given node has been started. */
  boolean isStarted(String node) {
    return futures.containsKey(node) && futures.get(node).isStarted();
  }

  /** Completes of the given nodes. */
  void complete(String... nodes) {
    for (String node : nodes) {
      getOrCreate(node).complete();
    }
  }

  /** Returns whether or not the given node has been cancelled. */
  boolean isCancelled(String node) {
    TestFuture future = futures.get(node);
    return future != null && future.isCancelled();
  }

  /** Asserts that the given nodes have been started. */
  Only assertStarted(String... nodes) {
    return assertAboutNodes(STARTED, nodes);
  }

  /** Asserts that the given nodes have been cancelled. */
  Only assertCancelled(String... nodes) {
    return assertAboutNodes(CANCELLED, nodes);
  }

  /** Asserts that the given nodes have not been started. */
  Only assertNotStarted(String... nodes) {
    return assertAboutNodes(not(STARTED), nodes);
  }

  /** Asserts that the given nodes have not been cancelled. */
  Only assertNotCancelled(String... nodes) {
    return assertAboutNodes(not(CANCELLED), nodes);
  }

  /** Asserts that no nodes in this tester have been started. */
  void assertNoStartedNodes() {
    for (TestFuture future : futures.values()) {
      assertWithMessage("%s is started", future).that(future.isStarted()).isFalse();
    }
  }

  private Only assertAboutNodes(Predicate<? super TestFuture> assertion, String... nodes) {
    ImmutableSet.Builder<TestFuture> builder = ImmutableSet.builder();
    for (String node : nodes) {
      TestFuture future = getOrCreate(node);
      assertWithMessage("%s is %s", future, assertion).that(assertion.test(future)).isTrue();
      builder.add(future);
    }
    return new Only(builder.build(), assertion);
  }

  /**
   * Fluent class for making a previous assertion more strict by specifying that whatever was
   * asserted should be true only for the specified nodes and not for any others.
   */
  final class Only {

    private final ImmutableSet<TestFuture> expected;
    private final Predicate<? super TestFuture> assertion;

    Only(ImmutableSet<TestFuture> expected, Predicate<? super TestFuture> assertion) {
      this.expected = checkNotNull(expected);
      this.assertion = checkNotNull(assertion);
    }

    /**
     * Asserts that the previous assertion was not true for any node other than those that were
     * specified.
     */
    void only() {
      for (TestFuture future : futures.values()) {
        if (!expected.contains(future)) {
          assertWithMessage("%s is %s", future, assertion).that(assertion.test(future)).isFalse();
        }
      }
    }
  }

  /**
   * A simple future for testing that can be marked as having been started and which can be
   * completed with a result.
   */
  private static final class TestFuture extends AbstractFuture<String> {

    private final String name;
    private volatile boolean started;

    private TestFuture(String name) {
      this.name = checkNotNull(name);
    }

    /** Marks this future as having been started and returns it. */
    TestFuture start() {
      this.started = true;
      return this;
    }

    /** Returns whether or not this future's task was started. */
    boolean isStarted() {
      return started;
    }

    /** Completes this future's task by setting a value for it. */
    public void complete() {
      super.set("completed");
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static final Predicate<TestFuture> STARTED =
      new Predicate<TestFuture>() {
        @Override
        public boolean test(TestFuture future) {
          return future.isStarted();
        }

        @Override
        public String toString() {
          return "started";
        }
      };

  private static final Predicate<TestFuture> CANCELLED =
      new Predicate<TestFuture>() {
        @Override
        public boolean test(TestFuture future) {
          return future.isCancelled();
        }

        @Override
        public String toString() {
          return "cancelled";
        }
      };

  /** Version of Predicates.not with a toString() that's nicer for our assertion error messages. */
  private static <T> Predicate<T> not(final Predicate<T> predicate) {
    return new Predicate<T>() {
      @Override
      public boolean test(T input) {
        return !predicate.test(input);
      }

      @Override
      public String toString() {
        return "not " + predicate;
      }
    };
  }
}
