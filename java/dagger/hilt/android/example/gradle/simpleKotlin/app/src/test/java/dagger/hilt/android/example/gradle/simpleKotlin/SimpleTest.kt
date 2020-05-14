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

package dagger.hilt.android.example.gradle.simpleKotlin

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = [Build.VERSION_CODES.P], application = HiltTestApplication::class)
class SimpleTest {
  @Rule
  @JvmField
  var rule = HiltAndroidRule(this)

  @AndroidEntryPoint
  class TestActivity : AppCompatActivity()

  @Test
  fun verifyMainActivity() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertThat(activity::class.java.getSuperclass()?.getSimpleName())
          .isEqualTo("Hilt_MainActivity")
        assertThat(activity.model).isNotNull()
        assertThat(activity.name).isNotNull()
      }
    }
  }

  @Test
  fun verifyTestActivity() {
    ActivityScenario.launch(TestActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertThat(activity::class.java.getSuperclass()?.getSimpleName())
          .isEqualTo("Hilt_SimpleTest_TestActivity")
      }
    }
  }
}
