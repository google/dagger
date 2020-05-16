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

package dagger.hilt.android.example.gradle.simple;

import static androidx.lifecycle.Lifecycle.State.RESUMED;
import static com.google.common.truth.Truth.assertThat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests that {@link ActivityScenarioRule} works with Hilt tests. */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public final class ActivityScenarioRuleTest {
  private static final String STR_VALUE = "STR_VALUE";

  /** An activity to test injection. */
  @AndroidEntryPoint
  public static final class TestActivity extends AppCompatActivity {
    @Inject String str;
  }

  @Rule(order = 0) public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  @Rule(order = 1)
  public ActivityScenarioRule<TestActivity> scenarioRule =
      new ActivityScenarioRule<>(TestActivity.class);

  @BindValue String str = STR_VALUE;

  @Test
  public void testState() {
    assertThat(scenarioRule.getScenario().getState()).isEqualTo(RESUMED);
  }

  @Test
  public void testInjection() {
    scenarioRule
        .getScenario()
        .onActivity(activity -> assertThat(activity.str).isEqualTo(STR_VALUE));
  }
}
