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

package dagger.hilt.android;

import static com.google.common.truth.Truth.assertThat;
import static dagger.hilt.android.OptionalInjectTestClasses.ACTIVITY_BINDING;
import static dagger.hilt.android.OptionalInjectTestClasses.APP_BINDING;
import static dagger.hilt.android.OptionalInjectTestClasses.FRAGMENT_BINDING;
import static dagger.hilt.android.OptionalInjectTestClasses.VIEW_BINDING;
import static dagger.hilt.android.migration.OptionalInjectCheck.wasInjectedByHilt;
import static org.junit.Assert.assertThrows;

import android.content.Intent;
import android.os.Build;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.OptionalInjectTestClasses.NonHiltViewModel;
import dagger.hilt.android.OptionalInjectTestClasses.NonOptionalSubclassActivity;
import dagger.hilt.android.OptionalInjectTestClasses.OptionalSubclassActivity;
import dagger.hilt.android.OptionalInjectTestClasses.TestActivity;
import dagger.hilt.android.OptionalInjectTestClasses.TestBroadcastReceiver;
import dagger.hilt.android.OptionalInjectTestClasses.TestFragment;
import dagger.hilt.android.OptionalInjectTestClasses.TestIntentService;
import dagger.hilt.android.OptionalInjectTestClasses.TestService;
import dagger.hilt.android.OptionalInjectTestClasses.TestView;
import dagger.hilt.android.OptionalInjectTestClasses.TestViewModel;
import dagger.hilt.android.OptionalInjectTestClasses.TestWithFragmentBindingsView;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

