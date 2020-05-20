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
import java.lang.reflect.InvocationTargetException;
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

  // This is a generated class that we always generate in a known location.
  private static final String TEST_COMPONENT_DATA_SUPPLIER_IMPL =
      "dagger.hilt.android.internal.testing.TestComponentDataSupplierImpl";

  private final Application application;
  private final Map<Class<?>, TestComponentData> testComponentDataSupplier;

  private final AtomicReference<Object> component = new AtomicReference<>();
  private final AtomicReference<Description> hasHiltTestRule = new AtomicReference<>();
  private final Map<Class<?>, Object> registeredModules = new ConcurrentHashMap<>();
  private final AtomicReference<Boolean> autoAddModuleEnabled = new AtomicReference<>();
  private volatile Object testInstance;
  private volatile OnComponentReadyRunner onComponentReadyRunner = new OnComponentReadyRunner();

  public TestApplicationComponentManager(Application application) {
    this.application = application;
    try {
      this.testComponentDataSupplier =
          Class.forName(TEST_COMPONENT_DATA_SUPPLIER_IMPL)
              .asSubclass(TestComponentDataSupplier.class)
              .getDeclaredConstructor()
              .newInstance()
              .get();
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InstantiationException
        | InvocationTargetException e) {
      throw new RuntimeException(
          "Hilt classes generated from @HiltAndroidTest are missing. Check that you have annotated "
              + "your test class with @HiltAndroidTest and that the processor is running over your "
              + "test",
          e);
    }
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
        autoAddModuleEnabled.get() == null,
        "The Hilt autoAddModuleEnabled cannot be set before Hilt's test rule has run.");
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
    autoAddModuleEnabled.set(null);
    onComponentReadyRunner = new OnComponentReadyRunner();
  }

  public Description getDescription() {
    return hasHiltTestRule.get();
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
          autoAddModuleEnabled.get() !=  null,
          "Component cannot be created before autoAddModuleEnabled is set.");
      Preconditions.checkState(
          component.compareAndSet(
              null,
              componentSupplier().get(registeredModules, testInstance, autoAddModuleEnabled.get())),
          "Tried to create the component more than once! "
              + "There is a race between registering the HiltAndroidRule and registering"
              + " all test modules. Make sure there is a happens-before edge between the two.");
      onComponentReadyRunner.setComponentManager((GeneratedComponentManager) application);
    }
  }

  void setTestInstance(Object testInstance) {
    Preconditions.checkNotNull(testInstance);
    Preconditions.checkState(this.testInstance == null, "The test instance was already set!");
    this.testInstance = testInstance;
  }

  void setAutoAddModule(boolean autoAddModule) {
    Preconditions.checkState(
        autoAddModuleEnabled.get() == null, "autoAddModuleEnabled is already set!");
    autoAddModuleEnabled.set(autoAddModule);
  }

  private Set<Class<?>> requiredModules() {
    return autoAddModuleEnabled.get()
        ? testComponentData().hiltRequiredModules()
        : testComponentData().daggerRequiredModules();
  }

  private boolean waitForBindValue() {
    return testComponentData().waitForBindValue();
  }

  private TestInjector<Object> testInjector() {
    return testComponentData().testInjector();
  }

  private TestComponentData.ComponentSupplier componentSupplier() {
    return testComponentData().componentSupplier();
  }

  private TestComponentData testComponentData() {
    return testComponentDataSupplier.get(testClass());
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
