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
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FragmentInjectionTest {

  @get:Rule
  val rule = HiltAndroidRule(this)

  @Test
  fun verifyInjection() {
    ActivityScenario.launch(TestActivity::class.java).use {
      it.onActivity { activity ->
        activity.addTestFragment()
        activity.supportFragmentManager.findTestFragment().let { fragment ->
          assertThat(fragment.myAndroidViewModel).isNotNull()
          assertThat(fragment.myViewModel).isNotNull()
          assertThat(fragment.myInjectedViewModel).isNotNull()
          assertThat(fragment.myInjectedViewModelWithSavedState).isNotNull()
        }
      }
    }
  }

  @Test
  fun verifyActivityViewModelInjection() {
    ActivityScenario.launch(TestActivity::class.java).use {
      it.onActivity { activity ->
        activity.addTestFragment()
        activity.supportFragmentManager.findTestFragment().let { fragment ->
          assertThat(fragment.myInjectedViewModel).isNotNull()
          assertThat(fragment.myActivityLevelInjectedViewModel).isNotNull()
          assertThat(fragment.myInjectedViewModel)
            .isNotEqualTo(fragment.myActivityLevelInjectedViewModel)
          assertThat(fragment.myActivityLevelInjectedViewModel)
            .isEqualTo(activity.myInjectedViewModel)
        }
        activity.removeTestFragment()
        activity.addTestFragment()
        activity.supportFragmentManager.findTestFragment().let { fragment ->
          assertThat(fragment.myInjectedViewModel).isNotNull()
          assertThat(fragment.myActivityLevelInjectedViewModel).isNotNull()
          assertThat(fragment.myInjectedViewModel)
            .isNotEqualTo(fragment.myActivityLevelInjectedViewModel)
          assertThat(fragment.myActivityLevelInjectedViewModel)
            .isEqualTo(activity.myInjectedViewModel)
        }
      }
    }
  }

  @AndroidEntryPoint
  class TestActivity : FragmentActivity() {

    val myInjectedViewModel by viewModels<MyInjectedViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      addTestFragment()
    }

    fun addTestFragment() {
      val fragment = supportFragmentManager.fragmentFactory.instantiate(
        TestFragment::class.java.classLoader!!,
        TestFragment::class.java.name
      )
      supportFragmentManager.beginTransaction()
        .add(0, fragment, FRAGMENT_TAG)
        .commitNow()
    }

    fun removeTestFragment() {
      supportFragmentManager.beginTransaction()
        .remove(supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)!!)
        .commitNow()
    }
  }

  @AndroidEntryPoint
  class TestFragment : Fragment() {
    val myAndroidViewModel by viewModels<MyAndroidViewModel>()
    val myViewModel by viewModels<MyViewModel>()
    val myInjectedViewModel by viewModels<MyInjectedViewModel>()
    val myInjectedViewModelWithSavedState by viewModels<MyInjectedViewModelWithSavedState>()
    val myActivityLevelInjectedViewModel by activityViewModels<MyInjectedViewModel>()
  }

  private fun FragmentManager.findTestFragment() =
    findFragmentByTag(FRAGMENT_TAG) as TestFragment

  companion object {
    const val FRAGMENT_TAG = "tag"
  }
}
