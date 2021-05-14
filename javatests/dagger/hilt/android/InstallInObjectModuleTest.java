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

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.EntryPoints;
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
public class InstallInObjectModuleTest {

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void verifyObjectModule() {
    TestEntryPoint entryPoint = EntryPoints.get(getApplicationContext(), TestEntryPoint.class);
    assertThat(entryPoint.getInteger()).isEqualTo(1);
    assertThat(entryPoint.getDouble()).isEqualTo(2.0);
    assertThat(entryPoint.getBoolean()).isTrue();
  }
}
