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

import android.app.Application;
import android.content.Context;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.testing.CustomTestApplication;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Tests {@link dagger.hilt.android.testing.CustomTestApplication}. */
@CustomTestApplication(CustomTestApplicationTest.BaseApplication.class)
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = CustomTestApplicationTest_Application.class)
public final class CustomTestApplicationTest {
  static class BaseApplication extends Application {}

  static class OtherBaseApplication extends Application {}

  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void testApplicationBaseClass() throws Exception {
    assertThat((Context) getApplicationContext()).isInstanceOf(BaseApplication.class);
  }

  @CustomTestApplication(OtherBaseApplication.class)
  public static class Other {}

  @Test
  @Config(application = CustomTestApplicationTest_Other_Application.class)
  public void testOtherApplicationBaseClass() throws Exception {
    assertThat((Context) getApplicationContext()).isInstanceOf(OtherBaseApplication.class);
  }
}
