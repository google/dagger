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

import static dagger.hilt.internal.Preconditions.checkNotNull;

import dagger.hilt.android.internal.testing.MarkThatRulesRanRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A {@link TestRule} for Hilt that can be used with JVM or Instrumentation tests.
 *
 * <p>This rule is required. The Dagger component will not be created without this test rule.
 */
public final class HiltAndroidRule implements TestRule {
  private final MarkThatRulesRanRule rule;

  /** Creates a new instance of the rules. Tests should pass {@code this}. */
  public HiltAndroidRule(Object testInstance) {
    this.rule = new MarkThatRulesRanRule(checkNotNull(testInstance));
  }

  @Override public Statement apply(Statement baseStatement, Description description) {
    return rule.apply(baseStatement, description);
  }

  /**
   * Completes Dagger injection. Must be called before accessing inject types. Must be called after
   * any non-static test module have been added. If {@link #delayComponentReady} was used, this must
   * be called after {@link #componentReady}.
   */
  public void inject() {
    rule.inject();
  }

  /**
   * Delays creating the component until {@link #componentReady} is called. This is only necessary
   * in the case that a dynamically bound value (e.g. configuring an @BindValue field in @Before
   * or @Test method) is requested before test case execution begins.
   *
   * <p>Examples of early binding requests include an Activity launched by a test rule, or an entry
   * points in a {@link OnComponentReadyRunner}.
   *
   * <p>If this method is called, {@link #componentReady} must be called before the test case
   * finishes.
   */
  public HiltAndroidRule delayComponentReady() {
    rule.delayComponentReady();
    return this;
  }

  /**
   * Completes Dagger component creation if {@link delayComponentReady} was used. Binds the current
   * value of {@link BindValue} fields. Normally this happens automatically. This method may only be
   * called if {@link delayComponentReady} was used to delay value binding.
   *
   * @return an instance of the test rule for chaining
   */
  public HiltAndroidRule componentReady() {
    rule.componentReady();
    return this;
  }
}
