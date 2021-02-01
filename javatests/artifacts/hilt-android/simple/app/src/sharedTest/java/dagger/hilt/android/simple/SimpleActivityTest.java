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

package dagger.hilt.android.simple;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.simple.lib.ThingImpl;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.UninstallModules;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** A simple test using Hilt that can be run with instrumentation or Robolectric tests. */
@UninstallModules({
  UserNameModule.class,
  ModelModule.class
})
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public final class SimpleActivityTest {
  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @BindValue @UserName String fakeUserName = "FakeUser";
  @BindValue @Model String fakeModel = "FakeModel";

  @Inject @UserName String injectedUserName;
  @Inject @Model String injectedModel;

  @Test
  public void testInjectedUserName() throws Exception {
    assertThat(injectedUserName).isNull();
    rule.inject();
    assertThat(injectedUserName).isEqualTo("FakeUser");
  }

  @Test
  public void testInjectedModel() throws Exception {
    assertThat(injectedModel).isNull();
    rule.inject();
    assertThat(injectedModel).isEqualTo("FakeModel");
  }

  @Test
  public void testActivityInject() throws Exception {
    try (ActivityScenario<SimpleActivity> scenario =
        ActivityScenario.launch(SimpleActivity.class)) {
      onView(withId(R.id.greeting))
          .check(matches(withText("Hello, FakeUser! You are on build FakeModel.")));
    } catch (RuntimeException e) {
      // Just skip this test if the root view never becomes active.
      // This issue occurs sporadically in emulator tests and causes the test to be flaky.
      // It is likely caused by a race between our activity and a dialog or lock screen but
      // it's difficult to debug this since it only fails in CI builds.
      // TODO(b/176111885): Remove this once this bug is fixed.
      if (!e.getMessage().startsWith("Waited for the root of the view hierarchy")) {
        throw e;
      }
    }
  }

  @Test
  public void verifyMainActivity() {
    try (ActivityScenario<SimpleActivity> scenario =
        ActivityScenario.launch(SimpleActivity.class)) {
      scenario.onActivity(
          activity -> {
            assertThat(activity.getClass().getSuperclass().getSimpleName())
              .isEqualTo("Hilt_SimpleActivity");
          }
      );
    }
  }

  @Test
  public void verifyThing() {
    try (ActivityScenario<SimpleActivity> scenario =
        ActivityScenario.launch(SimpleActivity.class)) {
      scenario.onActivity(activity -> assertThat(activity.thing).isInstanceOf(ThingImpl.class));
    }
  }
}
