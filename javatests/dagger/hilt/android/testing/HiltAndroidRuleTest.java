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

package dagger.hilt.android.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.Application;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public final class HiltAndroidRuleTest {
  public static final class NonHiltTest {}

  @Test
  @Config(application = HiltTestApplication.class)
  public void testMissingHiltAndroidTest_fails() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> new HiltAndroidRule(new NonHiltTest()));
    assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              "Expected dagger.hilt.android.testing.HiltAndroidRuleTest$NonHiltTest to be "
                  + "annotated with @HiltAndroidTest.");

  }

  @Test
  @Config(application = Application.class)
  public void testNonHiltTestApplication_fails() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> new HiltAndroidRule(HiltAndroidRuleTest.this));
    assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              "Hilt test, dagger.hilt.android.testing.HiltAndroidRuleTest, must use a Hilt test "
                  + "application but found android.app.Application. To fix, configure the test to "
                  + "use HiltTestApplication or a custom Hilt test application generated with "
                  + "@CustomTestApplication.");

  }

  @Test
  @Config(application = HiltAndroidRuleTestApp.class)
  public void testHiltAndroidApplication_fails() throws Exception {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> new HiltAndroidRule(HiltAndroidRuleTest.this));
    assertThat(exception)
          .hasMessageThat()
          .isEqualTo(
              "Hilt test, dagger.hilt.android.testing.HiltAndroidRuleTest, cannot use a "
                  + "@HiltAndroidApp application but found "
                  + "dagger.hilt.android.testing.HiltAndroidRuleTestApp. To fix, configure the "
                  + "test to use HiltTestApplication or a custom Hilt test application generated "
                  + "with @CustomTestApplication.");

  }
}
