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

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.EntryPoints;
import dagger.hilt.android.EarlyEntryPointHiltAndroidAppRuntimeClasses.EarlyFooEntryPoint;
import dagger.hilt.android.EarlyEntryPointHiltAndroidAppRuntimeClasses.Foo;
import dagger.hilt.android.EarlyEntryPointHiltAndroidAppRuntimeClasses.FooEntryPoint;
import dagger.hilt.android.EarlyEntryPointHiltAndroidAppRuntimeClasses.TestApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = TestApplication.class)
public final class EarlyEntryPointHiltAndroidAppRuntimeTest {
  // Tests that when using @HiltAndroidApp
  //   1) calling with the wrong parameters doesn't throw
  //   2) EarlyEntryPoints returns the same thing as EntryPoints.
  @Test
  public void testEntryPoints() throws Exception {
    Object generatedComponent = ((TestApplication) getApplicationContext()).generatedComponent();

    Foo foo1 = EntryPoints.get(getApplicationContext(), FooEntryPoint.class).foo();
    Foo foo2 = EntryPoints.get(getApplicationContext(), EarlyFooEntryPoint.class).foo();
    Foo foo3 = EntryPoints.get(generatedComponent, FooEntryPoint.class).foo();
    Foo foo4 = EntryPoints.get(generatedComponent, EarlyFooEntryPoint.class).foo();
    Foo foo5 = EarlyEntryPoints.get(getApplicationContext(), FooEntryPoint.class).foo();
    Foo foo6 = EarlyEntryPoints.get(getApplicationContext(), EarlyFooEntryPoint.class).foo();

    assertThat(foo1).isEqualTo(foo2);
    assertThat(foo1).isEqualTo(foo3);
    assertThat(foo1).isEqualTo(foo4);
    assertThat(foo1).isEqualTo(foo5);
    assertThat(foo1).isEqualTo(foo6);
  }
}
