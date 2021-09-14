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

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.hilt.android.flags.FragmentGetContextFix;
import dagger.hilt.android.testing.BindValueIntoSet;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.HiltTestApplication;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public final class FragmentContextOnAttachTest {

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @BindValueIntoSet
  @FragmentGetContextFix.DisableFragmentGetContextFix
  boolean disableGetContextFix = false;

  /** Hilt Activity */
  @AndroidEntryPoint(FragmentActivity.class)
  public static final class TestActivity extends Hilt_FragmentContextOnAttachTest_TestActivity {}

  /** Hilt Fragment */
  @AndroidEntryPoint(Fragment.class)
  public static final class TestFragment extends Hilt_FragmentContextOnAttachTest_TestFragment {
    Context onAttachContextContext = null;
    Context onAttachActivityContext = null;

    @Override
    public void onAttach(Context context) {
      // Test that getContext() can be called at this point
      onAttachContextContext = getContext();
      super.onAttach(context);
    }

    @Override
    public void onAttach(Activity activity) {
      // Test that getContext() can be called at this point
      onAttachActivityContext = getContext();
      super.onAttach(activity);
    }
  }

  @Test
  public void testGetContextAvailableBeforeSuperOnAttach() throws Exception {
    FragmentActivity activity = Robolectric.setupActivity(TestActivity.class);
    TestFragment fragment = new TestFragment();
    activity.getSupportFragmentManager().beginTransaction().add(fragment, "").commitNow();
    assertThat(fragment.onAttachContextContext).isNotNull();
    assertThat(fragment.onAttachActivityContext).isNotNull();
  }

  // Tests the behavior when using the useFragmentGetContextFix flag.
  @Test
  public void testGetContextReturnsNullAfterRemoval() throws Exception {
    FragmentActivity activity = Robolectric.setupActivity(TestActivity.class);
    TestFragment fragment = new TestFragment();
    activity.getSupportFragmentManager().beginTransaction().add(fragment, "").commitNow();
    assertThat(fragment.getContext()).isNotNull();
    activity.getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
    // This should be null since the fix was enabled by the compiler flag and runtime flag
    assertThat(fragment.getContext()).isNull();

    // Flip the flag so that we now disable the fix
    disableGetContextFix = true;
    TestFragment fragment2 = new TestFragment();
    activity.getSupportFragmentManager().beginTransaction().add(fragment2, "").commitNow();
    assertThat(fragment2.getContext()).isNotNull();
    activity.getSupportFragmentManager().beginTransaction().remove(fragment2).commitNow();
    // This should not be null since the fix was disabled by the runtime flag
    assertThat(fragment2.getContext()).isNotNull();
  }
}
