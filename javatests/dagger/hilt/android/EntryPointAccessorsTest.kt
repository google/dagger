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

package dagger.hilt.android

import android.content.Context
import android.os.Build
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import android.view.View
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.components.FragmentComponent
import dagger.hilt.android.components.ViewComponent
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = [Build.VERSION_CODES.P], application = HiltTestApplication::class)
class EntryPointAccessorsTest {

  companion object {
    const val APPLICATION_STRING = "APPLICATION_STRING"
    const val ACTIVITY_STRING = "ACTIVITY_STRING"
    const val FRAGMENT_STRING = "FRAGMENT_STRING"
    const val VIEW_STRING = "VIEW_STRING"
  }

  @get:Rule
  var rule = HiltAndroidRule(this)

  @Qualifier
  @Retention(AnnotationRetention.BINARY)
  annotation class ApplicationLevel

  @Qualifier
  @Retention(AnnotationRetention.BINARY)
  annotation class ActivityLevel

  @Qualifier
  @Retention(AnnotationRetention.BINARY)
  annotation class FragmentLevel

  @Qualifier
  @Retention(AnnotationRetention.BINARY)
  annotation class ViewLevel

  @Module
  @InstallIn(SingletonComponent::class)
  internal object ApplicationModule {
    @ApplicationLevel
    @Provides
    fun provideString(): String {
      return APPLICATION_STRING
    }
  }

  @Module
  @InstallIn(ActivityComponent::class)
  internal object ActivityModule {
    @ActivityLevel
    @Provides
    fun provideString(): String {
      return ACTIVITY_STRING
    }
  }

  @Module
  @InstallIn(FragmentComponent::class)
  internal object FragmentModule {
    @FragmentLevel
    @Provides
    fun provideString(): String {
      return FRAGMENT_STRING
    }
  }

  @Module
  @InstallIn(ViewComponent::class)
  internal object ViewModule {
    @ViewLevel
    @Provides
    fun provideString(): String {
      return VIEW_STRING
    }
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  internal interface ApplicationEntryPoint {
    @ApplicationLevel
    fun getString(): String
  }

  @EntryPoint
  @InstallIn(ActivityComponent::class)
  internal interface ActivityEntryPoint {
    @ActivityLevel
    fun getString(): String
  }

  @EntryPoint
  @InstallIn(FragmentComponent::class)
  internal interface FragmentEntryPoint {
    @FragmentLevel
    fun getString(): String
  }

  @EntryPoint
  @InstallIn(ViewComponent::class)
  internal interface ViewEntryPoint {
    @ViewLevel
    fun getString(): String
  }

  @Test
  fun testApplicationEntryPoint() {
    val app = getApplicationContext<HiltTestApplication>()
    val entryPoint = EntryPointAccessors.fromApplication<ApplicationEntryPoint>(app)
    Truth.assertThat(entryPoint.getString())
      .isEqualTo(APPLICATION_STRING)

    val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
    val applicationEntryPoint = EntryPointAccessors.fromApplication<ApplicationEntryPoint>(activity)
    Truth.assertThat(applicationEntryPoint.getString())
      .isEqualTo(APPLICATION_STRING)
  }

  @Test
  fun testActivityEntryPoint() {
    val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
    val entryPoint = EntryPointAccessors.fromActivity<ActivityEntryPoint>(activity)
    Truth.assertThat(entryPoint.getString())
      .isEqualTo(ACTIVITY_STRING)
  }

  @Test
  fun testFragmentEntryPoint() {
    val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
    val fragment = TestFragment()
    activity.supportFragmentManager.beginTransaction().add(fragment, "").commitNow()
    val entryPoint = EntryPointAccessors.fromFragment<FragmentEntryPoint>(fragment)
    Truth.assertThat(entryPoint.getString())
      .isEqualTo(FRAGMENT_STRING)
  }

  @Test
  fun testViewEntryPoint() {
    val activity = Robolectric.buildActivity(TestActivity::class.java).setup().get()
    val view = TestView(activity)
    val entryPoint = EntryPointAccessors.fromView<ViewEntryPoint>(view)
    Truth.assertThat(entryPoint.getString())
      .isEqualTo(VIEW_STRING)
  }

  @AndroidEntryPoint(FragmentActivity::class)
  class TestActivity : Hilt_EntryPointAccessorsTest_TestActivity()

  @AndroidEntryPoint(Fragment::class)
  class TestFragment : Hilt_EntryPointAccessorsTest_TestFragment()

  @AndroidEntryPoint(View::class)
  class TestView(context: Context) : Hilt_EntryPointAccessorsTest_TestView(context)
}
