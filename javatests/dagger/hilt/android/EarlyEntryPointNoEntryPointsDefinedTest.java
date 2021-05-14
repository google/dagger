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
import static org.junit.Assert.assertThrows;

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.internal.testing.TestApplicationComponentManager;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

// The main purpose of this test is to check the error messages if EarlyEntryPoints is called
// without the EarlyComponent being generated (i.e. if no @EarlyEntryPoints are defined).
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public final class EarlyEntryPointNoEntryPointsDefinedTest {
  @EntryPoint
  @InstallIn(SingletonComponent.class)
  interface FooEntryPoint {
    Foo foo();
  }

  @Singleton
  public static class Foo {
    @Inject
    Foo() {}
  }

  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void testEarlyComponentDoesNotExist() throws Exception {
    HiltTestApplication app = (HiltTestApplication) getApplicationContext();
    TestApplicationComponentManager componentManager =
        (TestApplicationComponentManager) app.componentManager();

    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> componentManager.earlySingletonComponent());

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "The EarlyComponent was requested but does not exist. Check that you have "
                + "annotated your test class with @HiltAndroidTest and that the processor is "
                + "running over your test");
  }

  @Test
  public void testEarlyEntryPointsWrongEntryPointFails() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> EarlyEntryPoints.get(getApplicationContext(), FooEntryPoint.class));

    assertThat(exception)
        .hasMessageThat()
        .contains(
            "FooEntryPoint should be called with EntryPoints.get() rather than "
                + "EarlyEntryPoints.get()");
  }
}
