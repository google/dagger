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
import android.support.v4.app.Fragment;
import dagger.android.AndroidInjector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.support.v4.SupportFragmentTestUtil;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public final class AndroidSupportInjectionTest {
  @Test
  public void injectFragment_simpleApplication() {
    Fragment fragment = new Fragment();
    SupportFragmentTestUtil.startFragment(fragment);

    try {
      AndroidSupportInjection.inject(fragment);
      fail();
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("No injector was found");
    }
  }

  private static class ApplicationReturnsNull extends Application
      implements HasSupportFragmentInjector {
    @Override
    public AndroidInjector<Fragment> supportFragmentInjector() {
      return null;
    }
  }

  @Test
  @Config(manifest = Config.NONE, application = ApplicationReturnsNull.class)
  public void fragmentInjector_returnsNull() {
    Fragment fragment = new Fragment();
    SupportFragmentTestUtil.startFragment(fragment);

    try {
      AndroidSupportInjection.inject(fragment);
      fail();
    } catch (Exception e) {
      assertThat(e).hasMessageThat().contains("supportFragmentInjector() returned null");
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
}
