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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EarlyEntryPoints;
import dagger.hilt.android.simple.EarlyEntryPointWithBindValueObjects.Foo;
import dagger.hilt.android.simple.EarlyEntryPointWithBindValueObjects.FooEarlyEntryPoint;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import dagger.hilt.components.SingletonComponent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public final class EarlyEntryPointWithBindValueTest {
  @EntryPoint
  @InstallIn(SingletonComponent.class)
  interface FooEntryPoint {
    Foo foo();
  }

  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @BindValue String bindString = "TestValue";

  @Test
  public void testBindValue() throws Exception {
    rule.inject();
    assertThat(bindString).isNotNull();
    assertThat(bindString).isEqualTo("TestValue");
  }

  @Test
  public void testEarlyEntryPoint() throws Exception {
    Context appContext = getApplicationContext();

    // Assert that all scoped Foo instances from EarlyEntryPoint are equal
    Foo earlyFoo1 = EarlyEntryPoints.get(appContext, FooEarlyEntryPoint.class).getEarlyFoo();
    Foo earlyFoo2 = EarlyEntryPoints.get(appContext, FooEarlyEntryPoint.class).getEarlyFoo();
    assertThat(earlyFoo1).isNotNull();
    assertThat(earlyFoo2).isNotNull();
    assertThat(earlyFoo1).isEqualTo(earlyFoo2);

    // Assert that all scoped Foo instances from EntryPoint are equal
    Foo foo1 = EntryPoints.get(appContext, FooEntryPoint.class).foo();
    Foo foo2 = EntryPoints.get(appContext, FooEntryPoint.class).foo();
    assertThat(foo1).isNotNull();
    assertThat(foo2).isNotNull();
    assertThat(foo1).isEqualTo(foo2);

    // Assert that scoped Foo instances from EarlyEntryPoint and EntryPoint are not equal
    assertThat(foo1).isNotEqualTo(earlyFoo1);
  }
}
