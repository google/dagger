/*
 * Copyright (C) 2020 The Dagger Authors.
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

package dagger.hilt.android.internal.testing;

import java.util.Map;
import java.util.Set;

/** Contains the data needed to create a test's component. */
public final class TestComponentData {
  private final ComponentSupplier componentSupplier;
  private final TestInjector<Object> testInjector;
  private final Set<Class<?>> requiredModules;
  private final boolean waitForBindValue;

  public TestComponentData(
      boolean waitForBindValue,
      TestInjector<Object> testInjector,
      Set<Class<?>> requiredModules,
      ComponentSupplier componentSupplier) {
    this.componentSupplier = componentSupplier;
    this.testInjector = testInjector;
    this.requiredModules = requiredModules;
    this.waitForBindValue = waitForBindValue;
  }

  /** Returns the {@link ComponentSupplier}. */
  public ComponentSupplier componentSupplier() {
    return componentSupplier;
  }

  /** Returns the {@link TestInjector}. */
  public TestInjector<Object> testInjector() {
    return testInjector;
  }

  /** Returns thes set of required modules. */
  public Set<Class<?>> requiredModules() {
    return requiredModules;
  }

  /** Returns true if creation of the component needs to wait for bind() to be called. */
  public boolean waitForBindValue() {
    return waitForBindValue;
  }

  /** Returns the component using the given registered modules. */
  public interface ComponentSupplier {
    Object get(Map<Class<?>, ?> registeredModules);
  }
}
