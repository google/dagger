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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.EntryPoint;
import dagger.hilt.EntryPoints;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Named;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
@Config(application = HiltTestApplication.class)
public final class BindValueTest {
  private static final String BIND_VALUE_STRING1 = "BIND_VALUE_STRING1";
  private static final String BIND_VALUE_STRING2 = "BIND_VALUE_STRING2";
  private static final String TEST_QUALIFIER1 = "TEST_QUALIFIER1";
  private static final String TEST_QUALIFIER2 = "TEST_QUALIFIER2";

  @EntryPoint
  @InstallIn(SingletonComponent.class)
  public interface BindValueEntryPoint {
    @Named(TEST_QUALIFIER1)
    String bindValueString1();

    @Named(TEST_QUALIFIER2)
    String bindValueString2();
  }

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @BindValue
  @Named(TEST_QUALIFIER1)
  String bindValueString1 = BIND_VALUE_STRING1;

  @BindValue
  @Named(TEST_QUALIFIER2)
  String bindValueString2 = BIND_VALUE_STRING2;

  @Test
  public void testBindValueFieldIsProvided() throws Exception {
    assertThat(bindValueString1).isEqualTo(BIND_VALUE_STRING1);
    assertThat(getBinding1()).isEqualTo(BIND_VALUE_STRING1);

    assertThat(bindValueString2).isEqualTo(BIND_VALUE_STRING2);
    assertThat(getBinding2()).isEqualTo(BIND_VALUE_STRING2);
  }

  @Test
  public void testBindValueIsMutable() throws Exception {
    bindValueString1 = "newValue";
    assertThat(getBinding1()).isEqualTo("newValue");
  }

  @Test
  public void testCallingComponentReadyWithoutDelayComponentReady_fails() throws Exception {
    IllegalStateException expected =
        assertThrows(IllegalStateException.class, rule::componentReady);
    assertThat(expected)
        .hasMessageThat()
        .isEqualTo("Called componentReady(), even though delayComponentReady() was not used.");
  }

  private static String getBinding1() {
    return EntryPoints.get(getApplicationContext(), BindValueEntryPoint.class).bindValueString1();
  }

  private static String getBinding2() {
    return EntryPoints.get(getApplicationContext(), BindValueEntryPoint.class).bindValueString2();
  }
}
