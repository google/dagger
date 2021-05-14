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

package dagger.hilt.android.testing.testinstallin;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.Bar;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.Foo;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.GlobalBarModule;
import dagger.hilt.android.testing.testinstallin.TestInstallInModules.GlobalFooTestModule;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

// Test that Foo uses the global @TestInstallIn module and Bar uses the global @InstallIn module.
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
public final class TestInstallInFooTest {

  @Rule public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  @Inject Foo foo;
  @Inject Bar bar;

  @Test
  public void testFoo() {
    hiltRule.inject();
    assertThat(foo.moduleClass).isEqualTo(GlobalFooTestModule.class);
  }

  @Test
  public void testBar() {
    hiltRule.inject();
    assertThat(bar.moduleClass).isEqualTo(GlobalBarModule.class);
  }
}
