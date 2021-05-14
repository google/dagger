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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.content.Intent;
import android.os.Build;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Tests that the Hilt view model factory doesn't cause errors with non-Hilt view models. This was
 * a problem at one point because if the non-Hilt view model is accessed before saved state
 * restoration happens, even if the non-Hilt view model doesn't use saved state, it can cause an
 * error because the default Hilt view model factory does use saved state. This test ensures we
 * still allow non-Hilt view models without saved state to be used before saved state is restored.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public class ActivityInjectedViewModelTest {

  private static final String DATA_KEY = "TEST_KEY";

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void memberInjectedViewModelWithSavedState() {
    final Intent intent = new Intent(getApplicationContext(), TestActivity.class);
    intent.putExtra(DATA_KEY, "test data");
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(intent)) {
      scenario.onActivity(activity -> assertThat(activity.myViewModel).isNotNull());
    }
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static final class TestActivity extends Hilt_ActivityInjectedViewModelTest_TestActivity {
    @Inject MyViewModel myViewModel;
  }

  @Module
  @InstallIn(ActivityComponent.class)
  static final class MyViewModelModel {
    @Provides
    static MyViewModel provideModel(FragmentActivity activity) {
      return new ViewModelProvider(activity).get(MyViewModel.class);
    }
  }

  public static final class MyViewModel extends ViewModel {}
}
