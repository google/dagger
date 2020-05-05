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

import android.app.Application;
import dagger.hilt.android.testing.OnComponentReadyRunner;
import dagger.hilt.android.testing.OnComponentReadyRunner.OnComponentReadyRunnerHolder;
import dagger.hilt.internal.GeneratedComponentManager;
import dagger.hilt.internal.Preconditions;
import java.util.HashSet;
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
    implements GeneratedComponentManager<Object>, OnComponentReadyRunnerHolder {

  private final Application application;
  private final TestComponentSupplier componentSupplier;

  private final AtomicReference<Object> component = new AtomicReference<>();
  private final AtomicReference<Description> hasHiltTestRule = new AtomicReference<>();
  private final Map<Class<?>, Object> registeredModules = new ConcurrentHashMap<>();
  private volatile Object testInstance;
  private volatile OnComponentReadyRunner onComponentReadyRunner = new OnComponentReadyRunner();

  public TestApplicationComponentManager(
      Application application, TestComponentSupplier componentSupplier) {
    this.application = application;
    this.componentSupplier = componentSupplier;
  }

  @Override
  public Object generatedComponent() {
    if (component.get() == null) {
      Preconditions.checkState(
          hasHiltTestRule(),
          "The component was not created. Check that you have added the HiltAndroidRule.");
      if (!registeredModules.keySet().containsAll(requiredModules())) {
        Set<Class<?>> difference = new HashSet<>(requiredModules());
        difference.removeAll(registeredModules.keySet());
        throw new IllegalStateException(
            "The component was not created. Check that you have "
                + "registered all test modules:\n\tUnregistered: "
                + difference);
      }
      Preconditions.checkState(
          bindValueReady(), "The test instance has not been set. Did you forget to call #bind()?");
      throw new IllegalStateException(
          "The component has not been created. "
              + "Check that you have called #inject()? Otherwise, "
              + "there is a race between injection and component creation. Make sure there is a "
              + "happens-before edge between the HiltAndroidRule/registering"
              + " all test modules and the first injection.");
    }
    return component.get();
  }

  @Override
  public OnComponentReadyRunner getOnComponentReadyRunner() {
    return onComponentReadyRunner;
  }

  /** For framework use only! This flag must be set before component creation. */
  void setHasHiltTestRule(Description description) {
    Preconditions.checkState(
        // Some exempted tests set the test rule multiple times. Use CAS to avoid setting twice.
        hasHiltTestRule.compareAndSet(null, description),
        "The hasHiltTestRule flag has already been set!");
    tryToCreateComponent();
  }

  void checkStateIsCleared() {
    Preconditions.checkState(
        component.get() == null,
        "The Hilt component cannot be set before Hilt's test rule has run.");
    Preconditions.checkState(
        hasHiltTestRule.get() == null,
        "The Hilt test rule cannot be set before Hilt's test rule has run.");
    Preconditions.checkState(
        testInstance == null,
        "The Hilt BindValue instance cannot be set before Hilt's test rule has run.");
    Preconditions.checkState(
        registeredModules.isEmpty(),
        "The Hilt registered modules cannot be set before Hilt's test rule has run.");
    Preconditions.checkState(
        onComponentReadyRunner.isEmpty(),
        "The Hilt onComponentReadyRunner cannot add listeners before Hilt's test rule has run.");
  }

  void clearState() {
    Preconditions.checkState(
        hasHiltTestRule(), "Cannot reset state if the test rule has not been set.");

    component.set(null);
    hasHiltTestRule.set(null);
    testInstance = null;
    registeredModules.clear();
    onComponentReadyRunner = new OnComponentReadyRunner();
  }

  public Description getDescription() {
    return hasHiltTestRule.get();
  }

  void setTestInstance(Object testInstance) {
    Preconditions.checkNotNull(testInstance);
    Preconditions.checkState(
        this.testInstance == null || this.testInstance == testInstance,
        "Cannot call setTestInstance from two different test instances.");

    if (this.testInstance == null) {
      this.testInstance = testInstance;
      // Some tests call bind without using @BindValue. b/128706854
      if (waitForBindValue()) {
        tryToCreateComponent();
      }
    }
  }

  public Object getTestInstance() {
    Preconditions.checkState(
        testInstance != null,
        "The test instance has not been set.");
    return testInstance;
  }

  /** For framework use only! This method should be called when a required module is installed. */
  public <T> void registerModule(Class<T> moduleClass, T module) {
    Preconditions.checkNotNull(moduleClass);
    Preconditions.checkState(
        requiredModules().contains(moduleClass),
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

  void inject() {
    Preconditions.checkNotNull(testInstance);
    testInjector().injectTest(testInstance);
  }

  private void tryToCreateComponent() {
    if (hasHiltTestRule()
        && registeredModules.keySet().containsAll(requiredModules())
        && bindValueReady()) {
      Preconditions.checkState(
          component.compareAndSet(null, componentSupplier().get(registeredModules)),
          "Tried to create the component more than once! "
              + "There is a race between registering the HiltAndroidRule and registering"
              + " all test modules. Make sure there is a happens-before edge between the two.");
      onComponentReadyRunner.setComponentManager((GeneratedComponentManager) application);
    }
  }

  private Set<Class<?>> requiredModules() {
    return componentSupplier.requiredModules().get(testClass());
  }

  private boolean waitForBindValue() {
    return componentSupplier.waitForBindValue().get(testClass());
  }

  private TestInjector<Object> testInjector() {
    return componentSupplier.testInjectors().get(testClass());
  }

  private TestComponentSupplier.ComponentSupplier componentSupplier() {
    return componentSupplier.get().get(testClass());
  }

  private Class<?> testClass() {
    Preconditions.checkState(
        hasHiltTestRule(),
        "Test must have an HiltAndroidRule.");
    return hasHiltTestRule.get().getTestClass();
  }

  private boolean bindValueReady() {
    return !waitForBindValue() || testInstance != null;
  }

  private boolean hasHiltTestRule() {
    return hasHiltTestRule.get() != null;
  }
}
