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

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
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
  private final Context context = ApplicationProvider.getApplicationContext();
  private final Object testInstance;

  public MarkThatRulesRanRule(Object testInstance) {
    this.testInstance = checkNotNull(testInstance);
  }

  public void inject() {
    getTestApplicationComponentManager().inject();
  }

  @Override
  public Statement apply(final Statement base, Description description) {
    checkState(
        description.getTestClass().isInstance(testInstance),
        "HiltAndroidRule was constructed with an argument that was not an instance of the test"
            + " class");
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {

        TestApplicationComponentManager componentManager = getTestApplicationComponentManager();
        // This check is required to check that state hasn't been set before this rule runs. This
        // prevents cases like setting state in Application.onCreate for Gradle emulator tests that
        // will get cleared after running the first test case.
        componentManager.checkStateIsCleared();
        componentManager.setHasHiltTestRule(description);
        if (testInstance != null) {
          componentManager.setTestInstance(testInstance);
        }
        base.evaluate();
        componentManager.clearState();
      }
    };
  }

  private TestApplicationComponentManager getTestApplicationComponentManager() {
    checkState(
        context instanceof TestApplicationComponentManagerHolder,
        "The context is not an instance of TestApplicationComponentManagerHolder: %s",
        context);
    Object componentManager = ((TestApplicationComponentManagerHolder) context).componentManager();
    checkState(
        componentManager instanceof TestApplicationComponentManager,
        "Expected TestApplicationComponentManagerHolder to return an instance of"
            + "TestApplicationComponentManager");
    return (TestApplicationComponentManager) componentManager;
  }
}
