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

package dagger.hilt.android;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EarlyEntryPointCustomApplicationClasses.EarlyFooEntryPoint;
import dagger.hilt.android.EarlyEntryPointCustomApplicationClasses.Foo;
import dagger.hilt.android.testing.CustomTestApplication;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.components.SingletonComponent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@CustomTestApplication(EarlyEntryPointCustomApplicationTest.BaseApplication.class)
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(
    sdk = Build.VERSION_CODES.P,
    application = EarlyEntryPointCustomApplicationTest_Application.class)
public final class EarlyEntryPointCustomApplicationTest {
  @EntryPoint
  @InstallIn(SingletonComponent.class)
  interface FooEntryPoint {
    Foo foo();
  }

  public static class BaseApplication extends Application {
    Foo earlyFoo = null;
    IllegalStateException entryPointsException = null;

    @Override
    protected final void attachBaseContext(Context base) {
      // This is a regression test for https://github.com/google/dagger/issues/3356.
      // Test that EarlyEntryPoints works even before the base context is attached.
      earlyFoo = EarlyEntryPoints.get(this, EarlyFooEntryPoint.class).foo();
      super.attachBaseContext(base);
    }

    @Override
    public void onCreate() {
      super.onCreate();

      // Test that calling EntryPoints fails if called before the test instance is created.
      try {
        EntryPoints.get(this, FooEntryPoint.class);
      } catch (IllegalStateException e) {
        entryPointsException = e;
      }
    }
  }

  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void testEarlyEntryPointsPasses() throws Exception {
    BaseApplication baseApplication = (BaseApplication) getApplicationContext();
    assertThat(baseApplication.earlyFoo).isNotNull();
  }

  @Test
  public void testEntryPointsFails() throws Exception {
    BaseApplication baseApplication = (BaseApplication) getApplicationContext();
    assertThat(baseApplication.entryPointsException).isNotNull();
    assertThat(baseApplication.entryPointsException)
        .hasMessageThat()
        .contains("The component was not created.");
  }
}
