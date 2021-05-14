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

package dagger.android.support;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.app.Application;
import android.os.Build;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import dagger.android.AndroidInjector;
import dagger.android.HasAndroidInjector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
// Robolectric requires Java9 to run API 29 and above, so use API 28 instead
@Config(sdk = Build.VERSION_CODES.P)
public final class AndroidSupportInjectionTest {
  @Test
  public void injectFragment_simpleApplication() {
    Fragment fragment = new Fragment();
    startFragment(fragment);

    try {
      AndroidSupportInjection.inject(fragment);
      fail();
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("No injector was found");
    }
  }

  private static class ApplicationReturnsNull extends Application
      implements HasAndroidInjector {
    @Override
    public AndroidInjector<Object> androidInjector() {
      return null;
    }
  }

  @Test
  @Config(application = ApplicationReturnsNull.class)
  public void fragmentInjector_returnsNull() {
    Fragment fragment = new Fragment();
    startFragment(fragment);

    try {
      AndroidSupportInjection.inject(fragment);
      fail();
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("androidInjector() returned null");
    }
  }

  @Test
  public void injectFragment_nullInput() {
    try {
      AndroidSupportInjection.inject(null);
      fail();
    } catch (NullPointerException e) {
      assertThat(e).hasMessageThat().contains("fragment");
    }
  }

  void startFragment(Fragment fragment) {
    Robolectric.setupActivity(FragmentActivity.class)
        .getSupportFragmentManager()
        .beginTransaction()
        .add(fragment, "")
        .commitNow();
  }
}