/** Tests that optional inject works with a Hilt root. */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public final class OptionalInjectWithHiltTest {
  @Rule public final HiltAndroidRule rules = new HiltAndroidRule(this);

  @Test
  public void testActivityInjection() throws Exception {
    TestActivity testActivity = Robolectric.setupActivity(TestActivity.class);
    assertThat(testActivity.testActivityBinding).isEqualTo(ACTIVITY_BINDING);
    assertThat(testActivity.wasInjectedByHilt()).isTrue();
    assertThat(wasInjectedByHilt(testActivity)).isTrue();
  }

  @Test
  public void testNonOptionalSubclassActivityInjection() throws Exception {
    NonOptionalSubclassActivity testActivity = Robolectric.setupActivity(
        NonOptionalSubclassActivity.class);
    assertThat(testActivity.testActivityBinding).isEqualTo(ACTIVITY_BINDING);
    assertThat(testActivity.testActivitySubclassBinding).isEqualTo(ACTIVITY_BINDING);
    assertThat(testActivity.wasInjectedByHilt()).isTrue();
    assertThat(wasInjectedByHilt(testActivity)).isTrue();
  }

  @Test
  public void testOptionalSubclassActivityInjection() throws Exception {
    OptionalSubclassActivity testActivity = Robolectric.setupActivity(
        OptionalSubclassActivity.class);
    assertThat(testActivity.testActivityBinding).isEqualTo(ACTIVITY_BINDING);
    assertThat(testActivity.testActivitySubclassBinding).isEqualTo(ACTIVITY_BINDING);
    assertThat(testActivity.wasInjectedByHilt()).isTrue();
    assertThat(wasInjectedByHilt(testActivity)).isTrue();
  }

  @Test
  public void testFragmentInjection() throws Exception {
    TestActivity testActivity = Robolectric.setupActivity(TestActivity.class);
    TestFragment testFragment = new TestFragment();
    testActivity.getSupportFragmentManager()
        .beginTransaction()
        .add(testFragment, null)
        .commitNow();
    assertThat(testFragment.testFragmentBinding).isEqualTo(FRAGMENT_BINDING);
    assertThat(testFragment.wasInjectedByHilt()).isTrue();
    assertThat(wasInjectedByHilt(testFragment)).isTrue();
  }

  @Test
  public void testFragmentInjectionWithNonHiltActivityWithHiltRoot() throws Exception {
    FragmentActivity testActivity = Robolectric.setupActivity(FragmentActivity.class);
    TestFragment testFragment = new TestFragment();
    testActivity.getSupportFragmentManager()
        .beginTransaction()
        .add(testFragment, null)
        .commitNow();
    assertThat(testFragment.testFragmentBinding).isNull();
    assertThat(testFragment.wasInjectedByHilt()).isFalse();
    assertThat(wasInjectedByHilt(testFragment)).isFalse();
  }

  @Test
  public void testViewInjection() throws Exception {
    TestActivity testActivity = Robolectric.setupActivity(TestActivity.class);
    TestView testView = new TestView(testActivity);
    assertThat(testView.testViewBinding).isEqualTo(VIEW_BINDING);
    assertThat(testView.wasInjectedByHilt()).isTrue();
    assertThat(wasInjectedByHilt(testView)).isTrue();
  }

  @Test
  public void testViewInjectionWithNonHiltActivityWithHiltRoot() throws Exception {
    FragmentActivity testActivity = Robolectric.setupActivity(FragmentActivity.class);
    TestView testView = new TestView(testActivity);
    assertThat(testView.testViewBinding).isNull();
    assertThat(testView.wasInjectedByHilt()).isFalse();
    assertThat(wasInjectedByHilt(testView)).isFalse();
  }

  @Test
  public void testViewWithFragmentBindingsInjection() throws Exception {
    TestActivity testActivity = Robolectric.setupActivity(TestActivity.class);
    TestFragment testFragment = new TestFragment();
    testActivity.getSupportFragmentManager()
        .beginTransaction()
        .add(testFragment, null)
        .commitNow();

    TestWithFragmentBindingsView testView = new TestWithFragmentBindingsView(
        testFragment.getLayoutInflater().getContext());
    assertThat(testView.testViewBinding).isEqualTo(VIEW_BINDING);
    assertThat(testView.wasInjectedByHilt()).isTrue();
    assertThat(wasInjectedByHilt(testView)).isTrue();
  }

  @Test
  public void testViewWithFragmentBindingsInjectionWithNonHiltFragmentWithHiltRoot()
      throws Exception {
    TestActivity testActivity = Robolectric.setupActivity(TestActivity.class);
    Fragment testFragment = new Fragment();
    testActivity.getSupportFragmentManager()
        .beginTransaction()
        .add(testFragment, null)
        .commitNow();

    TestWithFragmentBindingsView testView = new TestWithFragmentBindingsView(
        testFragment.getLayoutInflater().getContext());
    assertThat(testView.testViewBinding).isNull();
    assertThat(testView.wasInjectedByHilt()).isFalse();
    assertThat(wasInjectedByHilt(testView)).isFalse();
  }

  @Test
  public void testHiltViewModels() {
    TestActivity testActivity = Robolectric.setupActivity(TestActivity.class);
    TestFragment testFragment = new TestFragment();
    testActivity.getSupportFragmentManager()
        .beginTransaction()
        .add(testFragment, null)
        .commitNow();
    assertThat(new ViewModelProvider(testActivity).get(TestViewModel.class).appBinding)
        .isEqualTo(APP_BINDING);
    assertThat(new ViewModelProvider(testActivity).get(NonHiltViewModel.class)).isNotNull();
    assertThat(new ViewModelProvider(testFragment).get(TestViewModel.class).appBinding)
        .isEqualTo(APP_BINDING);
    assertThat(new ViewModelProvider(testFragment).get(NonHiltViewModel.class)).isNotNull();
  }

  @Test
  public void testHiltViewModelsWithNonHiltActivityWithHiltRoot() throws Exception {
    FragmentActivity testActivity = Robolectric.setupActivity(FragmentActivity.class);
    TestFragment testFragment = new TestFragment();
    testActivity.getSupportFragmentManager()
        .beginTransaction()
        .add(testFragment, null)
        .commitNow();
    assertThat(new ViewModelProvider(testActivity).get(NonHiltViewModel.class)).isNotNull();
    assertThat(new ViewModelProvider(testFragment).get(NonHiltViewModel.class)).isNotNull();

    // Hilt View Models aren't usable in this case, so check that it throws. We only test with the
    // owner as the fragment since the activity is just a plain FragmentActivity.
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> new ViewModelProvider(testFragment).get(TestViewModel.class));
    assertThat(exception)
        .hasMessageThat()
        .contains("TestViewModel");
  }

  @Test
  public void testServiceInjection() throws Exception {
    TestService testService = Robolectric.setupService(TestService.class);
    assertThat(testService.testAppBinding).isEqualTo(APP_BINDING);
    assertThat(testService.wasInjectedByHilt()).isTrue();
    assertThat(wasInjectedByHilt(testService)).isTrue();
  }

  @Test
  public void testIntentServiceInjection() throws Exception {
    TestIntentService testIntentService = Robolectric.setupService(TestIntentService.class);
    assertThat(testIntentService.testAppBinding).isEqualTo(APP_BINDING);
    assertThat(testIntentService.wasInjectedByHilt()).isTrue();
    assertThat(wasInjectedByHilt(testIntentService)).isTrue();
  }

  @Test
  public void testBroadcastReceiverInjection() throws Exception {
    TestBroadcastReceiver testBroadcastReceiver = new TestBroadcastReceiver();
    Intent intent = new Intent();
    testBroadcastReceiver.onReceive(ApplicationProvider.getApplicationContext(), intent);
    assertThat(testBroadcastReceiver.testAppBinding).isEqualTo(APP_BINDING);
    assertThat(testBroadcastReceiver.wasInjectedByHilt()).isTrue();
    assertThat(wasInjectedByHilt(testBroadcastReceiver)).isTrue();
  }
}
