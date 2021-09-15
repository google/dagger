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

package dagger.hilt.android;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.os.Build;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle.State;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.ActivityRetainedLifecycle.OnClearedListener;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public class ActivityRetainedClearedListenerTest {

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void onClearedInvoked() {
    final TestClearedListener callback = new TestClearedListener();
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> activity.activityRetainedLifecycle.addOnClearedListener(callback));
      assertThat(callback.onClearedInvoked).isEqualTo(0);

      // Recreate should not cause ViewModel to be cleared
      scenario.recreate();
      assertThat(callback.onClearedInvoked).isEqualTo(0);

      // Destroying activity (not due to recreate) should cause ViewModel to be cleared
      scenario.moveToState(State.DESTROYED);
      assertThat(callback.onClearedInvoked).isEqualTo(1);
    }
  }

  @Test
  public void addOnClearedListener_tooLate() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      // Grab the activity (leak it a bit) from the scenario and destroy it.
      AtomicReference<TestActivity> testActivity = new AtomicReference<>();
      scenario.onActivity(testActivity::set);
      scenario.moveToState(State.DESTROYED);

      try {
        TestClearedListener callback = new TestClearedListener();
        testActivity.get().activityRetainedLifecycle.addOnClearedListener(callback);
        fail("An exception should have been thrown.");
      } catch (IllegalStateException e) {
        assertThat(e)
            .hasMessageThat()
            .contains(
                "There was a race between the call to add/remove an OnClearedListener and "
                    + "onCleared(). This can happen when posting to the Main thread from a "
                    + "background thread, which is not supported.");
      }
    }
  }

  @Test
  public void removeOnClearedListener_tooLate() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      // Grab the activity (leak it a bit) from the scenario and destroy it.
      AtomicReference<TestActivity> testActivity = new AtomicReference<>();
      scenario.onActivity(testActivity::set);
      scenario.moveToState(State.DESTROYED);

      try {
        TestClearedListener callback = new TestClearedListener();
        testActivity.get().activityRetainedLifecycle.removeOnClearedListener(callback);
        fail("An exception should have been thrown.");
      } catch (IllegalStateException e) {
        assertThat(e)
            .hasMessageThat()
            .contains(
                "There was a race between the call to add/remove an OnClearedListener and "
                    + "onCleared(). This can happen when posting to the Main thread from a "
                    + "background thread, which is not supported.");
      }
    }
  }

  static class TestClearedListener implements OnClearedListener {
    int onClearedInvoked = 0;

    @Override
    public void onCleared() {
      onClearedInvoked++;
    }
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static final class TestActivity
      extends Hilt_ActivityRetainedClearedListenerTest_TestActivity {
    @Inject ActivityRetainedLifecycle activityRetainedLifecycle;
  }
}
