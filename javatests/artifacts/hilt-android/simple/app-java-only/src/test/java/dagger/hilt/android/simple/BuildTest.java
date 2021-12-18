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

package dagger.hilt.android.simple;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Regression test for https://github.com/google/dagger/issues/3119
 */
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = SimpleApplication.class)
public class BuildTest {
  @Test
  public void useAppContext() {
    assertThat((Object) ApplicationProvider.getApplicationContext())
        .isInstanceOf(SimpleApplication.class);
    SimpleApplication app = (SimpleApplication) ApplicationProvider.getApplicationContext();
    assertThat(app.str).isNotNull();
    assertThat(app.str).isEqualTo(app.getStringEntryPoint());
  }
}
