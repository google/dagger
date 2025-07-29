/*
 * Copyright (C) 2016 The Dagger Authors.
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

package dagger.android;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@LooperMode(LEGACY)
@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P)
public final class AndroidInjectionTest {

  // Most positive tests are performed in javatests/dagger/android/support/functional, but
  // Robolectric's support for framework fragments is lacking, so we supplement those tests here:
  public static class InjectableFragment extends Fragment {
    String tag;
  }

  private static AndroidInjector<Object> fakeFragmentInjector(String tag) {
    return instance -> {
      if (instance instanceof InjectableFragment) {
        ((InjectableFragment) instance).tag = tag;
      }
    };
  }

  public static class ApplicationInjectsFragment extends Application implements HasAndroidInjector {
    @Override
    public AndroidInjector<Object> androidInjector() {
      return fakeFragmentInjector("injected by app");
    }
  }

  @Config(application = ApplicationInjectsFragment.class)
  @Test
  public void fragmentInjectedByApplication() {
    Activity activity = Robolectric.setupActivity(Activity.class);
    InjectableFragment fragment = new InjectableFragment();
    activity.getFragmentManager().beginTransaction().add(fragment, "tag").commit();

    AndroidInjection.inject(fragment);

    assertThat(fragment.tag).isEqualTo("injected by app");
  }

  public static class ActivityInjectsFragment extends Activity implements HasAndroidInjector {
    @Override
    public AndroidInjector<Object> androidInjector() {
      return fakeFragmentInjector("injected by activity");
    }
  }

  @Config(application = ApplicationInjectsFragment.class)
  @Test
  public void fragmentInjectedByActivity() {
    ActivityInjectsFragment activity = Robolectric.setupActivity(ActivityInjectsFragment.class);
    InjectableFragment fragment = new InjectableFragment();
    activity.getFragmentManager().beginTransaction().add(fragment, "tag").commit();

    AndroidInjection.inject(fragment);

    assertThat(fragment.tag).isEqualTo("injected by activity");
  }

  public static class ParentFragmentInjectsChildFragment extends Fragment
      implements HasAndroidInjector {
    @Override
    public AndroidInjector<Object> androidInjector() {
      return fakeFragmentInjector("injected by parent fragment");
    }
  }

  @Config(application = ApplicationInjectsFragment.class)
  @Test
  public void fragmentInjectedByParentFragment() {
    ActivityInjectsFragment activity = Robolectric.setupActivity(ActivityInjectsFragment.class);
    ParentFragmentInjectsChildFragment parentFragment = new ParentFragmentInjectsChildFragment();
    InjectableFragment childFragment = new InjectableFragment();

    activity.getFragmentManager().beginTransaction().add(parentFragment, "tag").commit();
    parentFragment
        .getChildFragmentManager()
        .beginTransaction()
        .add(childFragment, "child-tag")
        .commit();
    AndroidInjection.inject(childFragment);

    assertThat(childFragment.tag).isEqualTo("injected by parent fragment");
  }

  @Test
  public void injectActivity_applicationDoesntImplementHasAndroidInjector() {
    Activity activity = Robolectric.setupActivity(Activity.class);

    try {
      AndroidInjection.inject(activity);
      fail();
    } catch (Exception e) {
      assertThat(e)
          .hasMessageThat()
          .contains("Application does not implement dagger.android.HasAndroidInjector");
    }
  }

  @Test
  public void injectFragment_hasFragmentInjectorNotFound() {
    Fragment fragment = new Fragment();
    Activity activity = Robolectric.setupActivity(Activity.class);
    activity.getFragmentManager().beginTransaction().add(fragment, null).commit();

    try {
      AndroidInjection.inject(fragment);
      fail();
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("No injector was found");
    }
  }

  private static class ApplicationReturnsNull extends Application implements HasAndroidInjector {
    @Override
    public AndroidInjector<Object> androidInjector() {
      return null;
    }
  }

  @Test
  @Config(application = ApplicationReturnsNull.class)
  public void activityInjector_returnsNull() {
    Activity activity = Robolectric.setupActivity(Activity.class);

    try {
      AndroidInjection.inject(activity);
      fail();
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("androidInjector() returned null");
    }
  }

  @Test
  @Config(application = ApplicationReturnsNull.class)
  public void fragmentInjector_returnsNull() {
    Fragment fragment = new Fragment();
    Activity activity = Robolectric.setupActivity(Activity.class);
    activity.getFragmentManager().beginTransaction().add(fragment, null).commit();

    try {
      AndroidInjection.inject(fragment);
      fail();
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("androidInjector() returned null");
    }
  }

  @Test
  public void injectActivity_nullInput() {
    try {
      AndroidInjection.inject((Activity) null);
      fail();
    } catch (NullPointerException e) {
      assertThat(e).hasMessageThat().contains("activity");
    }
  }

  @Test
  public void injectFragment_nullInput() {
    try {
      AndroidInjection.inject((Fragment) null);
      fail();
    } catch (NullPointerException e) {
      assertThat(e).hasMessageThat().contains("fragment");
    }
  }
}
