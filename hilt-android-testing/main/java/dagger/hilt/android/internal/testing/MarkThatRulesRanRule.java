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

import static dagger.hilt.internal.Preconditions.checkNotNull;
import static dagger.hilt.internal.Preconditions.checkState;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import dagger.hilt.android.internal.Contexts;
import dagger.hilt.internal.GeneratedComponentManager;
import java.lang.annotation.Annotation;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A Junit {@code TestRule} that's installed in all Hilt tests.
 *
 * <p>This rule enforces that a Hilt TestRule has run. The Dagger component will not be created
 * without this test rule.
 */
public final class MarkThatRulesRanRule implements TestRule {
  private static final String HILT_ANDROID_APP = "dagger.hilt.android.HiltAndroidApp";
  private static final String HILT_ANDROID_TEST = "dagger.hilt.android.testing.HiltAndroidTest";

  private final Application application = Contexts.getApplication(
      ApplicationProvider.getApplicationContext());
  private final Object testInstance;
  private final boolean autoAddModule;

  private final AtomicBoolean started = new AtomicBoolean(false);

  public MarkThatRulesRanRule(Object testInstance) {
    this.autoAddModule = true;
    this.testInstance = checkNotNull(testInstance);
    checkState(
        hasAnnotation(testInstance, HILT_ANDROID_TEST),
        "Expected %s to be annotated with @HiltAndroidTest.",
        testInstance.getClass().getName());
    checkState(
        application instanceof GeneratedComponentManager,
        "Hilt test, %s, must use a Hilt test application but found %s. To fix, configure the test "
            + "to use HiltTestApplication or a custom Hilt test application generated with "
            + "@CustomTestApplication.",
        testInstance.getClass().getName(),
        application.getClass().getName());
    checkState(
        !hasAnnotation(application, HILT_ANDROID_APP),
        "Hilt test, %s, cannot use a @HiltAndroidApp application but found %s. To fix, configure "
            + "the test to use HiltTestApplication or a custom Hilt test application generated "
            +  "with @CustomTestApplication.",
        testInstance.getClass().getName(),
        application.getClass().getName());
  }

  public void delayComponentReady() {
    checkState(!started.get(), "Called delayComponentReady after test execution started");
    getTestApplicationComponentManager().delayComponentReady();
  }

  public void componentReady() {
    checkState(started.get(), "Called componentReady before test execution started");
    getTestApplicationComponentManager().componentReady();
  }

  public void inject() {
    getTestApplicationComponentManager().inject();
  }

  @Override
  public Statement apply(final Statement base, Description description) {
    started.set(true);
    checkState(
        description.getTestClass().isInstance(testInstance),
        "HiltAndroidRule was constructed with an argument that was not an instance of the test"
            + " class");
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {

        TestApplicationComponentManager componentManager = getTestApplicationComponentManager();
        try {
          // This check is required to check that state hasn't been set before this rule runs. This
          // prevents cases like setting state in Application.onCreate for Gradle emulator tests
          // that will get cleared after running the first test case.
          componentManager.checkStateIsCleared();
          componentManager.setAutoAddModule(autoAddModule);
          if (testInstance != null) {
            componentManager.setTestInstance(testInstance);
          }
          componentManager.setHasHiltTestRule(description);
          base.evaluate();
          componentManager.verifyDelayedComponentWasMadeReady();
        } finally {
          componentManager.clearState();
        }
      }
    };
  }

  private TestApplicationComponentManager getTestApplicationComponentManager() {
    checkState(
        application instanceof TestApplicationComponentManagerHolder,
        "The application is not an instance of TestApplicationComponentManagerHolder: %s",
        application);
    Object componentManager =
        ((TestApplicationComponentManagerHolder) application).componentManager();
    checkState(
        componentManager instanceof TestApplicationComponentManager,
        "Expected TestApplicationComponentManagerHolder to return an instance of"
            + "TestApplicationComponentManager");
    return (TestApplicationComponentManager) componentManager;
  }

  private static boolean hasAnnotation(Object obj, String annotationName) {
    for (Annotation annotation : obj.getClass().getAnnotations()) {
      if (annotation.annotationType().getName().contentEquals(annotationName)) {
        return true;
      }
    }
    return false;
  }
}
