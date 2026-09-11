/*
 * Copyright (C) 2026 The Dagger Authors.
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

import static androidx.lifecycle.Lifecycle.State.RESUMED;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ActivityComponent;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltComponentActivity;
import javax.inject.Named;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests the reusable Hilt test activity from hilt-android-testing-manifest. */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public final class HiltComponentActivityTest {
  private static final String ACTIVITY_QUALIFIER = "HILT_COMPONENT_ACTIVITY";
  private static final String ACTIVITY_VALUE = "HiltComponentActivityTest_ActivityValue";

  @Module
  @InstallIn(ActivityComponent.class)
  interface TestActivityModule {
    @Provides
    @Named(ACTIVITY_QUALIFIER)
    static String provideString() {
      return ACTIVITY_VALUE;
    }
  }

  @EntryPoint
  @InstallIn(ActivityComponent.class)
  interface TestActivityEntryPoint {
    @Named(ACTIVITY_QUALIFIER)
    String activityValue();
  }

  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void hiltComponentActivity_canBeLaunchedFromManifestArtifact() {
    rule.inject();

    try (ActivityScenario<HiltComponentActivity> scenario =
        ActivityScenario.launch(HiltComponentActivity.class)) {
      assertThat(scenario.getState()).isEqualTo(RESUMED);
    }
  }

  @Test
  public void hiltComponentActivity_hasActivityComponent() {
    rule.inject();

    try (ActivityScenario<HiltComponentActivity> scenario =
        ActivityScenario.launch(HiltComponentActivity.class)) {
      scenario.onActivity(
          activity ->
              assertThat(EntryPoints.get(activity, TestActivityEntryPoint.class).activityValue())
                  .isEqualTo(ACTIVITY_VALUE));
    }
  }
}
