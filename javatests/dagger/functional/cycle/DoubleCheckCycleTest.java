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
import static org.junit.Assert.fail;

import dagger.functional.cycle.DoubleCheckCycles.FailingReentrantModule;
import dagger.functional.cycle.DoubleCheckCycles.NonReentrantModule;
import dagger.functional.cycle.DoubleCheckCycles.ReentrantModule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DoubleCheckCycleTest {
  // TODO(b/77916397): Migrate remaining tests in DoubleCheckTest to functional tests in this class.

  @Test
  public void testNonReentrant() {
    NonReentrantModule module = new NonReentrantModule();
    DoubleCheckCycles.TestComponent component =
        DaggerDoubleCheckCycles_TestComponent.builder().nonReentrantModule(module).build();

    assertThat(module.callCount).isEqualTo(0);
    Object first = component.getNonReentrant();
    assertThat(module.callCount).isEqualTo(1);
    Object second = component.getNonReentrant();
    assertThat(module.callCount).isEqualTo(1);
    assertThat(first).isSameAs(second);
  }

  @Test
  public void testReentrant() {
    ReentrantModule module = new ReentrantModule();
    DoubleCheckCycles.TestComponent component =
        DaggerDoubleCheckCycles_TestComponent.builder().reentrantModule(module).build();

    assertThat(module.callCount).isEqualTo(0);
    Object first = component.getReentrant();
    assertThat(module.callCount).isEqualTo(2);
    Object second = component.getReentrant();
    assertThat(module.callCount).isEqualTo(2);
    assertThat(first).isSameAs(second);
  }

  @Test
  public void testFailingReentrant() {
    FailingReentrantModule module = new FailingReentrantModule();
    DoubleCheckCycles.TestComponent component =
        DaggerDoubleCheckCycles_TestComponent.builder().failingReentrantModule(module).build();

    assertThat(module.callCount).isEqualTo(0);
    try {
      component.getFailingReentrant();
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("Scoped provider was invoked recursively");
    }
    assertThat(module.callCount).isEqualTo(2);
  }
}
