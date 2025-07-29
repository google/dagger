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
import dagger.hilt.internal.TestSingletonComponentManager;
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
    implements TestSingletonComponentManager, OnComponentReadyRunnerHolder {

  private final Object earlyComponentLock = new Object();
  private volatile Object earlyComponent = null;

  private final Object testComponentDataLock = new Object();
  private volatile TestComponentData testComponentData;

  private final Application application;
  private final AtomicReference<Object> component = new AtomicReference<>();
  private final AtomicReference<Description> hasHiltTestRule = new AtomicReference<>();
  // TODO(bcorso): Consider using a lock here rather than ConcurrentHashMap to avoid b/37042460.
  private final Map<Class<?>, Object> registeredModules = new ConcurrentHashMap<>();
  private final AtomicReference<Boolean> autoAddModuleEnabled = new AtomicReference<>();
  private final AtomicReference<DelayedComponentState> delayedComponentState =
      new AtomicReference<>(DelayedComponentState.NOT_DELAYED);
  private volatile Object testInstance;
  private volatile OnComponentReadyRunner onComponentReadyRunner = new OnComponentReadyRunner();

  /**
   * Represents the state of Component readiness. There are two valid transition sequences.
   *
   * <ul>
   *   <li>Typical test (no HiltAndroidRule#delayComponentReady): {@code NOT_DELAYED -> INJECTED}
   *   <li>Using HiltAndroidRule#delayComponentReady: {@code NOT_DELAYED -> COMPONENT_DELAYED ->
   *       COMPONENT_READY -> INJECTED}
   * </ul>
   */
  private enum DelayedComponentState {
    // Valid transitions: COMPONENT_DELAYED, INJECTED
    NOT_DELAYED,
    // Valid transitions: COMPONENT_READY
    COMPONENT_DELAYED,
    // Valid transitions: INJECTED
    COMPONENT_READY,
    // Terminal state
    INJECTED
  }

  public TestApplicationComponentManager(Application application) {
    this.application = application;
  }

  @Override
  public Object earlySingletonComponent() {
    if (earlyComponent == null) {
      synchronized (earlyComponentLock) {
        if (earlyComponent == null) {
          earlyComponent = EarlySingletonComponentCreator.createComponent(application);
        }
      }
    }
    return earlyComponent;
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
        testComponentData == null,
        "The testComponentData instance cannot be set before Hilt's test rule has run.");
    Preconditions.checkState(
        registeredModules.isEmpty(),
        "The Hilt registered modules cannot be set before Hilt's test rule has run.");
    Preconditions.checkState(
        onComponentReadyRunner.isEmpty(),
        "The Hilt onComponentReadyRunner cannot add listeners before Hilt's test rule has run.");
    DelayedComponentState state = delayedComponentState.get();
    switch (state) {
      case NOT_DELAYED:
      case COMPONENT_DELAYED:
        // Expected
        break;
      case COMPONENT_READY:
        throw new IllegalStateException("Called componentReady before test execution started");
      case INJECTED:
        throw new IllegalStateException("Called inject before test execution started");
    }
  }

  void clearState() {
    component.set(null);
    hasHiltTestRule.set(null);
    testInstance = null;
    testComponentData = null;
    registeredModules.clear();
    autoAddModuleEnabled.set(null);
    delayedComponentState.set(DelayedComponentState.NOT_DELAYED);
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
        testComponentData().daggerRequiredModules().contains(moduleClass),
        "Found unknown module class: %s",
        moduleClass.getName());
    if (requiredModules().contains(moduleClass)) {
      Preconditions.checkState(
          // Some exempted tests register modules multiple times.
          !registeredModules.containsKey(moduleClass),
          "Module is already registered: %s",
          moduleClass.getName());

      registeredModules.put(moduleClass, module);
      tryToCreateComponent();
    }
  }

  void delayComponentReady() {
    switch (delayedComponentState.getAndSet(DelayedComponentState.COMPONENT_DELAYED)) {
      case NOT_DELAYED:
        // Expected
        break;
      case COMPONENT_DELAYED:
        throw new IllegalStateException("Called delayComponentReady() twice");
      case COMPONENT_READY:
        throw new IllegalStateException("Called delayComponentReady() after componentReady()");
      case INJECTED:
        throw new IllegalStateException("Called delayComponentReady() after inject()");
    }
  }

  void componentReady() {
    switch (delayedComponentState.getAndSet(DelayedComponentState.COMPONENT_READY)) {
      case NOT_DELAYED:
        throw new IllegalStateException(
            "Called componentReady(), even though delayComponentReady() was not used.");
      case COMPONENT_DELAYED:
        // Expected
        break;
      case COMPONENT_READY:
        throw new IllegalStateException("Called componentReady() multiple times");
      case INJECTED:
        throw new IllegalStateException("Called componentReady() after inject()");
    }
    tryToCreateComponent();
  }

  void inject() {
    switch (delayedComponentState.getAndSet(DelayedComponentState.INJECTED)) {
      case NOT_DELAYED:
      case COMPONENT_READY:
        // Expected
        break;
      case COMPONENT_DELAYED:
        throw new IllegalStateException("Called inject() before calling componentReady()");
      case INJECTED:
        throw new IllegalStateException("Called inject() multiple times");
    }
    Preconditions.checkNotNull(testInstance);
    testInjector().injectTest(testInstance);
  }

  void verifyDelayedComponentWasMadeReady() {
    Preconditions.checkState(
        delayedComponentState.get() != DelayedComponentState.COMPONENT_DELAYED,
        "Used delayComponentReady(), but never called componentReady()");
  }

  private void tryToCreateComponent() {
    if (hasHiltTestRule()
        && registeredModules.keySet().containsAll(requiredModules())
        && bindValueReady()
        && delayedComponentReady()) {
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
        autoAddModuleEnabled.compareAndSet(null, autoAddModule),
        "autoAddModuleEnabled is already set!");
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
    if (testComponentData == null) {
      synchronized (testComponentDataLock) {
        if (testComponentData == null) {
          testComponentData = TestComponentDataSupplier.get(testClass());
        }
      }
    }
    return testComponentData;
  }

  private Class<?> testClass() {
    Preconditions.checkState(hasHiltTestRule(), "Test must have a HiltAndroidRule.");
    return hasHiltTestRule.get().getTestClass();
  }

  private boolean bindValueReady() {
    return !waitForBindValue() || testInstance != null;
  }

  private boolean delayedComponentReady() {
    return delayedComponentState.get() != DelayedComponentState.COMPONENT_DELAYED;
  }

  private boolean hasHiltTestRule() {
    return hasHiltTestRule.get() != null;
  }
}
