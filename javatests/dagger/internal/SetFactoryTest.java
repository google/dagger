/*
 * Copyright (C) 2014 The Dagger Authors.
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

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Provider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@SuppressWarnings("unchecked")
public class SetFactoryTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void providerReturnsNull() {
    Factory<Set<Integer>> factory =
        SetFactory.<Integer>builder(0, 1).addCollectionProvider(() -> null).build();
    thrown.expect(NullPointerException.class);
    factory.get();
  }

  @Test
  public void providerReturnsNullSet() {
    Factory<Set<Integer>> factory =
        SetFactory.<Integer>builder(1, 0).addProvider(() -> null).build();
    thrown.expect(NullPointerException.class);
    factory.get();
  }

  @Test
  public void providerReturnsSetWithNullElement() {
    Set<Integer> set = new LinkedHashSet<>(Arrays.asList(1, null, 3));
    Factory<Set<Integer>> factory =
        SetFactory.<Integer>builder(0, 1).addCollectionProvider(() -> set).build();
    thrown.expect(NullPointerException.class);
    factory.get();
  }

  @Test
  public void invokesProvidersEveryTime() {
    Factory<Set<Integer>> factory =
        SetFactory.<Integer>builder(2, 2)
            .addProvider(incrementingIntegerProvider(0))
            .addProvider(incrementingIntegerProvider(10))
            .addCollectionProvider(incrementingIntegerSetProvider(20))
            .addCollectionProvider(incrementingIntegerSetProvider(30))
            .build();
    assertThat(factory.get()).containsExactly(0, 10, 20, 21, 30, 31);
    assertThat(factory.get()).containsExactly(1, 11, 22, 23, 32, 33);
    assertThat(factory.get()).containsExactly(2, 12, 24, 25, 34, 35);
  }

  private static Provider<Integer> incrementingIntegerProvider(int seed) {
    final AtomicInteger value = new AtomicInteger(seed);
    return value::getAndIncrement;
  }

  private static Provider<Set<Integer>> incrementingIntegerSetProvider(int seed) {
    final AtomicInteger value = new AtomicInteger(seed);
    return () -> ImmutableSet.of(value.getAndIncrement(), value.getAndIncrement());
  }
}
