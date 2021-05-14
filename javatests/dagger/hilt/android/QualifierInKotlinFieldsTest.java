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

import android.os.Build;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public class QualifierInKotlinFieldsTest {

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void activityFactory() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            assertThat(activity.object.appContext).isNotNull();
            assertThat(activity.object.activityContext).isNotNull();
          });
    }
  }

  // This test activity injects a class that is defined in Kotlin because we want to test the
  // qualifiers in Kotlin fields / properties (generated getters and setter with backing field).
  // Ideally we would write this test in Kotlin, but there is no open fule for writting android
  // local tests in Kotlin.
  @AndroidEntryPoint(FragmentActivity.class)
  public static final class TestActivity extends Hilt_QualifierInKotlinFieldsTest_TestActivity {
    @Inject QualifierInFieldsClass object;
  }
}
