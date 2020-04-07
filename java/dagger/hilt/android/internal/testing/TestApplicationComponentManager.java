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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.hilt.android.internal.managers.ComponentSupplier;
import dagger.hilt.android.testing.OnComponentReadyRunner;
import dagger.hilt.internal.GeneratedComponentManager;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.runner.Description;

/**
 * Do not use except in Hilt generated code!
 *
 * <p>A manager for the creation of components that live in the test Application.
 */
public final class TestApplicationComponentManager
    implements GeneratedComponentManager<Object>, OnComponentReadyRunner.Holder {
  private final OnComponentReadyRunner onComponentReadyRunner = new OnComponentReadyRunner();
  private final AtomicReference<Object> component = new AtomicReference<>();
  private final AtomicReference<Description> hasHiltTestRule = new AtomicReference<>();
  private final ComponentSupplier componentSupplier;
  private final ImmutableSet<Class<?>> requiredModules;
  private final Map<Class<?>, Object> registeredModules = new ConcurrentHashMap<>();
  private volatile boolean bindValueCalled = false;
  private final boolean waitForBindValue;

  public TestApplicationComponentManager(
      ComponentSupplier componentSupplier,
      Set<Class<?>> requiredModules,
      boolean waitForBindValue) {
    this.componentSupplier = componentSupplier;
    this.requiredModules = ImmutableSet.copyOf(requiredModules);
    this.waitForBindValue = waitForBindValue;
  }

  @Override
  public Object generatedComponent() {
    if (component.get() == null) {
      Preconditions.checkState(hasHiltTestRule(),
          "The component was not created. Check that you have added the HiltTestRule.");
      Preconditions.checkState(registeredModules.keySet().containsAll(requiredModules),
          "The component was not created. Check that you have registered all test modules:\n"
              + "\tUnregistered: ", Sets.difference(requiredModules, registeredModules.keySet()));
      Preconditions.checkState(bindValueReady(),
          "The test instance has not been set. Did you forget to call #bind()?");
      throw new IllegalStateException("The component has not been created. "
          + "Check that you have called #inject()? Otherwise, "
          + "there is a race between injection and component creation. Make sure there is a "
          + "happens-before edge between the HiltTestRule/registering all test modules and the "
          + "first injection.");
    }
    return component.get();
  }

  /** For framework use only! This flag must be set before component creation. */
  public void setHasHiltTestRule(Description description) {
    Preconditions.checkState(
        // Some exempted tests set the test rule multiple times. Use CAS to avoid setting twice.
        hasHiltTestRule.compareAndSet(null, description),
        "The hasHiltTestRule flag has already been set!");
    tryToCreateComponent();
  }

  @Override
  public OnComponentReadyRunner getOnComponentReadyRunner() {
    return onComponentReadyRunner;
  }

  public Description getDescription() {
    return hasHiltTestRule.get();
  }

  public void setBindValueCalled() {
    // Some tests call bind without using @BindValue. b/128706854
    if (waitForBindValue) {
      bindValueCalled = true;
      tryToCreateComponent();
    }
  }

  /** For framework use only! This method should be called when a required module is installed. */
  public <T> void registerModule(Class<T> moduleClass, T module) {
    Preconditions.checkNotNull(moduleClass);
    Preconditions.checkState(
        requiredModules.contains(moduleClass),
        "Found unknown module class: %s",
        moduleClass.getName());
    Preconditions.checkState(
        // Some exempted tests register modules multiple times.
        !registeredModules.containsKey(moduleClass),
        "Module is already registered: %s",
        moduleClass.getName());

    registeredModules.put(moduleClass, module);
    tryToCreateComponent();
  }

  /** For framework use only! This method should be called when creating the dagger component. */
  @SuppressWarnings("unchecked") // Matching types are enforced in #registerModule(Class<T>, T).
  public <T> T getRegisteredModule(Class<T> moduleClass) {
    Preconditions.checkState(
        registeredModules.containsKey(moduleClass),
        "Module is not registered: %s",
        moduleClass.getName());

    return (T) registeredModules.get(moduleClass);
  }

  private void tryToCreateComponent() {
    if (hasHiltTestRule()
        && registeredModules.keySet().containsAll(requiredModules)
        && bindValueReady()) {
      Preconditions.checkState(component.compareAndSet(null, componentSupplier.get()),
          "Tried to create the component more than once! "
              + "There is a race between registering the HiltTestRule and registering all test "
              + "modules. Make sure there is a happens-before edge between the two.");
      onComponentReadyRunner.runListeners();
    }
  }

  private boolean bindValueReady() {
    return !waitForBindValue || bindValueCalled;
  }

  private boolean hasHiltTestRule() {
    return hasHiltTestRule.get() != null;
  }
}
