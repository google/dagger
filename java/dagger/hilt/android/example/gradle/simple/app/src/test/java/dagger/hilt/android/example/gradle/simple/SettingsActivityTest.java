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

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.GenerateComponents;
import dagger.hilt.android.testing.AndroidRobolectricEntryPoint;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltRobolectricTestRule;
import dagger.hilt.android.testing.UninstallModules;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** A simple test using Hilt. */
@UninstallModules(ModelModule.class)
@GenerateComponents
@AndroidRobolectricEntryPoint
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = SettingsActivityTest_Application.class)
public final class SettingsActivityTest {
  @Rule public HiltRobolectricTestRule rule = new HiltRobolectricTestRule(this);

  @BindValue @Model String fakeModel = "FakeModel";

  @Inject @Model String injectedModel;

  @Test
  public void testInjectedModel() throws Exception {
    assertThat(injectedModel).isNull();
    SettingsActivityTest_Application.get().inject(this);
    assertThat(injectedModel).isEqualTo("FakeModel");
  }

  @Test
  public void testActivityInject() throws Exception {
    try (ActivityScenario<SettingsActivity> scenario =
        ActivityScenario.launch(SettingsActivity.class)) {
      scenario.onActivity(
          activity ->
              assertThat(activity.greeter.greet())
                  .isEqualTo("ProdUser, you are on build FakeModel."));
    }
  }
}
