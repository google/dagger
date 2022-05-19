/*
 * Copyright (C) 2022 The Dagger Authors.
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

import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
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

/** Test that you can use the Hilt ViewModel factory with other owners. */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P, application = HiltTestApplication.class)
public class ViewModelSavedStateOwnerTest {

  @Rule public final HiltAndroidRule rule = new HiltAndroidRule(this);

  @Test
  public void testViewModelSavedState() {
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      scenario.onActivity(
          activity -> {
            NavController navController =
                Navigation.findNavController(activity, R.id.nav_host_fragment);
            TestFragment startFragment = findTestFragment(activity);

            MyViewModel activityVm =
                getViewModel(activity, activity.getDefaultViewModelProviderFactory());
            MyViewModel fragmentVm =
                getViewModel(startFragment, startFragment.getDefaultViewModelProviderFactory());
            MyViewModel fragmentBackStackVm =
                getViewModel(
                    navController.getBackStackEntry(R.id.start_destination),
                    startFragment.getDefaultViewModelProviderFactory());
            MyViewModel navGraphVm =
                getViewModel(
                    navController.getBackStackEntry(R.id.nav_graph),
                    startFragment.getDefaultViewModelProviderFactory());

            // The activity shouldn't have any arguments since it was only set on the fragment.
            assertThat((String) activityVm.savedStateHandle.get("argument_key")).isNull();
            activityVm.savedStateHandle.set("other_key", "activity_other_key");

            // The fragment argument (set in the navgraph xml) should be set.
            assertThat((String) fragmentVm.savedStateHandle.get("argument_key"))
                .isEqualTo("fragment_argument");
            fragmentVm.savedStateHandle.set("other_key", "fragment_other_key");

            // The back stack entry also has the fragment arguments
            assertThat((String) fragmentBackStackVm.savedStateHandle.get("argument_key"))
                .isEqualTo("fragment_argument");
            fragmentBackStackVm.savedStateHandle.set("other_key", "fragment_backstack_other_key");

            // When the nav graph itself is the owner, then there should be no arguments.
            assertThat((String) navGraphVm.savedStateHandle.get("argument_key")).isNull();
            navGraphVm.savedStateHandle.set("other_key", "nav_graph_other_key");

            navController.navigate(R.id.next_destination);
          });

      // Now move to the next fragment to compare
      scenario.onActivity(
          activity -> {
            NavController navController =
                Navigation.findNavController(activity, R.id.nav_host_fragment);

            TestFragment nextFragment = findTestFragment(activity);

            MyViewModel activityVm =
                getViewModel(activity, activity.getDefaultViewModelProviderFactory());
            MyViewModel fragmentVm =
                getViewModel(nextFragment, nextFragment.getDefaultViewModelProviderFactory());
            MyViewModel navGraphVm =
                getViewModel(
                    navController.getBackStackEntry(R.id.nav_graph),
                    nextFragment.getDefaultViewModelProviderFactory());
            MyViewModel fragmentBackStackVm =
                getViewModel(
                    navController.getBackStackEntry(R.id.next_destination),
                    nextFragment.getDefaultViewModelProviderFactory());

            // The activity still shouldn't have any arguments, but since it is the same
            // owner (since the activity didn't change), the other key should still be set
            // from before.
            assertThat((String) activityVm.savedStateHandle.get("argument_key")).isNull();
            assertThat((String) activityVm.savedStateHandle.get("other_key"))
                .isEqualTo("activity_other_key");

            // The fragment argument should be set via the navgraph xml again. Also, since
            // this is a new fragment, the other key should not be set.
            assertThat((String) fragmentVm.savedStateHandle.get("argument_key"))
                .isEqualTo("next_fragment_argument");
            assertThat((String) fragmentVm.savedStateHandle.get("other_key")).isNull();

            // Same as using the fragment as the owner.
            assertThat((String) fragmentBackStackVm.savedStateHandle.get("argument_key"))
                .isEqualTo("next_fragment_argument");
            assertThat((String) fragmentBackStackVm.savedStateHandle.get("other_key")).isNull();

            // Similar to the activity case, the navgraph is the same so we expect the same
            // key to be set from before. Arguments should still be missing.
            assertThat((String) navGraphVm.savedStateHandle.get("argument_key")).isNull();
            assertThat((String) navGraphVm.savedStateHandle.get("other_key"))
                .isEqualTo("nav_graph_other_key");
          });
    }
  }

  private TestFragment findTestFragment(FragmentActivity activity) {
    return (TestFragment)
        activity
            .getSupportFragmentManager()
            .findFragmentById(R.id.nav_host_fragment)
            .getChildFragmentManager()
            .getPrimaryNavigationFragment();
  }

  private MyViewModel getViewModel(ViewModelStoreOwner owner, ViewModelProvider.Factory factory) {
    return new ViewModelProvider(owner, factory).get(MyViewModel.class);
  }

  @AndroidEntryPoint(FragmentActivity.class)
  public static class TestActivity extends Hilt_ViewModelSavedStateOwnerTest_TestActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.navigation_activity);
    }
  }

  @AndroidEntryPoint(Fragment.class)
  public static class TestFragment extends Hilt_ViewModelSavedStateOwnerTest_TestFragment {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
    }
  }

  @HiltViewModel
  static class MyViewModel extends ViewModel {
    final SavedStateHandle savedStateHandle;

    @Inject
    MyViewModel(SavedStateHandle savedStateHandle) {
      this.savedStateHandle = savedStateHandle;
    }
  }
}
