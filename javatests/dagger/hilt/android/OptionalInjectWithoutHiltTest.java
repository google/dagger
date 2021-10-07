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
import static dagger.hilt.android.migration.OptionalInjectCheck.wasInjectedByHilt;
import static org.junit.Assert.assertThrows;

import android.content.Intent;
import android.os.Build;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.OptionalInjectTestClasses.NonHiltViewModel;
import dagger.hilt.android.OptionalInjectTestClasses.OptionalSubclassActivity;
import dagger.hilt.android.OptionalInjectTestClasses.TestActivity;
import dagger.hilt.android.OptionalInjectTestClasses.TestBroadcastReceiver;
import dagger.hilt.android.OptionalInjectTestClasses.TestFragment;
import dagger.hilt.android.OptionalInjectTestClasses.TestIntentService;
import dagger.hilt.android.OptionalInjectTestClasses.TestService;
import dagger.hilt.android.OptionalInjectTestClasses.TestView;
import dagger.hilt.android.OptionalInjectTestClasses.TestViewModel;
import dagger.hilt.android.OptionalInjectTestClasses.TestWithFragmentBindingsView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

/** Tests that optional inject work without a Hilt root. */
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P)
public final class OptionalInjectWithoutHiltTest {
  @Test
  public void testActivityInjection() throws Exception {
    TestActivity testActivity = Robolectric.setupActivity(TestActivity.class);
    assertThat(testActivity.testActivityBinding).isNull();
    assertThat(testActivity.wasInjectedByHilt()).isFalse();
    assertThat(wasInjectedByHilt(testActivity)).isFalse();
  }

  @Test
  public void testOptionalSubclassActivityInjection() throws Exception {
    OptionalSubclassActivity testActivity = Robolectric.setupActivity(
        OptionalSubclassActivity.class);
    assertThat(testActivity.testActivityBinding).isNull();
    assertThat(testActivity.testActivitySubclassBinding).isNull();
    assertThat(testActivity.wasInjectedByHilt()).isFalse();
    assertThat(wasInjectedByHilt(testActivity)).isFalse();
  }

  @Test
  public void testFragmentInjection() throws Exception {
    TestActivity testActivity = Robolectric.setupActivity(TestActivity.class);
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
    assertThat(testView.testViewBinding).isNull();
    assertThat(testView.wasInjectedByHilt()).isFalse();
    assertThat(wasInjectedByHilt(testView)).isFalse();
  }

  @Test
  public void testViewModels() {
    TestActivity testActivity = Robolectric.setupActivity(TestActivity.class);
    TestFragment testFragment = new TestFragment();
    testActivity.getSupportFragmentManager()
        .beginTransaction()
        .add(testFragment, null)
        .commitNow();
    assertThat(new ViewModelProvider(testActivity).get(NonHiltViewModel.class)).isNotNull();
    assertThat(new ViewModelProvider(testFragment).get(NonHiltViewModel.class)).isNotNull();

    // Hilt View Models aren't usable in this case, so check that it throws.
    RuntimeException activityException =
        assertThrows(
            RuntimeException.class,
            () -> new ViewModelProvider(testFragment).get(TestViewModel.class));
    assertThat(activityException)
        .hasMessageThat()
        .contains("TestViewModel");
    RuntimeException fragmentException =
        assertThrows(
            RuntimeException.class,
            () -> new ViewModelProvider(testFragment).get(TestViewModel.class));
    assertThat(fragmentException)
        .hasMessageThat()
        .contains("TestViewModel");
  }

  @Test
  public void testServiceInjection() throws Exception {
    TestService testService = Robolectric.setupService(TestService.class);
    assertThat(testService.testAppBinding).isNull();
    assertThat(testService.wasInjectedByHilt()).isFalse();
    assertThat(wasInjectedByHilt(testService)).isFalse();
  }

  @Test
  public void testIntentServiceInjection() throws Exception {
    TestIntentService testIntentService = Robolectric.setupService(TestIntentService.class);
    assertThat(testIntentService.testAppBinding).isNull();
    assertThat(testIntentService.wasInjectedByHilt()).isFalse();
    assertThat(wasInjectedByHilt(testIntentService)).isFalse();
  }

  @Test
  public void testBroadcastReceiverInjection() throws Exception {
    TestBroadcastReceiver testBroadcastReceiver = new TestBroadcastReceiver();
    Intent intent = new Intent();
    testBroadcastReceiver.onReceive(ApplicationProvider.getApplicationContext(), intent);
    assertThat(testBroadcastReceiver.testAppBinding).isNull();
    assertThat(testBroadcastReceiver.wasInjectedByHilt()).isFalse();
    assertThat(wasInjectedByHilt(testBroadcastReceiver)).isFalse();
  }
}
