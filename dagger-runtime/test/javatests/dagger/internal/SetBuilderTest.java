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

package dagger.internal;

import static org.junit.Assert.fail;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SetBuilderTest {
  private SetBuilder<String> setBuilder;

  @Before
  public void setUp() {
    setBuilder = SetBuilder.newSetBuilder(1);
  }

  @Test
  public void addNull() {
    try {
      setBuilder.add(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void addNullCollection() {
    try {
      setBuilder.addAll(null);
      fail();
    } catch (NullPointerException expected) {
    }
  }

  @Test
  public void addNullElement() {
    try {
      setBuilder.addAll(Arrays.asList("hello", null, "world"));
      fail();
    } catch (NullPointerException expected) {
    }
  }
}
