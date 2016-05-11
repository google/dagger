/*
 * Copyright (C) 2014 Google, Inc.
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

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Provider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.collect.DiscreteDomain.integers;
import static com.google.common.truth.Truth.assert_;

@RunWith(JUnit4.class)
@SuppressWarnings("unchecked")
public class SetFactoryTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void providerReturnsNullSet() {
    Factory<Set<Integer>> factory = SetFactory.create(new Provider<Set<Integer>>() {
      @Override
      public Set<Integer> get() {
        return null;
      }
    }, incrementingIntegerProvider(0));
    thrown.expect(NullPointerException.class);
    factory.get();
  }

  @Test
  public void providerReturnsNullSet_single() {
    Factory<Set<Integer>> factory = SetFactory.create(new Provider<Set<Integer>>() {
      @Override
      public Set<Integer> get() {
        return null;
      }
    });
    thrown.expect(NullPointerException.class);
    factory.get();
  }

  @Test
  public void providerReturnsSetWithNullElement() {
    Factory<Set<Integer>> factory = SetFactory.create(new Provider<Set<Integer>>() {
      @Override
      public Set<Integer> get() {
        LinkedHashSet<Integer> result = new LinkedHashSet<Integer>();
        result.add(1);
        result.add(null);
        result.add(3);
        return result;
      }
    });
    thrown.expect(NullPointerException.class);
    factory.get();
  }

  @Test
  public void providerReturnsSetWithNullElement_single() {
    Factory<Set<Integer>> factory = SetFactory.create(new Provider<Set<Integer>>() {
      @Override
      public Set<Integer> get() {
        LinkedHashSet<Integer> result = new LinkedHashSet<Integer>();
        result.add(1);
        result.add(null);
        result.add(3);
        return result;
      }
    }, incrementingIntegerProvider(0));
    thrown.expect(NullPointerException.class);
    factory.get();
  }

  @Test
  public void invokesProvidersEverytTime() {
    Factory<Set<Integer>> factory = SetFactory.create(
        incrementingIntegerProvider(0),
        incrementingIntegerProvider(10),
        incrementingIntegerProvider(20));
    assert_().that(factory.get()).containsExactly(0, 10, 20);
    assert_().that(factory.get()).containsExactly(1, 11, 21);
    assert_().that(factory.get()).containsExactly(2, 12, 22);
  }

  @Test
  public void iterationOrder() {
    Factory<Set<Integer>> factory = SetFactory.create(
        integerSetProvider(Range.closed(5, 9)),
        integerSetProvider(Range.closed(3, 6)),
        integerSetProvider(Range.closed(0, 5)));
    assert_().that(factory.get()).containsExactly(5, 6, 7, 8, 9, 3, 4, 0, 1, 2).inOrder();
  }

  private static Provider<Set<Integer>> incrementingIntegerProvider(int seed) {
    final AtomicInteger value = new AtomicInteger(seed);
    return new Provider<Set<Integer>>() {
      @Override
      public Set<Integer> get() {
        return ImmutableSet.of(value.getAndIncrement());
      }
    };
  }

  private static Provider<Set<Integer>> integerSetProvider(Range<Integer> range) {
    final ContiguousSet<Integer> set = ContiguousSet.create(range, integers());
    return new Provider<Set<Integer>>() {
      @Override
      public Set<Integer> get() {
        return set;
      }
    };
  }
}
