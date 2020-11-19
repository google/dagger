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

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BaseFragmentInjectionTest {

  @get:Rule
  val rule = HiltAndroidRule(this)

  @Test
  fun verifyInjection() {
    ActivityScenario.launch(TestActivity::class.java).use {
      it.onActivity { activity ->
        val fragment = activity.supportFragmentManager.fragmentFactory.instantiate(
          TestFragment::class.java.classLoader!!,
          TestFragment::class.java.name
        ) as TestFragment
        activity.supportFragmentManager.beginTransaction()
          .add(0, fragment, FragmentInjectionTest.FRAGMENT_TAG)
          .commitNow()
        assertThat(fragment.myAndroidViewModel).isNotNull()
        assertThat(fragment.myViewModel).isNotNull()
        assertThat(fragment.myInjectedViewModel).isNotNull()
      }
    }
  }

  @AndroidEntryPoint
  class TestActivity : FragmentActivity()

  @AndroidEntryPoint
  class TestFragment : BaseFragment()

  abstract class BaseFragment : Fragment() {
    val myAndroidViewModel by viewModels<MyAndroidViewModel>()
    val myViewModel by viewModels<MyViewModel>()
    val myInjectedViewModel by viewModels<MyInjectedViewModel>()
  }
}
