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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.simple.BaseTestApplication.Foo;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests {@link dagger.hilt.android.testing.CustomTestApplication}. */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public final class CustomTestApplicationTest {
  @Rule public HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void testApplicationBaseClass() throws Exception {
    assertThat((Context) getApplicationContext()).isInstanceOf(BaseTestApplication.class);
  }

  @Test
  public void testEarlyEntryPoint() throws Exception {
    BaseTestApplication app = (BaseTestApplication) getApplicationContext();

    // Assert that all scoped Foo instances from EarlyEntryPoint are equal
    Foo earlyFoo = app.earlyFoo();
    Foo lazyEarlyFoo1 = app.lazyEarlyFoo();
    Foo lazyEarlyFoo2 = app.lazyEarlyFoo();
    assertThat(earlyFoo).isNotNull();
    assertThat(lazyEarlyFoo1).isNotNull();
    assertThat(lazyEarlyFoo2).isNotNull();
    assertThat(earlyFoo).isEqualTo(lazyEarlyFoo1);
    assertThat(earlyFoo).isEqualTo(lazyEarlyFoo2);

    // Assert that all scoped Foo instances from EntryPoint are equal
    Foo lazyFoo1 = app.lazyFoo();
    Foo lazyFoo2 = app.lazyFoo();
    assertThat(lazyFoo1).isNotNull();
    assertThat(lazyFoo2).isNotNull();
    assertThat(lazyFoo1).isEqualTo(lazyFoo2);

    // Assert that scoped Foo instances from EarlyEntryPoint and EntryPoint are not equal
    assertThat(earlyFoo).isNotEqualTo(lazyFoo1);
  }
}
