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
import dagger.Module;
import dagger.Provides;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.GenerateComponents;
import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ApplicationComponent;
import dagger.hilt.android.testing.AndroidRobolectricEntryPoint;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltRobolectricTestRule;
import dagger.hilt.android.testing.UninstallModules;
import javax.inject.Inject;
import javax.inject.Named;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** A simple test using Hilt. */
@UninstallModules(ModelModule.class)
@GenerateComponents
@AndroidRobolectricEntryPoint
@RunWith(RobolectricTestRunner.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = SettingsActivityTest_Application.class)
public final class SettingsActivityTest {
  private static final String FAKE_MODEL = "FakeModel";
  private static final int TEST_VALUE = 11;

  private static final String BIND_VALUE_STRING = "BIND_VALUE_STRING";
  private static final String TEST_QUALIFIER = "TEST_QUALIFIER";

  @Module
  @InstallIn(ApplicationComponent.class)
  interface TestModule {
    @Provides
    @Model
    static String provideFakeModel() {
      return FAKE_MODEL;
    }

    @Provides
    static int provideInt() {
      return TEST_VALUE;
    }
  }

  @EntryPoint
  @InstallIn(ApplicationComponent.class)
  interface BindValueEntryPoint {
    @Named(TEST_QUALIFIER)
    String bindValueString();
  }

  @Rule public HiltRobolectricTestRule rule = new HiltRobolectricTestRule(this);

  @Inject Integer intValue;

  @BindValue
  @Named(TEST_QUALIFIER)
  String bindValueString = BIND_VALUE_STRING;

  @Test
  public void testInject() throws Exception {
    assertThat(intValue).isNull();

    SettingsActivityTest_Application.get().inject(this);

    assertThat(intValue).isNotNull();
    assertThat(intValue).isEqualTo(TEST_VALUE);
  }

  @Test
  public void testActivityInject() throws Exception {
    SettingsActivity activity = Robolectric.setupActivity(SettingsActivity.class);
    assertThat(activity.greeter.greet())
        .isEqualTo("ProdUser, you are on build FakeModel.");
  }

  @Test
  public void testBindValueIsMutable() throws Exception {
    bindValueString = "newValue";
    assertThat(getBinding()).isEqualTo("newValue");
  }

  @Test
  public void testBindValueFieldIsProvided() throws Exception {
    assertThat(bindValueString).isEqualTo(BIND_VALUE_STRING);
    assertThat(getBinding()).isEqualTo(BIND_VALUE_STRING);
  }

  @Test
  public void testSuperClassTransformation() {
    SimpleActivity simpleActivity = Robolectric.setupActivity(SimpleActivity.class);
    assertThat(simpleActivity.getClass().getSuperclass().getSimpleName())
        .isEqualTo("Hilt_SimpleActivity");
  }

  private static String getBinding() {
    return EntryPoints.get(SettingsActivityTest_Application.get(), BindValueEntryPoint.class)
        .bindValueString();
  }
}
