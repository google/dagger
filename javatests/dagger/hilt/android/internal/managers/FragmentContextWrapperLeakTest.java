/*
 * Copyright (C) 2021 The Dagger Authors.
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

package dagger.hilt.android.internal.managers;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.os.Build;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.internal.managers.ViewComponentManager.FragmentContextWrapper;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public final class FragmentContextWrapperLeakTest {
  /** An activity to test injection. */
  @AndroidEntryPoint(FragmentActivity.class)
  public static final class TestActivity extends Hilt_FragmentContextWrapperLeakTest_TestActivity {}

  /** A fragment to test injection. */
  @AndroidEntryPoint(Fragment.class)
  public static final class TestFragment extends Hilt_FragmentContextWrapperLeakTest_TestFragment {}

  @Rule public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  @Test
  public void testFragmentContextWrapperDoesNotLeakFragment() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      TestFragment fragment = new TestFragment();
      scenario.onActivity(
          activity ->
              activity
                  .getSupportFragmentManager()
                  .beginTransaction()
                  .add(fragment, "TestFragment")
                  .commitNow());

      FragmentContextWrapper fragmentContextWrapper =
          (FragmentContextWrapper) fragment.getContext();
      assertThat(fragmentContextWrapper.getFragment()).isEqualTo(fragment);
      scenario.moveToState(Lifecycle.State.DESTROYED);
      NullPointerException exception =
          assertThrows(NullPointerException.class, fragmentContextWrapper::getFragment);
      assertThat(exception).hasMessageThat().contains("The fragment has already been destroyed");
    }
  }
}
