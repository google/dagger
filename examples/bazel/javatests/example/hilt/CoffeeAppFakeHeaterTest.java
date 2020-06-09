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

package example.hilt;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Build;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import example.common.CoffeeLogger;
import example.common.CoffeeMaker;
import example.common.Heater;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Tests using a fake heater. */
@HiltAndroidTest
@Config(sdk = {Build.VERSION_CODES.P}, application = HiltTestApplication.class)
@RunWith(AndroidJUnit4.class)
public final class CoffeeAppFakeHeaterTest {
  public @Rule HiltAndroidRule rule = new HiltAndroidRule(this);

  final class FakeHeater implements Heater {
    private boolean heating;

    @Override
    public void on() {
      this.heating = true;
      logger.log("~ ~ ~ fake heating ~ ~ ~");
    }

    @Override
    public void off() {
      this.heating = false;
    }

    @Override
    public boolean isHot() {
      return heating;
    }
  }

  @Inject CoffeeMaker maker;
  @Inject CoffeeLogger logger;

  @BindValue Heater fakeHeater = new FakeHeater();

  @Test
  public void testApplicationClass() throws Exception {
    assertThat((Context) ApplicationProvider.getApplicationContext())
        .isInstanceOf(HiltTestApplication.class);
  }

  @Test
  public void testLogs() throws Exception {
    rule.inject();
    assertThat(logger.logs()).isEmpty();
    maker.brew();
    assertThat(logger.logs())
        .containsExactly(
            "~ ~ ~ fake heating ~ ~ ~",
            "=> => pumping => =>",
            " [_]P coffee! [_]P ")
        .inOrder();
  }
}
