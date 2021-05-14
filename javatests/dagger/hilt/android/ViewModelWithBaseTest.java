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

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.lifecycle.HiltViewModel;
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
public class ViewModelWithBaseTest {

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void verifyBaseInjection() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            assertThat(activity.myViewModel.foo).isNotNull();
            assertThat(activity.myViewModel.bar).isNotNull();
          });
    }
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static class TestActivity extends Hilt_ViewModelWithBaseTest_TestActivity {

    MyViewModel myViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      myViewModel = new ViewModelProvider(this).get(MyViewModel.class);
    }
  }

  @HiltViewModel
  static class MyViewModel extends BaseViewModel {

    final Foo foo;

    @Inject
    MyViewModel(SavedStateHandle handle, Foo foo) {
      this.foo = foo;
    }
  }

  abstract static class BaseViewModel extends ViewModel {
    @Inject Bar bar;
  }

  static class Foo {
    @Inject
    Foo() {}
  }

  static class Bar {
    @Inject
    Bar() {}
  }
}
