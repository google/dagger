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

package dagger.android.simple;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** A simple test using Hilt that can be run with instrumentation or Robolectric tests. */
@RunWith(AndroidJUnit4.class)
public final class SimpleActivityTest {

  @Test
  public void testActivityInject() throws Exception {
    try (ActivityScenario<SimpleActivity> scenario =
        ActivityScenario.launch(SimpleActivity.class)) {
      scenario.onActivity(
          activity -> {
            onView(withId(R.id.greeting))
                .check(matches(withText("Hello, ProdUser! You are on build robolectric.")));
          });
    }
  }

  @Test
  public void verifyApplicationInstance() {
    assertThat((Context) ApplicationProvider.getApplicationContext())
        .isInstanceOf(SimpleApplication.class);
  }
}
