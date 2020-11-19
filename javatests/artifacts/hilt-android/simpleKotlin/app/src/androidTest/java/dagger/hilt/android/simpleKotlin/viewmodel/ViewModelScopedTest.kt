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

package dagger.hilt.android.simpleKotlin.viewmodel

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.lifecycle.ViewModelInject
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ViewModelScopedTest {

  @get:Rule
  val rule = HiltAndroidRule(this)

  @Test
  fun testViewModelScopeInFragment() {
    ActivityScenario.launch(TestActivity::class.java).use {
      it.onActivity { activity ->
        activity.supportFragmentManager.findTestFragment().let { fragment ->
          val vm = fragment.vm
          assertThat(vm.one.bar).isEqualTo(vm.two.bar)
        }
      }
    }
  }

  @Module
  @InstallIn(ViewModelComponent::class)
  object ScopedModule {
    @Provides
    @ViewModelScoped
    fun provideBar() = Bar()
  }

  @AndroidEntryPoint
  class TestActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)

      if (savedInstanceState == null) {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
          TestFragment::class.java.classLoader!!,
          TestFragment::class.java.name
        )
        supportFragmentManager.beginTransaction()
          .add(0, fragment, FRAGMENT_TAG)
          .commitNow()
      }
    }
  }

  @AndroidEntryPoint
  class TestFragment : Fragment() {
    val vm by viewModels<TestViewModel>()
  }

  class TestViewModel @ViewModelInject constructor(
    val one: DependsOnBarOne,
    val two: DependsOnBarTwo
  ) : ViewModel()

  class DependsOnBarOne @Inject constructor(val bar: Bar)
  class DependsOnBarTwo @Inject constructor(val bar: Bar)

  private fun FragmentManager.findTestFragment() =
    findFragmentByTag(FRAGMENT_TAG) as TestFragment

  companion object {
    const val FRAGMENT_TAG = "tag"
  }
}
