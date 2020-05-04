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

/** A utility class for getting the component supplier in tests. */
public abstract class TestComponentSupplier {
  /** Returns the component for the given test class */
  protected abstract Map<Class<?>, ComponentSupplier> get();

  /** Returns the test injector for the given test class */
  protected abstract Map<Class<?>, TestInjector<Object>> testInjectors();

  /** Returns the list of required modules for a given test class */
  protected abstract Map<Class<?>, Set<Class<?>>> requiredModules();

  /** Returns true if creation of the component needs to wait for bind() to be called. */
  protected abstract Map<Class<?>, Boolean> waitForBindValue();

  /** Returns the component using the given registered modules. */
  protected interface ComponentSupplier {
    Object get(Map<Class<?>, ?> registeredModules);
  }
}
