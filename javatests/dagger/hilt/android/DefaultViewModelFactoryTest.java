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

package dagger.hilt.android;

import static com.google.common.truth.Truth.assertThat;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.os.Build;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public class DefaultViewModelFactoryTest {

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @BindValue String hiltStringValue = "hilt";

  @Test
  public void activityFactoryFallsBackToBase() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            assertThat(new ViewModelProvider(activity).get(TestHiltViewModel.class).value)
                .isEqualTo("hilt");
            assertThat(new ViewModelProvider(activity).get(TestViewModel.class).value)
                .isEqualTo("non-hilt");
          });
    }
  }

  @Test
  public void fragmentFactoryFallbsBackToBase() {
    // TODO(danysantiago): Use FragmentScenario when it becomes available.
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            TestFragment fragment = new TestFragment();
            activity.getSupportFragmentManager().beginTransaction().add(fragment, "").commitNow();
            assertThat(new ViewModelProvider(fragment).get(TestHiltViewModel.class).value)
                .isEqualTo("hilt");
            assertThat(new ViewModelProvider(fragment).get(TestViewModel.class).value)
                .isEqualTo("non-hilt");
          });
    }
  }

  @HiltViewModel
  public static final class TestHiltViewModel extends ViewModel {
    final String value;

    @Inject
    TestHiltViewModel(String value) {
      this.value = value;
    }
  }

  public static final class TestViewModel extends ViewModel {
    final String value;
    // Take in a string so it cannot be constructed by the default view model factory
    public TestViewModel(String value) {
      this.value = value;
    }
  }

  @AndroidEntryPoint(BaseActivity.class)
  public static final class TestActivity extends Hilt_DefaultViewModelFactoryTest_TestActivity {}

  public static class BaseActivity extends FragmentActivity {
    @SuppressWarnings("unchecked")
    @Override public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
      return new ViewModelProvider.Factory() {
        @Override public <T extends ViewModel> T create(Class<T> clazz) {
          assertThat(clazz).isEqualTo(TestViewModel.class);
          return (T) new TestViewModel("non-hilt");
        }
      };
    }
  }

  @AndroidEntryPoint(BaseFragment.class)
  public static final class TestFragment extends Hilt_DefaultViewModelFactoryTest_TestFragment {}

  public static class BaseFragment extends Fragment {
    @SuppressWarnings("unchecked")
    @Override public ViewModelProvider.Factory getDefaultViewModelProviderFactory() {
      return new ViewModelProvider.Factory() {
        @Override public <T extends ViewModel> T create(Class<T> clazz) {
          assertThat(clazz).isEqualTo(TestViewModel.class);
          return (T) new TestViewModel("non-hilt");
        }
      };
    }
  }
}
