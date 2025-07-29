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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@SuppressWarnings("unchecked")
public class MapProviderFactoryTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void nullKey() {
    thrown.expect(NullPointerException.class);
    MapProviderFactory.<String, Integer>builder(1).put(null, incrementingIntegerProvider(1));
  }

  @Test
  public void nullValue() {
    thrown.expect(NullPointerException.class);
    MapProviderFactory.<String, Integer>builder(1).put("Hello", null);
  }


  @Test
  public void iterationOrder() {
    Provider<Integer> p1 = incrementingIntegerProvider(10);
    Provider<Integer> p2 = incrementingIntegerProvider(20);
    Provider<Integer> p3 = incrementingIntegerProvider(30);
    Provider<Integer> p4 = incrementingIntegerProvider(40);
    Provider<Integer> p5 = incrementingIntegerProvider(50);

    Factory<Map<String, Provider<Integer>>> factory = MapProviderFactory
        .<String, Integer>builder(4)
        .put("two", p2)
        .put("one", p1)
        .put("three", p3)
        .put("one", p5)
        .put("four", p4)
        .build();

    Map<String, Provider<Integer>> expectedMap = new LinkedHashMap<>();
    expectedMap.put("two", p2);
    expectedMap.put("one", p1);
    expectedMap.put("three", p3);
    expectedMap.put("one", p5);
    expectedMap.put("four", p4);
    assertThat(factory.get().entrySet())
        .containsExactlyElementsIn(expectedMap.entrySet())
        .inOrder();
  }


  private static Provider<Integer> incrementingIntegerProvider(int seed) {
    return new AtomicInteger(seed)::getAndIncrement;
  }
}
