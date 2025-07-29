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

package dagger.android;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.android.internal.AndroidInjectionKeys;
import java.net.URL;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P)
public class AndroidProguardTest {

  @Test
  public void checkLegacyProguardRules() {
    URL resUrl =
        AndroidInjectionKeys.class
            .getClassLoader()
            .getResource("META-INF/proguard/dagger-android.pro");
    assertThat(resUrl).isNotNull();
  }

  // The com.android.tools files are only used outside Google, in Gradle projects.
  @Test
  public void checkProguardRules() {
    URL resUrl =
        AndroidInjectionKeys.class
            .getClassLoader()
            .getResource("META-INF/com.android.tools/proguard/dagger-android.pro");
    assertThat(resUrl).isNotNull();
  }

  @Test
  public void checkR8Rules() {
    URL resUrl =
        AndroidInjectionKeys.class
            .getClassLoader()
            .getResource("META-INF/com.android.tools/r8/dagger-android.pro");
    assertThat(resUrl).isNotNull();
  }
}
