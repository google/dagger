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

package dagger.hilt.android.testing;

import android.content.Context;
import com.google.common.base.Preconditions;
import dagger.hilt.android.internal.testing.TestApplicationComponentManager;
import dagger.hilt.android.internal.testing.TestApplicationComponentManagerHolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A Junit {@code TestRule} that's installed in all Hilt tests.
 *
 * <p>This rule enforces that a Hilt TestRule has run.
 * The Dagger component will not be created without this test rule.
 */
final class MarkThatRulesRanRule implements TestRule {
  private final Context context;

  MarkThatRulesRanRule(Context context) {
    this.context = context;
  }

  @Override
  public Statement apply(final Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        setHasHiltTestRule(description);
        base.evaluate();
      }
    };
  }

  private void setHasHiltTestRule(Description description) {
    Context applicationContext = context.getApplicationContext();
    if (applicationContext instanceof TestApplicationComponentManagerHolder) {
      Object componentManager =
          ((TestApplicationComponentManagerHolder) applicationContext).componentManager();
      Preconditions.checkState(componentManager instanceof TestApplicationComponentManager);
      ((TestApplicationComponentManager) componentManager).setHasHiltTestRule(description);
    }
  }
}
